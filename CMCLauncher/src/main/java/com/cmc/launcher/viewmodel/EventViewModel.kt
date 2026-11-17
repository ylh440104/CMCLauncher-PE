package com.cmc.launcher.viewmodel

import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.control.input.TextInputMode
import com.cmc.launcher.ui.guide.GuideKeys
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class EventViewModel : ViewModel() {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    sealed interface Event {
        sealed interface Key : Event {

            data object StartKeyCapture : Key

            data object StopKeyCapture : Key

            data class OnKeyDown(val key: KeyEvent) : Key
        }
        sealed interface Game : Event {

            data class KeyHandle(val handle: Boolean): Game

            data class SwitchIme(val mode: TextInputMode?) : Game

            data object RefreshSize : Game

            data object OnBack : Game

            data object OnResume: Game
        }
        sealed interface Terracotta : Event {

            data object RequestVPN : Terracotta

            data class VPNUpdateState(val stringRes: Int): Terracotta

            data object StopVPN : Terracotta
        }

        sealed interface Launch : Event {

            data class Game(val version: Version?) : Launch

            data class PlayServer(val version: Version, val address: String): Launch

            data class PlaySave(val version: Version, val saveName: String): Launch
        }

        data object CheckUpdate : Event

        data class OpenLink(val url: String) : Event

        data class KeepScreen(val on: Boolean) : Event

        data class ImportControls(val uris: List<Uri>) : Event

        data class DownloadPlugins(val link: Links): Event {
            data class Links(
                val github: String,
                val cloudDrives: List<CloudDrive> = emptyList()
            )

            data class CloudDrive(
                val language: String,
                val link: String
            )
        }

        sealed interface LogShare : Event {
            data class ShareGameLog(val logFile: File) : LogShare
        }

        data class VulkanCheck(val version: Version): Event

        data class ShowToast(
            val text: AndroidStringText,
            val duration: Int = Toast.LENGTH_SHORT
        ) : Event

        data class OpenFileManager(
            val rootPath: String,
            val currentPath: String? = null,
        ) : Event

        sealed interface Guide : Event {

            data class StartGuide(val group: GuideKeys.Keys) : Guide

            data class StartGuideOnce(val group: GuideKeys.Keys) : Guide
        }
    }
}

fun EventViewModel.sendKeepScreen(
    on: Boolean
) {
    sendEvent(EventViewModel.Event.KeepScreen(on))
}

fun EventViewModel.sendToast(
    text: AndroidStringText,
    duration: Int = Toast.LENGTH_SHORT,
) {
    sendEvent(EventViewModel.Event.ShowToast(text, duration))
}

fun EventViewModel.sendDLPlugin(
    githubLink: String,
    cloudDrives: List<EventViewModel.Event.DownloadPlugins.CloudDrive> = emptyList()
) {
    sendEvent(
        EventViewModel.Event.DownloadPlugins(
            link = EventViewModel.Event.DownloadPlugins.Links(
                github = githubLink,
                cloudDrives = cloudDrives
            )
        )
    )
}
