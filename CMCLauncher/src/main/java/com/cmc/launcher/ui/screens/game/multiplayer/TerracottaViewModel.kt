package com.cmc.launcher.ui.screens.game.multiplayer

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmc.launcher.R
import com.cmc.launcher.context.COPY_LABEL_TERRACOTTA_INVITE_CODE
import com.cmc.launcher.context.COPY_LABEL_TERRACOTTA_SERVER_ADDRESS
import com.cmc.launcher.game.launch.handler.GameHandler
import com.cmc.launcher.terracotta.Terracotta
import com.cmc.launcher.terracotta.TerracottaState
import com.cmc.launcher.terracotta.TerracottaVPNService
import com.cmc.launcher.terracotta.profile.TerracottaProfile
import com.cmc.launcher.utils.copyText
import com.cmc.launcher.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TerracottaViewModel(
    val gameHandler: GameHandler,
    val eventViewModel: EventViewModel,
    val getUserName: () -> String?
): ViewModel() {
    var operation by mutableStateOf<TerracottaOperation>(TerracottaOperation.None)

    var dialogState by mutableStateOf<TerracottaState.Ready?>(null)

    var dialogLogOperation by mutableStateOf<TerracottaLogOperation>(TerracottaLogOperation.None)
        private set

    var terracottaVer by mutableStateOf<String?>(null)

    var easyTierVer by mutableStateOf<String?>(null)

    var vpnLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null

    var isWaitingInteractive by mutableStateOf(false)

    private val _profiles = MutableStateFlow<List<TerracottaProfile>>(emptyList())

    val profiles = _profiles.asStateFlow()

    private val allJobs: MutableList<Job> = mutableListOf()

    fun openMenu() {
        if (operation !is TerracottaOperation.None) return

        if (!Terracotta.initialized) initialize()
        if (allJobs.isEmpty()) initJobs()

        operation = TerracottaOperation.ShowMenu
    }

    private val logMutex = Mutex()

    fun showLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = logMutex.withLock {
                if (dialogLogOperation is TerracottaLogOperation.CollectingLog) return@withLock null
                TerracottaLogOperation.CollectingLog
            }

            result ?: return@launch
            dialogLogOperation = result

            val finalState = Terracotta.collectLogs()?.let { logString ->
                TerracottaLogOperation.EnableLog(logString)
            } ?: TerracottaLogOperation.None

            dialogLogOperation = finalState
        }
    }

    fun hideLog() {
        dialogLogOperation = TerracottaLogOperation.None
    }

    fun copyInviteCode(
        state: TerracottaState.HostOK
    ) {
        val code = state.code ?: return
        copyText(label = COPY_LABEL_TERRACOTTA_INVITE_CODE, text = code) {
            it.getString(R.string.terracotta_status_host_ok_code_copy_toast)
        }
    }

    fun copyServerAddress(
        state: TerracottaState.GuestOK
    ) {
        val address = state.url ?: return
        copyText(label = COPY_LABEL_TERRACOTTA_SERVER_ADDRESS, text = address) {
            it.getString(R.string.terracotta_status_guest_ok_address_copy_toast)
        }
    }

    private fun copyText(
        label: String,
        text: String,
        toast: (Context) -> String
    ) {
        val context = gameHandler.activity
        copyText(
            label = label,
            text = text,
            context = context,
            showToast = false
        )
        Toast.makeText(context, toast(context), Toast.LENGTH_SHORT).show()
    }

    private fun updateProfiles(profiles: List<TerracottaProfile>?) {

        _profiles.update {
            profiles ?: emptyList()
        }
    }

    private fun initialize() {
        Terracotta.initialize(viewModelScope, eventViewModel)
        Terracotta.setWaiting(true)

        val metadata = Terracotta.getMetadata()
        terracottaVer = metadata.terracottaVersion
        easyTierVer = metadata.easyTierVersion
    }

    private fun initJobs() {
        val activity = gameHandler.activity

        val stateChangeJob = viewModelScope.launch {
            Terracotta.stateChanges.collect { (old, new) ->
                when (new) {
                    is TerracottaState.Waiting -> {
                        if (old !is TerracottaState.Waiting) {

                            isWaitingInteractive = true
                        }
                    }
                    is TerracottaState.HostOK -> {
                        if (old !is TerracottaState.HostOK) {

                            withContext(Dispatchers.Main) {
                                copyInviteCode(new)
                            }

                            updateProfiles(new.profiles)
                        }
                        if (new.isForkOf(old)) {
                            updateProfiles(new.profiles)
                            return@collect
                        }
                    }
                    is TerracottaState.GuestOK -> {
                        if (old !is TerracottaState.GuestOK) {
                            updateProfiles(new.profiles)
                        }
                        if (new.isForkOf(old)) {
                            updateProfiles(new.profiles)
                            return@collect
                        }
                    }
                    else -> {
                        if (_profiles.value.isNotEmpty()) {

                            updateProfiles(emptyList())
                        }
                    }
                }
                dialogState = new
            }
        }

        val eventJob = viewModelScope.launch {
            eventViewModel.events
                .filterIsInstance<EventViewModel.Event.Terracotta>()
                .collect { event ->
                    when (event) {
                        is EventViewModel.Event.Terracotta.RequestVPN -> {
                            withContext(Dispatchers.Main) {
                                val intent = VpnService.prepare(activity)
                                if (intent != null) {
                                    vpnLauncher?.launch(intent)
                                } else {
                                    val vpnIntent = Intent(activity, TerracottaVPNService::class.java)
                                        .setAction(TerracottaVPNService.ACTION_START)
                                    activity.startForegroundService(vpnIntent)
                                }
                            }
                        }
                        is EventViewModel.Event.Terracotta.VPNUpdateState -> {
                            withContext(Dispatchers.Main) {
                                if (TerracottaVPNService.isRunning()) {

                                    activity.startService(
                                        Intent(activity, TerracottaVPNService::class.java)
                                            .setAction(TerracottaVPNService.ACTION_UPDATE_STATE)
                                            .putExtra(TerracottaVPNService.EXTRA_STATE_TEXT, event.stringRes)
                                    )
                                }
                            }
                        }
                        is EventViewModel.Event.Terracotta.StopVPN -> {
                            withContext(Dispatchers.Main) {
                                val activity = gameHandler.activity
                                if (TerracottaVPNService.isRunning()) {

                                    activity.stopService(
                                        Intent(activity, TerracottaVPNService::class.java)
                                    )
                                }
                            }
                        }
                    }
                }
        }

        allJobs.add(stateChangeJob)
        allJobs.add(eventJob)
    }

    override fun onCleared() {
        allJobs.forEach { it.cancel() }
        allJobs.clear()
    }
}

@Composable
fun rememberTerracottaViewModel(
    keyTag: String,
    gameHandler: GameHandler,
    eventViewModel: EventViewModel,
    getUserName: () -> String?
): TerracottaViewModel {
    return viewModel(
        key = keyTag
    ) {
        TerracottaViewModel(
            gameHandler = gameHandler,
            eventViewModel = eventViewModel,
            getUserName = getUserName
        )
    }
}
