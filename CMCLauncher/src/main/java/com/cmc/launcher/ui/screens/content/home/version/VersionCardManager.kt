package com.cmc.launcher.ui.screens.content.home.version

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.utils.hasStoragePermission
import com.cmc.launcher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "VersionCardManager"

private data class VersionCardRecordDto(
    @SerializedName("cardId")
    val cardId: String = "",
    @SerializedName("versionName")
    val versionName: String = "",
    @SerializedName("dirType")
    val dirType: String = "",
    @SerializedName("dirPath")
    val dirPath: String = ""
) {
    fun toRecord() = VersionCardRecord(
        cardId = cardId,
        versionName = versionName,
        dir = when (dirType) {
            VersionCardManager.DIR_TYPE_CUSTOM -> VersionCardDir.Custom(dirPath)
            else -> VersionCardDir.Default
        }
    )

    companion object {
        fun fromRecord(record: VersionCardRecord) = VersionCardRecordDto(
            cardId = record.cardId,
            versionName = record.versionName,
            dirType = when (record.dir) {
                is VersionCardDir.Custom -> VersionCardManager.DIR_TYPE_CUSTOM
                VersionCardDir.Default -> VersionCardManager.DIR_TYPE_DEFAULT
            },
            dirPath = (record.dir as? VersionCardDir.Custom)?.path ?: ""
        )
    }
}

object VersionCardManager {
    const val DIR_TYPE_DEFAULT = "default"
    const val DIR_TYPE_CUSTOM = "custom"

    private const val KEY_RECORDS = "versionCards"

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("home_version_cards") }
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _cards = MutableStateFlow<List<VersionCardState>>(emptyList())

    val cards: StateFlow<List<VersionCardState>> = _cards.asStateFlow()

    init {
        _cards.value = loadRecords().map { record ->
            VersionCardState(record, VersionCardStatus.Loading)
        }
        recheck()

        VersionsManager.registerListener {
            recheck()
        }
    }

    fun hasCard(versionName: String, gameHome: String): Boolean {
        val dir = VersionCardDir.fromGameHome(gameHome)
        return _cards.value.any { it.record.versionName == versionName && it.record.dir == dir }
    }

    fun addCard(version: Version): Boolean {
        val dir = VersionCardDir.fromGameHome(version.getGameHome())
        synchronized(this) {
            if (hasCard(version.getVersionName(), version.getGameHome())) return false
            val record = VersionCardRecord(
                cardId = UUID.randomUUID().toString(),
                versionName = version.getVersionName(),
                dir = dir
            )
            _cards.update { states ->
                states + VersionCardState(record, VersionCardStatus.Available(version))
            }
            saveRecords()
        }
        return true
    }

    fun removeCard(cardId: String) {
        synchronized(this) {
            val removed = _cards.value.any { it.record.cardId == cardId }
            if (!removed) return
            _cards.update { states -> states.filterNot { it.record.cardId == cardId } }
            saveRecords()
        }
    }

    fun onVersionRenamed(gameHome: String, oldName: String, newName: String) {
        val dir = VersionCardDir.fromGameHome(gameHome)
        synchronized(this) {
            val changed = _cards.value.any { it.record.versionName == oldName && it.record.dir == dir }
            if (!changed) return
            _cards.update { states ->
                states.map { state ->
                    if (state.record.versionName == oldName && state.record.dir == dir) {
                        state.copy(record = state.record.copy(versionName = newName))
                    } else {
                        state
                    }
                }
            }
            saveRecords()
        }
        recheck()
    }

    private fun recheck() {
        scope.launch {
            val current = _cards.value
            if (current.isEmpty()) return@launch
            val updated = current.map { state ->
                state.copy(status = resolveStatus(state.record))
            }

            if (updated != current) _cards.value = updated
        }
    }

    private fun resolveStatus(record: VersionCardRecord): VersionCardStatus {
        val gameHome = record.dir.resolveGameHome()
        if (record.dir is VersionCardDir.Custom && !hasStoragePermission) {
            return VersionCardStatus.Inaccessible
        }
        if (!File(gameHome).exists()) return VersionCardStatus.Inaccessible
        val version = VersionsManager.loadVersion(gameHome, record.versionName)
            ?.takeIf { it.isValid() }
            ?: return VersionCardStatus.Deleted
        Logger.info(
            TAG,
            "Version card loaded version: ${version.getVersionName()}, " +
                    "Path: (${version.getVersionPath()}), " +
                    "Info: ${version.getVersionInfo()?.getInfoString()}"
        )
        return VersionCardStatus.Available(version)
    }

    private fun loadRecords(): List<VersionCardRecord> {
        val json = mmkv.decodeString(KEY_RECORDS, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<VersionCardRecordDto>::class.java)
                .orEmpty()
                .map { it.toRecord() }
                .filter { it.cardId.isNotBlank() && it.versionName.isNotBlank() }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse version card records.", e)
        }.getOrDefault(emptyList())
    }

    private fun saveRecords() {
        val json = gson.toJson(_cards.value.map { VersionCardRecordDto.fromRecord(it.record) })
        mmkv.encode(KEY_RECORDS, json)
    }
}
