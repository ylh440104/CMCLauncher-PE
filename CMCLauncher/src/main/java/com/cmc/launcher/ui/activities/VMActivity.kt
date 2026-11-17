package com.cmc.launcher.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jakewharton.processphoenix.ProcessPhoenix
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.cmc.launcher.R
import com.cmc.launcher.bridge.CURSOR_DISABLED
import com.cmc.launcher.bridge.FliteTts
import com.cmc.launcher.bridge.LoggerBridge
import com.cmc.launcher.bridge.ZLBridge
import com.cmc.launcher.bridge.ZLBridgeStates
import com.cmc.launcher.coroutine.DataBridge
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.input.AWTCharSender
import com.cmc.launcher.game.input.CharacterSenderStrategy
import com.cmc.launcher.game.input.LWJGLCharSender
import com.cmc.launcher.game.launch.GameLauncher
import com.cmc.launcher.game.launch.GameService
import com.cmc.launcher.game.launch.JvmLaunchInfo
import com.cmc.launcher.game.launch.JvmLauncher
import com.cmc.launcher.game.launch.LaunchConfig
import com.cmc.launcher.game.launch.Launcher
import com.cmc.launcher.game.launch.handler.AbstractHandler
import com.cmc.launcher.game.launch.handler.GameHandler
import com.cmc.launcher.game.launch.handler.HandlerType
import com.cmc.launcher.game.launch.handler.JVMHandler
import com.cmc.launcher.game.multirt.RuntimesManager
import com.cmc.launcher.game.plugin.PluginLoader
import com.cmc.launcher.game.renderer.Renderers
import com.cmc.launcher.game.sdl.SdlBridge
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.ResolutionRule
import com.cmc.launcher.terracotta.TerracottaVPNService
import com.cmc.launcher.ui.base.BaseAppCompatActivity
import com.cmc.launcher.ui.base.ObserveFullScreenSetting
import com.cmc.launcher.ui.components.rememberBoxSize
import com.cmc.launcher.ui.control.input.HidableInputLayout
import com.cmc.launcher.ui.control.input.InputMagnifier
import com.cmc.launcher.ui.control.input.InputMagnifierState
import com.cmc.launcher.ui.control.input.TextInputMode
import com.cmc.launcher.ui.screens.game.elements.OpenFolderLayer
import com.cmc.launcher.ui.screens.game.elements.OpenFolderOperation
import com.cmc.launcher.ui.theme.CMCLauncherTheme
import com.cmc.launcher.ui.toAndroidString
import com.cmc.launcher.utils.computeGameDisplayLayout
import com.cmc.launcher.utils.computeGameRenderSize
import com.cmc.launcher.utils.device.PhysicalMouseChecker
import com.cmc.launcher.utils.getDisplayFriendlyRes
import com.cmc.launcher.utils.getParcelableSafely
import com.cmc.launcher.utils.rememberGameRenderSize
import com.cmc.launcher.viewmodel.ErrorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.GamepadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Color as NativeColor
import android.view.WindowInsets as AndroidWindowInsets

private const val INTENT_RUN_GAME = "BUNDLE_RUN_GAME"
private const val INTENT_RUN_JAR = "INTENT_RUN_JAR"
private const val INTENT_GAME_CONFIG = "INTENT_GAME_CONFIG"
private const val INTENT_JAR_INFO = "INTENT_JAR_INFO"

data class LaunchSession(
    val activityTitle: String,
    val launcher: Launcher,
    val handler: AbstractHandler,
    val inputSender: CharacterSenderStrategy
)

class VMViewModel : ViewModel() {
    var isRunning = false

    var keyHandle = true

    val screenSizeBridge = DataBridge<IntSize>()
    var screenSize: IntSize = IntSize.Zero

    private val _onConfigurationChanged = MutableStateFlow(false)
    val onConfigurationChanged = _onConfigurationChanged.asStateFlow()

    fun onConfigurationChanged(value: Boolean = true) {
        _onConfigurationChanged.update { value }
    }

    var sender: CharacterSenderStrategy = LWJGLCharSender
        private set

    private val _openFolderOperation = MutableStateFlow<OpenFolderOperation>(OpenFolderOperation.None)

    val openFolderOperation = _openFolderOperation.asStateFlow()

