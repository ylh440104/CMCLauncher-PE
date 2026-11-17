package com.cmc.launcher.ui.screens.content.home.server

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.game.version.multiplayer.pingServer
import com.cmc.launcher.game.version.multiplayer.resolve
import com.cmc.launcher.ui.screens.content.home.version.VersionCardDir
import com.cmc.launcher.ui.screens.content.home.version.VersionCardManager
import com.cmc.launcher.ui.screens.content.home.version.VersionCardStatus
import com.cmc.launcher.utils.hasStoragePermission
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.ServerAddress
import com.cmc.launcher.utils.string.stripColorCodes
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

private const val TAG = "ServerCardManager"

private const val MODE_SPECIFIC = "specific"

private data class ServerCardRecordDto(
    @SerializedName("cardId")
    val cardId: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("quickLaunchEnabled")
    val quickLaunchEnabled: Boolean = true,
    @SerializedName("versionMode")
    val versionMode: String = "",
    @SerializedName("dirType")
    val dirType: String = "",
    @SerializedName("dirPath")
    val dirPath: String = "",
    @SerializedName("versionName")
    val versionName: String? = null
) {
    fun toRecord() = ServerCardRecord(
        cardId = cardId,
        name = name,
        icon = icon?.let { base64 ->
            runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
        },
        quickLaunchEnabled = quickLaunchEnabled,
        versionMode = if (versionMode == MODE_SPECIFIC) {
            ServerCardVersionMode.SPECIFIC
        } else {
            ServerCardVersionMode.CURRENT
        },
        dir = when {
            versionMode != MODE_SPECIFIC -> null
            dirType == VersionCardManager.DIR_TYPE_CUSTOM -> VersionCardDir.Custom(dirPath)
            else -> VersionCardDir.Default
        },
        versionName = versionName
    )

    companion object {
        fun fromRecord(record: ServerCardRecord) = ServerCardRecordDto(
            cardId = record.cardId,
            name = record.name,
            icon = record.icon?.let { Base64.getEncoder().encodeToString(it) },
            quickLaunchEnabled = record.quickLaunchEnabled,
            versionMode = if (record.versionMode == ServerCardVersionMode.SPECIFIC) {
                MODE_SPECIFIC
            } else {
                "current"
            },
            dirType = when (record.dir) {
                is VersionCardDir.Custom -> VersionCardManager.DIR_TYPE_CUSTOM
                is VersionCardDir.Default -> VersionCardManager.DIR_TYPE_DEFAULT
                null -> ""
            },
            dirPath = (record.dir as? VersionCardDir.Custom)?.path ?: "",
            versionName = record.versionName
        )
    }
}

object ServerCardManager {

    private const val REFRESH_COOLDOWN_MS = 15_000L

