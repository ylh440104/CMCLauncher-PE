package com.cmc.launcher.game.launch.handler

import android.app.Activity
import android.view.KeyEvent
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.cmc.launcher.bridge.ZLBridge
import com.cmc.launcher.game.control.ControlManager
import com.cmc.launcher.game.input.EfficientAndroidLWJGLKeycode
import com.cmc.launcher.game.input.LWJGLCharSender
import com.cmc.launcher.game.launch.GameLauncher
import com.cmc.launcher.game.launch.LaunchConfig
import com.cmc.launcher.game.launch.MCOptions
import com.cmc.launcher.game.launch.loadLanguage
import com.cmc.launcher.game.sdl.SdlBridge
import com.cmc.launcher.game.sdl.handleGamepadKeyEvent
import com.cmc.launcher.game.version.installed.GraphicsApi
import com.cmc.launcher.game.version.installed.utils.isLowerVer
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.GamepadInputMode
import com.cmc.launcher.terracotta.Terracotta
import com.cmc.launcher.ui.control.gamepad.isGamepadKeyEvent
import com.cmc.launcher.ui.control.input.TextInputMode
import com.cmc.launcher.ui.screens.game.GameScreen
import com.cmc.launcher.ui.screens.game.elements.LogState
import com.cmc.launcher.ui.screens.game.elements.mutableStateOfLog
import com.cmc.launcher.viewmodel.ErrorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.GamepadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libsdl.app.SDLActivity
import org.lwjgl.glfw.CallbackBridge

class GameHandler(
    val activity: Activity,
    config: LaunchConfig,
    errorViewModel: ErrorViewModel,
    eventViewModel: EventViewModel,
    private val gamepadViewModel: GamepadViewModel,
    gameLauncher: GameLauncher,
    onExit: (code: Int) -> Unit
) : AbstractHandler(
    type = HandlerType.GAME,
    errorViewModel = errorViewModel,
    eventViewModel = eventViewModel,
    launcher = gameLauncher,
    onExit = onExit
) {
    private val version = config.version
    private val account = config.account

    private val _inputArea = MutableStateFlow<IntRect?>(null)
    override val inputArea = _inputArea.asStateFlow()

    private var isGameRendering = false
    private var showGameInfo by mutableStateOf(true)

    private var logState by mutableStateOfLog()

    override suspend fun execute(
        surface: Surface,
        screenSize: IntSize,
        scope: CoroutineScope
    ) {
        ZLBridge.setupBridgeWindow(surface)

        MCOptions.setup(activity, version)

        MCOptions.apply {
            set("fullscreen", "false")
            set("touchscreen", "false")

            if (version.getVersionInfo()!!.minecraftVersion.isLowerVer("1.13")) {

                set("key_key.fullscreen", "0")

                set("key_key.streamStartStop", "0")
                set("key_key.streamPauseUnpause", "0")
            }

            set("overrideWidth", screenSize.width.toString())
            set("overrideHeight", screenSize.height.toString())

            val graphicsApi = version.getGraphicsApi()
            val graphicsOption = "preferredGraphicsBackend"
            when (graphicsApi) {
                GraphicsApi.DEFAULT, GraphicsApi.DEFAULT_OPENGL -> {
                    if (!containsKey(graphicsOption)) {
                        set(graphicsOption, graphicsApi.option)
                    }
                }
                else -> set(graphicsOption, graphicsApi.option)
            }

            loadLanguage(version.getVersionInfo()!!.minecraftVersion)
            save()
        }

        super.execute(surface, screenSize, scope)
    }

    override fun onPause() {
    }

    override fun onResume() {
        refreshControls()
        eventViewModel.sendEvent(EventViewModel.Event.Game.OnResume)
    }

    override fun onDestroy() {
        Terracotta.setWaiting(false)
    }

    override fun onGraphicOutput() {
        if (!isGameRendering) {
            isGameRendering = true
            showGameInfo = false

            if (logState == LogState.SHOW_BEFORE_LOADING) {
                logState = LogState.CLOSE
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldIgnoreKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && (event.flags and KeyEvent.FLAG_CANCELED) != 0) return false

        if (event.isGamepadKeyEvent()) {
            if (AllSettings.gamepadControl.state && gamepadViewModel.checkModePrompt()) {

                return false
            }
            if (AllSettings.gamepadControl.state && AllSettings.gamepadInputMode.state == GamepadInputMode.SdlDirect) {

                if (SdlBridge.sdlEnabled) {

                    gamepadViewModel.notifyActivity()
                    handleGamepadKeyEvent(event)
                    try {
                        SDLActivity.handleKeyEvent(null, event.keyCode, event, null)
                    } catch (_: UnsatisfiedLinkError) {

                    }
                }
                return false
            }
            return if (AllSettings.gamepadControl.state) {

                gamepadViewModel.sendKeyEvent(event)
                false
            } else {

                if (AllSettings.showMenuBall.state) {

                    false
                } else {
                    true
                }
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true

        if ((event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                LWJGLCharSender.sendEnter()
                return false
            }

            if (event.getUnicodeChar() != 0) return true
        }

        EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode).takeIf { it >= 0 }?.let { index ->
            EfficientAndroidLWJGLKeycode.execKey(event, index)
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.ACTION_MULTIPLE,
            KeyEvent.ACTION_UP
                 -> false

            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
                 -> true

            else -> (event.flags and KeyEvent.FLAG_FALLBACK) != KeyEvent.FLAG_FALLBACK
        }
    }

    override fun sendMouseRight(isPressed: Boolean) {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), isPressed)
    }

    @Composable
    override fun ComposableLayout(
        textInputMode: TextInputMode
    ) {
        GameScreen(
            version = version,
            gameHandler = this,
            showGameInfo = showGameInfo,
            onInfoBoxClose = { showGameInfo = false },
            logState = logState,
            onLogStateChange = { logState = it },
            textInputMode = textInputMode,
            isTouchProxyEnabled = version.enableTouchProxy,
            onInputAreaRectUpdated = { _inputArea.value = it },
            getAccountName = { account.username },
            eventViewModel = eventViewModel,
            gamepadViewModel = gamepadViewModel,
        )
    }

    private fun refreshControls() {
        ControlManager.refresh()
    }

    init {
        refreshControls()
    }
}