    fun clearFolder() {
        _openFolderOperation.update {
            OpenFolderOperation.None
        }
    }

    private var _session: LaunchSession? = null
    val session: LaunchSession
        get() = _session ?: error("LaunchSession not initialized")

    fun initSession(
        activity: VMActivity,
        bundle: Bundle,
        errorViewModel: ErrorViewModel,
        eventViewModel: EventViewModel,
        gamepadViewModel: GamepadViewModel,
        exitListener: (Int, Boolean) -> Unit,
    ) {
        if (_session != null) return

        _session = when {
            bundle.getBoolean(INTENT_RUN_GAME) -> {
                val config: LaunchConfig = bundle.getParcelableSafely(INTENT_GAME_CONFIG, LaunchConfig::class.java)
                    ?: throw IllegalStateException("No launch config has been set.")

                val launcher = GameLauncher(
                    activity = activity,
                    config = config,
                    onExit = { code, isSignal ->
                        if (code == 0) {
                            val finishedCount = AllSettings.finishedGame.getValue()
                            if (finishedCount < Int.MAX_VALUE)  {
                                AllSettings.finishedGame.save(finishedCount + 1)
                            }
                        }
                        exitListener(code, isSignal)
                    },
                    openPath = { folder ->
                        _openFolderOperation.update {
                            OpenFolderOperation.OpenFolder(folder)
                        }
                    }
                )

                sender = LWJGLCharSender

                LaunchSession(
                    activityTitle = config.version.getVersionName(),
                    launcher = launcher,
                    handler = GameHandler(
                        activity = activity,
                        config = config,
                        errorViewModel = errorViewModel,
                        eventViewModel = eventViewModel,
                        gamepadViewModel = gamepadViewModel,
                        gameLauncher = launcher
                    ) { code ->
                        exitListener(code, false)
                    },
                    inputSender = LWJGLCharSender
                )
            }
            bundle.getBoolean(INTENT_RUN_JAR) -> {
                val jvmLaunchInfo: JvmLaunchInfo = bundle.getParcelableSafely(INTENT_JAR_INFO, JvmLaunchInfo::class.java)
                    ?: throw IllegalStateException("No launch jar info has been set.")

                val launcher = JvmLauncher(
                    context = activity,
                    jvmLaunchInfo = jvmLaunchInfo,
                    onExit = exitListener,
                    openPath = { folder ->
                        _openFolderOperation.update {
                            OpenFolderOperation.OpenFolder(folder)
                        }
                    }
                )

                sender = AWTCharSender

                LaunchSession(
                    activityTitle = activity.getString(R.string.execute_jar_title),
                    launcher = launcher,
                    handler = JVMHandler(
                        jvmLauncher = launcher,
                        errorViewModel = errorViewModel,
                        eventViewModel = eventViewModel,
                    ) { code ->
                        exitListener(code, false)
                    },
                    inputSender = AWTCharSender
                )
            }
            else -> error("Unknown VM launch mode")
        }
    }

    var textInputMode by mutableStateOf(TextInputMode.DISABLE)

    val inputMagnifierState = InputMagnifierState()

    fun disableInputMode() {
        if (textInputMode == TextInputMode.ENABLE) textInputMode = TextInputMode.DISABLE
    }

    private fun String.sendText() {
        forEach { char ->
            sender.sendChar(char)
        }
    }

    fun sendInputText(text: String) {
        text.sendText()
    }

    fun sendBackspace() {
        sender.sendBackspace()
    }

    fun sendEnder() {
        sender.sendEnter()
    }

    fun handleSpecialKey(keyEvent: KeyEvent) {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER -> {

            }

            KeyEvent.KEYCODE_DPAD_LEFT -> sender.sendLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> sender.sendRight()
            KeyEvent.KEYCODE_DPAD_UP -> sender.sendUp()
            KeyEvent.KEYCODE_DPAD_DOWN -> sender.sendDown()

            KeyEvent.KEYCODE_TAB -> sender.sendTab()

            else -> {
                val isSoftKeyboard = (keyEvent.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0
                val isTextInput = SDLActivity.isTextInputEvent(keyEvent)
                when {
                    shouldConsumeSdlTextInput(keyEvent) -> sender.sendChar(keyEvent.unicodeChar.toChar())
                    !isSoftKeyboard && !isTextInput -> sender.sendOther(keyEvent)
                }
            }
        }
    }