    private const val KEY_RECORDS = "serverCards"

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("home_server_cards") }
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _cards = MutableStateFlow<List<ServerCardState>>(emptyList())

    val cards: StateFlow<List<ServerCardState>> = _cards.asStateFlow()

    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())

    val inFlightCards: StateFlow<Set<String>> = _inFlight.asStateFlow()

    private val pingJobs = mutableMapOf<String, Job>()

    private val lastPingAt = mutableMapOf<String, Long>()

    private var visibleIds: Set<String> = emptySet()

    init {
        _cards.value = loadRecords().map { record ->
            ServerCardState(record, ServerCardPingStatus.Loading)
        }

        recheckVersions()

        VersionsManager.registerListener {
            recheckVersions()
        }
    }

    fun hasCard(serverIp: String): Boolean =
        _cards.value.any { it.record.cardId == serverIp }

    fun addCard(name: String, serverIp: String, icon: ByteArray? = null): Boolean {
        val ip = serverIp.trim()
        if (ip.isEmpty()) return false
        synchronized(this) {
            if (hasCard(ip)) return false
            val record = ServerCardRecord(
                cardId = ip,
                name = name.stripColorCodes().ifBlank { ip },
                icon = icon
            )
            _cards.update { states ->
                states + ServerCardState(record, ServerCardPingStatus.Loading)
            }
            saveRecords()
        }
        return true
    }

    fun removeCard(cardId: String) {
        synchronized(this) {
            if (_cards.value.none { it.record.cardId == cardId }) return
            pingJobs.remove(cardId)?.cancel()
            lastPingAt.remove(cardId)
            _inFlight.update { it - cardId }
            visibleIds -= cardId
            _cards.update { states ->
                states.filterNot { it.record.cardId == cardId }
            }
            saveRecords()
        }
    }

    fun setCardName(cardId: String, name: String) {
        val trimmed = name.stripColorCodes().trim()
        if (trimmed.isEmpty()) return
        synchronized(this) {
            updateState(cardId) { it.copy(record = it.record.copy(name = trimmed)) } ?: return
            saveRecords()
        }
    }

    fun setQuickLaunchEnabled(cardId: String, enabled: Boolean) {
        synchronized(this) {
            updateState(cardId) { it.copy(record = it.record.copy(quickLaunchEnabled = enabled)) } ?: return
            saveRecords()
        }
    }

    fun setVersionBinding(
        cardId: String,
        mode: ServerCardVersionMode,
        dir: VersionCardDir? = null,
        versionName: String? = null
    ) {
        synchronized(this) {
            updateState(cardId) {
                it.copy(
                    record = it.record.copy(
                        versionMode = mode,
                        dir = dir.takeIf { mode == ServerCardVersionMode.SPECIFIC },
                        versionName = versionName.takeIf { mode == ServerCardVersionMode.SPECIFIC }
                    )
                )
            } ?: return
            saveRecords()
        }
        scope.launch {
            resolveBinding(cardId)
        }
    }

    fun refresh(
        cardId: String,
        manual: Boolean = false,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        synchronized(this) {
            if (_cards.value.none { it.record.cardId == cardId }) return
            val existing = pingJobs[cardId]
            if (existing != null) {
                if (!manual) return
                existing.cancel()
            } else if (!manual && System.currentTimeMillis() - (lastPingAt[cardId] ?: 0L) < REFRESH_COOLDOWN_MS) {
                return
            }
            _inFlight.update { it + cardId }
            pingJobs[cardId] = scope.launch {
                val success = ping(cardId)
                onFinished?.let { callback ->
                    withContext(Dispatchers.Main) { callback(success) }
                }
            }
        }
    }

    fun onVisibleCardsChanged(ids: Set<String>) {
        val entered: List<String>
        synchronized(this) {
            entered = ids.filterNot { it in visibleIds }
            visibleIds = ids
        }
        entered.forEach { refresh(it) }
    }

    private suspend fun ping(cardId: String): Boolean {
        val ip = _cards.value.firstOrNull { it.record.cardId == cardId }?.record?.cardId ?: return false

        val result = runCatching {
            pingServer(ServerAddress.parse(ip).resolve())
        }.onFailure { e ->
            Logger.warning(TAG, "Unable to ping server: $ip", e)
        }.getOrNull()

        val current = _cards.value.firstOrNull { it.record.cardId == cardId } ?: return false

        return synchronized(this) {

            lastPingAt[cardId] = System.currentTimeMillis()
            pingJobs.remove(cardId)
            _inFlight.update { it - cardId }

            when (result) {
                null -> {

                    if (current.ping !is ServerCardPingStatus.Loaded) {
                        _cards.update { states ->
                            states.map {
                                if (it.record.cardId == cardId) {
                                    it.copy(ping = ServerCardPingStatus.Failed)
                                } else it
                            }
                        }
                    }
                    false
                }
                else -> {

                    val newIcon = result.status.favicon?.icon
                    val changed = !newIcon.contentEquals(current.record.icon)
                    val newRecord = if (changed) current.record.copy(icon = newIcon) else current.record
                    _cards.update { states ->
                        states.map {
                            if (it.record.cardId == cardId) {
                                it.copy(record = newRecord, ping = ServerCardPingStatus.Loaded(result))
                            } else it
                        }
                    }

                    if (changed) saveRecords()
                    true
                }
            }
        }
    }

    private fun recheckVersions() {
        scope.launch {
            _cards.value.forEach { state ->
                resolveBinding(state.record.cardId)
            }
        }
    }

    private suspend fun resolveBinding(cardId: String) {
        val state = _cards.value.firstOrNull { it.record.cardId == cardId } ?: return
        val status = resolveVersionStatus(state.record)
        when {
            status is VersionCardStatus.Deleted -> {
                synchronized(this) {
                    updateState(cardId) {
                        it.copy(
                            record = it.record.copy(dir = null, versionName = null),
                            versionStatus = null
                        )
                    } ?: return
                    saveRecords()
                }
            }
            status != state.versionStatus -> {

                _cards.update { states ->
                    states.map {
                        if (it.record.cardId == cardId) it.copy(versionStatus = status) else it
                    }
                }
            }
        }
    }

    private fun resolveVersionStatus(record: ServerCardRecord): VersionCardStatus? {
        if (record.versionMode != ServerCardVersionMode.SPECIFIC) return null
        val dir = record.dir ?: return null
        val versionName = record.versionName ?: return null
        if (dir is VersionCardDir.Custom && !hasStoragePermission) {
            return VersionCardStatus.Inaccessible
        }
        val gameHome = dir.resolveGameHome()
        if (!File(gameHome).exists()) return VersionCardStatus.Inaccessible
        val version = VersionsManager.loadVersion(gameHome, versionName)
            ?.takeIf { it.isValid() }
            ?: return VersionCardStatus.Deleted
        return VersionCardStatus.Available(version)
    }

    private fun updateState(cardId: String, transform: (ServerCardState) -> ServerCardState): ServerCardState? {
        var updated: ServerCardState? = null
        _cards.update { states ->
            states.map { state ->
                if (state.record.cardId == cardId) {
                    transform(state).also { updated = it }
                } else state
            }
        }
        return updated
    }

    private fun loadRecords(): List<ServerCardRecord> {
        val json = mmkv.decodeString(KEY_RECORDS, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<ServerCardRecordDto>::class.java)
                .orEmpty()
                .map { it.toRecord() }
                .filter { it.cardId.isNotBlank() }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse server card records.", e)
        }.getOrDefault(emptyList())
    }

    private fun saveRecords() {
        val json = gson.toJson(_cards.value.map { ServerCardRecordDto.fromRecord(it.record) })
        mmkv.encode(KEY_RECORDS, json)
    }
}
