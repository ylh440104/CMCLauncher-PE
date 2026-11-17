package com.cmc.launcher.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.game.launch.GameLaunchFlow
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.ui.screens.content.elements.LaunchGameOperation
import com.cmc.launcher.ui.screens.content.elements.QuickPlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LaunchGameViewModel : ViewModel() {
    private val _launchFlow = MutableStateFlow<GameLaunchFlow?>(null)

    val launchFlow = _launchFlow.asStateFlow()

    private val _launchGameOperation = MutableStateFlow<LaunchGameOperation>(LaunchGameOperation.None)

    val launchGameOperation = _launchGameOperation.asStateFlow()

    fun start(
        activity: Activity,
        version: Version,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
        quickPlay: QuickPlay?,
        skipAccountRefresh: Boolean
    ) {
        _launchFlow.update {
            GameLaunchFlow(viewModelScope).also {
                it.launch(
                    context = activity,
                    version = version,
                    exitActivity = exitActivity,
                    onError = { message ->
                        submitError(message)
                        cancel()
                    },
                    onReloginRequired = { account ->
                        activity.runOnUiThread {
                            updateOperation(
                                LaunchGameOperation.AccountRelogin(account, version, quickPlay)
                            )
                        }
                        cancel()
                    },
                    onRefreshFailed = { account, th ->
                        activity.runOnUiThread {
                            updateOperation(
                                LaunchGameOperation.AccountRefreshFailed(account, th, version, quickPlay)
                            )
                        }
                        cancel()
                    },
                    onComplete = {
                        _launchFlow.update { null }
                    },
                    skipAccountRefresh = skipAccountRefresh
                )
            }
        }
    }

    fun cancel() {
        _launchFlow.value?.cancel()
        _launchFlow.update { null }
    }

    fun tryLaunch(
        version: Version? = null
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version ?: VersionsManager.currentVersion.value
                )
            )
        }
    }

    fun quickPlaySave(
        version: Version,
        saveName: String
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version = version,
                    quickPlay = QuickPlay.Save(saveName),
                )
            )
        }
    }

    fun tryPlayServer(address: String) {
        val version = VersionsManager.currentVersion.value ?: return
        quickPlayServer(version, address)
    }

    fun quickPlayServer(
        version: Version,
        address: String
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version = version,
                    quickPlay = QuickPlay.Server(address),
                )
            )
        }
    }

    fun updateOperation(operation: LaunchGameOperation) {
        this._launchGameOperation.update { operation }
    }
}