    fun shouldConsumeSdlTextInput(keyEvent: KeyEvent): Boolean {
        return SdlBridge.sdlEnabled && SdlBridge.isSdlRenderActive() &&
                (keyEvent.flags and KeyEvent.FLAG_SOFT_KEYBOARD) == 0 &&
                SDLActivity.isTextInputEvent(keyEvent)
    }

    fun shouldConsumeSdlModifierKey(keyEvent: KeyEvent): Boolean {
        return SdlBridge.sdlEnabled && SdlBridge.isSdlRenderActive() &&
                (keyEvent.keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                        keyEvent.keyCode == KeyEvent.KEYCODE_CTRL_RIGHT)
    }

    fun keyCanHandle(keyEvent: KeyEvent): Boolean {
        val keyCode = keyEvent.keyCode

        val isShift = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT

        val isCtrl = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        return !isShift && !isCtrl
    }
}

class VMActivity : BaseAppCompatActivity(), SurfaceTextureListener {
    override fun isIgnoreNotch(): Boolean = AllSettings.gameFullScreen.getValue()

    private val errorViewModel: ErrorViewModel by viewModels()

    private val eventViewModel: EventViewModel by viewModels()

    private val gamepadViewModel: GamepadViewModel by viewModels()

    private val vmViewModel: VMViewModel by viewModels()

    private var gameTextureView: TextureView? = null

    private var magnifierBitmap: Bitmap? = null
    private var magnifierFrame by mutableStateOf<ImageBitmap?>(null)
    private var magnifierFrameVersion by mutableLongStateOf(0L)

    private inline fun <T> withHandler(block: AbstractHandler.() -> T): T {
        return vmViewModel.session.handler.block()
    }

    private inline fun <T> withLauncher(block: Launcher.() -> T): T {
        return vmViewModel.session.launcher.block()
    }

    private fun captureMagnifierFrame() {
        if (!vmViewModel.inputMagnifierState.isVisible) return
        val textureView = gameTextureView ?: return
        val width = textureView.width
        val height = textureView.height
        if (!textureView.isAvailable || width <= 0 || height <= 0) return

        val bitmap = magnifierBitmap
            ?.takeIf { it.width == width && it.height == height }
            ?: createBitmap(width, height).also {
                magnifierBitmap = it
                magnifierFrame = it.asImageBitmap()
            }
        val captured = runCatching {
            textureView.getBitmap(bitmap)
        }.getOrNull() ?: return

        if (magnifierFrame == null) magnifierFrame = captured.asImageBitmap()
        magnifierFrameVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Renderers.init()

        PluginLoader.loadAllPlugins(this, false)
        refreshData()

        PhysicalMouseChecker.initChecker(this)

        runCatching {

            startService(Intent(this, GameService::class.java))
        }

        val bundle = intent.extras ?: throw IllegalStateException("Unknown VM launch state!")

        vmViewModel.initSession(
            activity = this,
            bundle = bundle,
            errorViewModel = errorViewModel,
            eventViewModel = eventViewModel,
            gamepadViewModel = gamepadViewModel,
            exitListener = { exitCode: Int, isSignal: Boolean ->
                stopAllService()
                if (exitCode != 0) {
                    val logPath = withLauncher {
                        getLogFile().absolutePath
                    }
                    showExitMessage(this@VMActivity, exitCode, isSignal, logPath)
                } else {

                    ProcessPhoenix.triggerRebirth(this@VMActivity)
                }
            }
        )

        CallbackBridge.setGraphicOutputListener {
            withHandler { onGraphicOutput() }
        }

        window?.apply {
            setBackgroundDrawable(NativeColor.BLACK.toDrawable())
            if (AllSettings.sustainedPerformance.getValue()) {
                setSustainedPerformanceMode(true)
            }
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
        applyMaxRefreshRatePolicy()

        val logFile = withLauncher { getLogFile() }
        logFile.parentFile?.mkdirs()
        if (!logFile.exists() && !logFile.createNewFile()) throw IOException("Failed to create a new log file")
        LoggerBridge.start(logFile.absolutePath)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                errorViewModel.errorEvents.collect { tm ->
                    errorViewModel.showErrorDialog(
                        context = this@VMActivity,
                        tm = tm
                    )
                }
            }
        }

        lifecycleScope.launch {

            eventViewModel.events.collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.RefreshSize -> {
                        vmViewModel.onConfigurationChanged()
                    }
                    is EventViewModel.Event.Game.SwitchIme -> {
                        vmViewModel.textInputMode = event.mode ?: vmViewModel.textInputMode.switch()
                    }
                    is EventViewModel.Event.Game.KeyHandle -> {
                        vmViewModel.keyHandle = event.handle
                    }
                    is EventViewModel.Event.ShowToast -> {
                        Toast.makeText(
                            this@VMActivity,
                            event.text.toAndroidString(this@VMActivity),
                            event.duration
                        ).show()
                    }
                    else -> {  }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vmViewModel.textInputMode == TextInputMode.ENABLE) {

                    vmViewModel.disableInputMode()
                    return
                }
                if (!vmViewModel.keyHandle) return

                eventViewModel.sendEvent(EventViewModel.Event.Game.OnBack)
            }
        })

        if (!AllSettings.showMenuBall.getValue()) {
            Toast.makeText(
                this@VMActivity,
                getString(R.string.game_menu_option_show_menu_hided),
                Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            CMCLauncherTheme {
                ObserveFullScreenSetting(AllSettings.gameFullScreen.state)

                LaunchedEffect(AllSettings.requestMaxRefreshRate.state) {
                    applyMaxRefreshRatePolicy()
                }
                Screen {
                    withHandler {
                        ComposableLayout(vmViewModel.textInputMode)
                    }

                    if (vmViewModel.textInputMode == TextInputMode.ENABLE) {

                        HidableInputLayout(
                            onSend = { text ->
                                vmViewModel.sendInputText(text)
                            },
                            onBackspace = {
                                vmViewModel.sendBackspace()
                            },
                            onEnter = {
                                vmViewModel.sendEnder()
                            },
                            onClose = {
                                vmViewModel.textInputMode = TextInputMode.DISABLE
                            }
                        )
                    }

                    val cursorMode by ZLBridgeStates.cursorMode.collectAsStateWithLifecycle()
                    LaunchedEffect(cursorMode) {
                        if (cursorMode == CURSOR_DISABLED) vmViewModel.disableInputMode()
                    }

                    val operation by vmViewModel.openFolderOperation.collectAsStateWithLifecycle()
                    OpenFolderLayer(
                        modifier = Modifier.fillMaxSize(),
                        operation = operation,
                        requestClose = {
                            vmViewModel.clearFolder()
                        },
                        lifecycleScope = lifecycleScope
                    )
                }
            }
        }
    }

    override fun getTaskDescriptionTitle(): String? {
        return runCatching {
            vmViewModel.session.activityTitle
        }.getOrNull()
    }

    override fun onResume() {
        super.onResume()
        withHandler { onResume() }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 1)
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
    }

    override fun onPause() {
        super.onPause()
        withHandler { onPause() }
        CallbackBridge.resetInputState()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 0)
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
    }

    override fun onStart() {
        super.onStart()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
    }

    override fun onStop() {
        super.onStop()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            CallbackBridge.resetInputState()
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, if (hasFocus) 1 else 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        vmViewModel.onConfigurationChanged()
    }

    override fun setRequestedOrientation(requestedOrientation: Int) {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (vmViewModel.isRunning) {
            requestRefreshWindowSize(screenSize = vmViewModel.screenSize)
        }
    }

    private fun referenceScreenSize(fallback: IntSize): IntSize {
        return vmViewModel.screenSize.takeIf { it.width > 0 && it.height > 0 } ?: fallback
    }

    private var lastWindowSize: IntSize? = null
    private var refreshSizeJob: Job? = null
    private var pendingRefreshSize: IntSize? = null

    private fun requestRefreshWindowSize(screenSize: IntSize) {
        if (screenSize.width <= 0 || screenSize.height <= 0) return
        pendingRefreshSize = screenSize
        refreshSizeJob?.cancel()
        refreshSizeJob = lifecycleScope.launch {
            delay(50L.milliseconds)
            val size = pendingRefreshSize ?: return@launch
            pendingRefreshSize = null
            refreshWindowSize(screenSize = size)
        }
    }

    private suspend fun refreshWindowSize(
        screenSize: IntSize
    ): IntSize = withContext(Dispatchers.Main) {
        val newSize = withHandler {
            when (type) {
                HandlerType.GAME -> computeGameRenderSize(screenSize)
                HandlerType.JVM -> IntSize(
                    getDisplayFriendlyRes(screenSize.width, 0.8f),
                    getDisplayFriendlyRes(screenSize.height, 0.8f)
                )
            }
        }
        lastWindowSize = newSize

        gameTextureView?.surfaceTexture?.setDefaultBufferSize(newSize.width, newSize.height)
        ZLBridgeStates.onWindowChange()
        CallbackBridge.sendUpdateWindowSize(newSize.width, newSize.height)
        if (SdlBridge.sdlEnabled) {
            SDLActivity.getSDLSurface()?.let { surface ->
                surface.surfaceChanged()
                surface.nativeResize(newSize.width, newSize.height)
            }
        }

        newSize
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        stopAllService()
        withHandler { onDestroy() }
        SdlBridge.reset()
        FliteTts.shutdown()
        super.onDestroy()
    }

    private fun stopAllService() {
        stopService(Intent(this, GameService::class.java))
        if (TerracottaVPNService.isRunning()) {

            stopService(Intent(this, TerracottaVPNService::class.java))
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!vmViewModel.keyHandle) return super.dispatchKeyEvent(event)

        val isPressed = event.action == KeyEvent.ACTION_DOWN

        val code = AllSettings.physicalKeyImeCode.state
        if (isPressed && code != null && event.keyCode == code) {

            vmViewModel.textInputMode = vmViewModel.textInputMode.switch()
            return true
        }
        if (vmViewModel.textInputMode == TextInputMode.ENABLE) {
            if (vmViewModel.shouldConsumeSdlModifierKey(event)) {
                vmViewModel.handleSpecialKey(event)
                return true
            }
            if (isPressed && !vmViewModel.keyCanHandle(event)) {
                return super.dispatchKeyEvent(event)
            }

            if (isPressed) {
                vmViewModel.handleSpecialKey(event)
            }
            if (vmViewModel.shouldConsumeSdlTextInput(event)) {
                return true
            }

            if (event.keyCode == KeyEvent.KEYCODE_TAB) {

                return true
            }

            return super.dispatchKeyEvent(event)
        }
        event.device?.let {
            val source = event.source
            if (source and InputDevice.SOURCE_MOUSE_RELATIVE == InputDevice.SOURCE_MOUSE_RELATIVE ||
                source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {

                if (event.keyCode == KeyEvent.KEYCODE_BACK) {

                    withHandler { sendMouseRight(isPressed) }
                    return false
                }
            }
        }
        withHandler {
            if (shouldIgnoreKeyEvent(event)) {
                return super.dispatchKeyEvent(event)
            }
        }
        return true
    }

    @Keep
    fun messageboxShowMessageBox(
        flags: Int,
        title: String?,
        message: String?,
        buttonFlags: IntArray,
        buttonIds: IntArray,
        buttonTexts: Array<String?>,
        colors: IntArray?
    ): Int = SDLActivity.messageboxShowMessageBox(
        this, flags, title, message, buttonFlags, buttonIds, buttonTexts, colors
    )

    private var gameSurface: Surface? = null

    private var requestedRefreshRate = 0f

    private val displayListener = object : DisplayManager.DisplayListener {
        private var lastRequestAt = 0L

        override fun onDisplayAdded(displayId: Int) {}

        override fun onDisplayRemoved(displayId: Int) {}

        override fun onDisplayChanged(displayId: Int) {
            if (SystemClock.uptimeMillis() - lastRequestAt < 1000L) return
            val display = currentDisplay() ?: return
            if (display.displayId != displayId ||
                requestedRefreshRate <= 0f ||
                display.mode.refreshRate >= requestedRefreshRate - 0.1f
            ) return
            lastRequestAt = SystemClock.uptimeMillis()
            applyMaxRefreshRatePolicy()
        }
    }

    fun applyMaxRefreshRatePolicy() {
        val display = currentDisplay() ?: return
        if (!AllSettings.requestMaxRefreshRate.getValue()) {
            requestedRefreshRate = 0f
            setPreferredDisplayModeId(0)
            clearSurfaceFrameRate()
            return
        }
        val best = maxRefreshRateMode(display)
        requestedRefreshRate = best.refreshRate
        setPreferredDisplayModeId(best.modeId)
        voteMaxDisplayRefreshRate()
    }

    private fun currentDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else @Suppress("DEPRECATION") windowManager.defaultDisplay
    }

    private fun maxRefreshRateMode(display: Display): Display.Mode {
        val current = display.mode
        return display.supportedModes.fold(current) { best, mode ->
            if (mode.physicalWidth == current.physicalWidth &&
                mode.physicalHeight == current.physicalHeight &&
                mode.refreshRate > best.refreshRate
            ) mode else best
        }
    }

    private fun voteMaxDisplayRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val surface = gameSurface ?: return
        val display = currentDisplay() ?: return
        surface.setFrameRate(
            maxRefreshRateMode(display).refreshRate,
            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
            Surface.CHANGE_FRAME_RATE_ALWAYS
        )
    }

    private fun setPreferredDisplayModeId(modeId: Int) {
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }

    private fun clearSurfaceFrameRate() {
        gameSurface?.let { surface ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                surface.clearFrameRate()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val nativeSurface = Surface(surface)
        gameSurface = nativeSurface
        applyMaxRefreshRatePolicy()
        SdlBridge.prepareSurface(this, nativeSurface, gameTextureView?.parent as? ViewGroup, surface)

        CallbackBridge.setDirectGamepadEnableHandler {
            LoggerBridge.append("CMCLauncher: Direct gamepad handler enabled")
        }
        if (vmViewModel.isRunning) {
            ZLBridge.setupBridgeWindow(nativeSurface)

            withHandler { mIsSurfaceDestroyed = false }
            requestRefreshWindowSize(screenSize = referenceScreenSize(fallback = IntSize(width, height)))
            return
        }
        vmViewModel.isRunning = true

        withHandler { mIsSurfaceDestroyed = false }
        lifecycleScope.launch(Dispatchers.Default) {
            val screenSize = vmViewModel.screenSizeBridge.awaitData()
            val currentSize = refreshWindowSize(screenSize = screenSize)
            withHandler {
                execute(
                    surface = Surface(surface),
                    screenSize = currentSize,
                    scope = lifecycleScope
                )
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (withHandler { mIsSurfaceDestroyed }) return
        requestRefreshWindowSize(screenSize = referenceScreenSize(fallback = IntSize(width, height)))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        gameSurface = null
        val nativeSurface = SDLSurface.getNativeSurface()
        if (SdlBridge.beginSurfaceDestroy(surface, nativeSurface)) {
            if (SdlBridge.sdlEnabled) {
                SDLActivity.getSDLSurface()?.surfaceDestroyed()
            }
            SdlBridge.unregisterSurface(nativeSurface)
        }
        withHandler { mIsSurfaceDestroyed = true }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        captureMagnifierFrame()
    }

    @Composable
    private fun Screen(
        content: @Composable () -> Unit = {}
    ) {
        val imeInsets = WindowInsets.ime
        val inputArea by withHandler { inputArea }.collectAsStateWithLifecycle()
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenSize = rememberBoxSize()

            val changed by vmViewModel.onConfigurationChanged.collectAsStateWithLifecycle()
            LaunchedEffect(screenSize, changed) {
                vmViewModel.screenSize = screenSize
                vmViewModel.screenSizeBridge.provideData(screenSize)
                if (changed) {
                    requestRefreshWindowSize(screenSize = screenSize)
                    vmViewModel.onConfigurationChanged(false)
                }
            }

            val letterboxed = withHandler { type } == HandlerType.GAME &&
                    AllSettings.resolutionRule.state == ResolutionRule.CUSTOM
            val renderSize = rememberGameRenderSize(screenSize)
            val displaySize = if (letterboxed) computeGameDisplayLayout(screenSize, renderSize).displaySize else screenSize

            AndroidView(
                modifier = Modifier
                    .then(
                        if (displaySize == screenSize) Modifier.fillMaxSize()
                        else Modifier
                            .align(Alignment.Center)
                            .size(
                                width = with(density) { displaySize.width.toDp() },
                                height = with(density) { displaySize.height.toDp() }
                            )
                    )
                    .absoluteOffset {
                        val area = inputArea ?: return@absoluteOffset IntOffset.Zero
                        val imeHeight = imeInsets.getBottom(this@absoluteOffset)
                        val bottomDistance = screenSize.height - area.bottom
                        val bottomPadding = (imeHeight - bottomDistance).coerceAtLeast(0)
                        IntOffset(0, -bottomPadding)
                    },
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = true
                        alpha = 1.0f
                        surfaceTextureListener = this@VMActivity
                        setOnApplyWindowInsetsListener { v, insets ->
                            if (SdlBridge.sdlEnabled && Build.VERSION.SDK_INT >= 30) {
                                SDLActivity.notifyImeVisibilityChanged(
                                    insets.isVisible(AndroidWindowInsets.Type.ime())
                                )
                            }
                            v.onApplyWindowInsets(insets)
                        }
                    }.also { texture ->
                        gameTextureView = texture
                    }
                }
            )

            content()

            InputMagnifier(
                inputEnabled = vmViewModel.textInputMode == TextInputMode.ENABLE,
                state = vmViewModel.inputMagnifierState,
                frame = { magnifierFrame },
                frameVersion = { magnifierFrameVersion },
                floatingContent = {
                    InputMagnifierContent(
                        modifier = Modifier
                            .padding(all = 8.dp)
                            .align(Alignment.TopEnd),
                        onClose = { vmViewModel.inputMagnifierState.close() }
                    )
                }
            )
            LaunchedEffect(vmViewModel.inputMagnifierState.isVisible) {
                if (vmViewModel.inputMagnifierState.isVisible) captureMagnifierFrame()
            }
        }
    }
}

@Composable
private fun InputMagnifierContent(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.65f),
        contentColor = Color.White
    ) {
        Row {
            Box {
                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .focusProperties { canFocus = false },
                    onClick = { menuExpanded = true }
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.generic_more)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = MaterialTheme.shapes.large,
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.focusProperties { canFocus = false },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.game_menu_option_ime_magnifier_auto_show)
                                )
                                Checkbox(
                                    modifier = Modifier.focusProperties { canFocus = false },
                                    checked = AllSettings.imeMagnifierAutoShow.state,
                                    onCheckedChange = {
                                        AllSettings.imeMagnifierAutoShow.save(
                                            !AllSettings.imeMagnifierAutoShow.state
                                        )
                                    }
                                )
                            }
                        },
                        onClick = {
                            AllSettings.imeMagnifierAutoShow.save(
                                !AllSettings.imeMagnifierAutoShow.state
                            )
                        }
                    )
                }
            }
            IconButton(
                modifier = Modifier
                    .size(40.dp)
                    .focusProperties { canFocus = false },
                onClick = onClose,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.generic_close)
                )
            }
        }
    }
}

fun runGame(
    context: Context,
    version: Version,
    account: Account,
) {
    startGameService(context)
    val intent = Intent(context, VMActivity::class.java).apply {
        putExtra(INTENT_RUN_GAME, true)
        putExtra(INTENT_GAME_CONFIG, LaunchConfig(version, account))
    }
    context.startActivity(intent)
}

fun runJar(
    context: Context,
    jarFile: File,
    jreName: String? = null,
    customArgs: String? = null
) {
    RuntimesManager.getExactJreName(8) ?: run {
        Toast.makeText(context, R.string.multirt_no_java_8, Toast.LENGTH_SHORT).show()
        return
    }

    val jvmArgsPrefix = customArgs?.let { "$it " } ?: ""
    val jvmArgs = "$jvmArgsPrefix-jar ${jarFile.absolutePath}"

    val jvmLaunchInfo = JvmLaunchInfo(
        jvmArgs = jvmArgs,
        jreName = jreName
    )

    startGameService(context)

    val intent = Intent(context, VMActivity::class.java).apply {
        putExtra(INTENT_RUN_JAR, true)
        putExtra(INTENT_JAR_INFO, jvmLaunchInfo)
    }
    context.startActivity(intent)
}

private fun startGameService(context: Context) {
    runCatching {

        context.startService(Intent(context, GameService::class.java))
    }
}
