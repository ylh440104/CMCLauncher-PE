package com.cmc.launcher.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.inputmap.keycodes.ControlEventKeycode
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.inputmap.keycodes.OPEN_CHAT
import com.movtery.inputmap.keycodes.OPEN_CHAT_VALUE
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.cmc.launcher.R
import com.cmc.launcher.bridge.CURSOR_DISABLED
import com.cmc.launcher.bridge.ZLBridgeStates
import com.cmc.launcher.bridge.ZLNativeInvoker
import com.cmc.launcher.game.input.LWJGLCharSender
import com.cmc.launcher.game.keycodes.mapToKeycode
import com.cmc.launcher.game.launch.handler.GameHandler
import com.cmc.launcher.game.sdl.SdlBridge
import com.cmc.launcher.game.sdl.SdlTextSender
import com.cmc.launcher.game.support.touch_controller.touchControllerInputModifier
import com.cmc.launcher.game.support.touch_controller.touchControllerTouchModifier
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.isLauncherInDarkTheme
import com.cmc.launcher.setting.enums.toAction
import com.cmc.launcher.terracotta.Terracotta
import com.cmc.launcher.ui.components.BackgroundCard
import com.cmc.launcher.ui.components.MenuState
import com.cmc.launcher.ui.components.rememberBoxSize
import com.cmc.launcher.ui.control.MinecraftHotbar
import com.cmc.launcher.ui.control.event.launcherEvent
import com.cmc.launcher.ui.control.event.lwjglEvent
import com.cmc.launcher.ui.control.gamepad.GamepadKeyListener
import com.cmc.launcher.ui.control.gamepad.GamepadOnActionListener
import com.cmc.launcher.ui.control.gamepad.GamepadStickMovementListener
import com.cmc.launcher.ui.control.gamepad.SimpleGamepadCapture
import com.cmc.launcher.ui.control.gyroscope.GyroscopeReader
import com.cmc.launcher.ui.control.gyroscope.isGyroscopeAvailable
import com.cmc.launcher.ui.control.hotbarPercentage
import com.cmc.launcher.ui.control.input.TextInputMode
import com.cmc.launcher.ui.control.mouse.SwitchableMouseLayout
import com.cmc.launcher.ui.screens.game.elements.DraggableGameBall
import com.cmc.launcher.ui.screens.game.elements.ForceCloseOperation
import com.cmc.launcher.ui.screens.game.elements.GameMenuSubscreen
import com.cmc.launcher.ui.screens.game.elements.GamepadModePromptDialog
import com.cmc.launcher.ui.screens.game.elements.LogBox
import com.cmc.launcher.ui.screens.game.elements.LogState
import com.cmc.launcher.ui.screens.game.elements.ReplacementControlOperation
import com.cmc.launcher.ui.screens.game.elements.ReplacementControlState
import com.cmc.launcher.ui.screens.game.elements.SendKeycodeOperation
import com.cmc.launcher.ui.screens.game.elements.SendKeycodeState
import com.cmc.launcher.ui.screens.game.multiplayer.TerracottaOperation
import com.cmc.launcher.ui.screens.game.multiplayer.rememberTerracottaViewModel
import com.cmc.launcher.ui.screens.main.control_editor.ControlEditor
import com.cmc.launcher.utils.currentGameDisplayLayout
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.viewmodel.EditorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.GamepadViewModel
import com.cmc.launcher.viewmodel.sendToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "GameScreen"

private const val FPS_HISTORY_SIZE = 15

private const val FPS_RESYNC_NODES = 10

private class GameViewModel(
    private val version: Version,
    private val onChangeTextInputMode: (TextInputMode?) -> Unit
) : ViewModel() {

    var gameMenuState by mutableStateOf(MenuState.NONE)

    var forceCloseState by mutableStateOf<ForceCloseOperation>(ForceCloseOperation.None)

    var sendKeycodeState by mutableStateOf<SendKeycodeState>(SendKeycodeState.None)

    var replacementControlState by mutableStateOf<ReplacementControlState>(ReplacementControlState.None)

    var moveOnlyPointers = mutableSetOf<PointerId>()

    var occupiedPointers = mutableSetOf<PointerId>()

    var gameFps by mutableIntStateOf(0)
        private set

    var fpsHistory by mutableStateOf<List<Int>>(emptyList())
        private set

    var fpsMax by mutableIntStateOf(0)
        private set

    var fpsMin by mutableIntStateOf(0)
        private set

    private var fpsMaxResyncCountdown = -1

    private var fpsMinResyncCountdown = -1
    private var fpsJob: Job? = null

    fun startFpsCapture() {
        if (fpsJob?.isActive == true) return

        fpsHistory = emptyList()
        fpsMax = 0
        fpsMin = 0
        fpsMaxResyncCountdown = -1
        fpsMinResyncCountdown = -1

        fpsJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                runCatching {
                    ensureActive()
                }.onFailure {
                    break
                }
                recordFps(CallbackBridge.getCurrentFps())
                delay(1000L.milliseconds)
            }
        }
    }

    fun stopFpsCapture() {
        fpsJob?.cancel()
        fpsJob = null
    }

    private fun recordFps(fps: Int) {
        gameFps = fps

        if (fpsMaxResyncCountdown > 0) fpsMaxResyncCountdown--
        if (fpsMinResyncCountdown > 0) fpsMinResyncCountdown--
        if (fpsMaxResyncCountdown == 0) {
            fpsMax = fpsHistory.max()
            fpsMaxResyncCountdown = -1
        }
        if (fpsMinResyncCountdown == 0) {
            fpsMin = fpsHistory.min()
            fpsMinResyncCountdown = -1
        }

        if (fpsHistory.isEmpty()) {
            fpsMax = fps
            fpsMin = fps
        } else {
            if (fps > fpsMax) {
                fpsMax = fps
                fpsMaxResyncCountdown = -1
            }
            if (fps < fpsMin) {
                fpsMin = fps
                fpsMinResyncCountdown = -1
            }
        }
        fpsHistory = fpsHistory + fps

        if (fpsHistory.size > FPS_HISTORY_SIZE) {
            val removed = fpsHistory.first()
            fpsHistory = fpsHistory.drop(1)
            if (removed == fpsMax && fpsHistory.none { it == fpsMax }) {
                fpsMaxResyncCountdown = FPS_RESYNC_NODES
            }
            if (removed == fpsMin && fpsHistory.none { it == fpsMin }) {
                fpsMinResyncCountdown = FPS_RESYNC_NODES
            }
        }
    }

    var editorRefresh by mutableIntStateOf(0)
        private set

    var observableLayout by mutableStateOf<ObservableControlLayout?>(null)
        private set

    var currentControlFile by mutableStateOf<File?>(null)
        private set

    var controlLayerHideState by mutableStateOf(HideLayerWhen.None)
        private set

    var isEditingLayout by mutableStateOf(false)
        private set

    fun switchControlLayer(hideWhen: HideLayerWhen) {
        if (controlLayerHideState != hideWhen) controlLayerHideState = hideWhen
    }

    val mouseScrollUpEvent = MouseScrollEvent(viewModelScope, 1.0)
    val mouseScrollDownEvent = MouseScrollEvent(viewModelScope, -1.0)

    val gameTextSender = GameTextSender(viewModelScope)

    val eventHandler = EventHandler { event, pressed ->
        onKeyEvent(event, pressed)
    }

    fun onKeyEvent(event: ClickEvent, pressed: Boolean) {
        val key = event.key
        when (event.type) {
            ClickEvent.Type.Key -> {
                lwjglEvent(
                    eventKey = key,
                    isMouse = key.startsWith("GLFW_MOUSE_", false),
                    isPressed = pressed
                )
            }
            ClickEvent.Type.LauncherEvent -> {
                launcherEvent(
                    eventKey = key,
                    isPressed = pressed,
                    onSwitchIME = { onChangeTextInputMode(null) },
                    onSwitchMenu = { switchMenu() },
                    onSingleScrollUp = { mouseScrollUpEvent.scrollSingle() },
                    onSingleScrollDown = { mouseScrollDownEvent.scrollSingle() },
                    onLongScrollUp = { mouseScrollUpEvent.scrollLongPress() },
                    onLongScrollUpCancel = { mouseScrollUpEvent.cancel() },
                    onLongScrollDown = { mouseScrollDownEvent.scrollLongPress() },
                    onLongScrollDownCancel = { mouseScrollDownEvent.cancel() }
                )
            }
            ClickEvent.Type.SendText -> {

                if (pressed) {
                    val text = event.key
                    val inGame = ZLBridgeStates.cursorMode.value == CURSOR_DISABLED
                    gameTextSender.send(GameTextSender.Data(text, inGame))
                }
                return
            }
            else -> return
        }
    }

    fun replaceControlLayout(layoutFile: File) {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout(layoutFile)
        }
    }

    private val layoutMutex = Mutex()
    suspend fun loadControlLayout(layoutFile: File? = version.getControlPath()) {
        layoutMutex.withLock {
            withContext(Dispatchers.Main) {
                observableLayout = null
                val layout = withContext(Dispatchers.IO) {
                    delay(10L.milliseconds)
                    currentControlFile = layoutFile
                    getLayout(layoutFile)
                }

                observableLayout = ObservableControlLayout(layout)
            }
        }
    }

    private fun getLayout(layoutFile: File? = currentControlFile): ControlLayout {
        return layoutFile?.let {
            try {
                loadLayoutFromFile(it)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load control layout: $it", e)
                null
            }
        } ?: EmptyControlLayout
    }

    fun startControlEditor(editorVM: EditorViewModel) {
        if (!isEditingLayout) {
            clearState()
            editorVM.initLayout(getLayout())
            isEditingLayout = true
        }
    }

    fun exitControlEditor() {
        viewModelScope.launch(Dispatchers.Main) {
            if (isEditingLayout) {
                isEditingLayout = false
                loadControlLayout(currentControlFile)
                editorRefresh++
            }
        }
    }

    fun switchMenu() {
        this.gameMenuState = this.gameMenuState.next()
    }

    fun clearState() {
        mouseScrollUpEvent.cancel()
        mouseScrollDownEvent.cancel()
        gameTextSender.cancel()
        onChangeTextInputMode(TextInputMode.DISABLE)
        moveOnlyPointers.clear()
        occupiedPointers.clear()
    }

    init {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout()
        }
    }

    override fun onCleared() {
        clearState()
    }
}

private class MouseScrollEvent(
    private val scope: CoroutineScope,
    private val offset: Double
) {
    private var mouseScrollJob: Job? = null

    fun cancel() {
        mouseScrollJob?.cancel()
        mouseScrollJob = null
    }

    fun scrollSingle() {
        CallbackBridge.sendScroll(0.0, offset)
    }

    fun scrollLongPress() {
        mouseScrollJob?.cancel()
        mouseScrollJob = scope.launch {
            while (true) {
                try {
                    ensureActive()
                    CallbackBridge.sendScroll(0.0, offset)
                    delay(50L.milliseconds)
                } catch (_: Exception) {
                    break
                }
            }
            mouseScrollJob = null
        }
    }
}

private class GameTextSender(private val scope: CoroutineScope) {

    data class Data(
        val text: String,
        val inGame: Boolean
    )

    private var messageChannel: Channel<Data>? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        messageChannel?.close()
        messageChannel = null
        job = null
    }

    fun send(data: Data) {
        if (job?.isActive != true || messageChannel == null) {
            job?.cancel()
            messageChannel?.close()

            messageChannel = Channel(Channel.UNLIMITED)
            job = scope.launch {
                messageChannel?.let { channel ->
                    for ((text, inGame) in channel) {
                        sendMessage(text, inGame)
                    }
                }
            }
        }

        messageChannel?.trySend(data)
    }

    private suspend fun sendMessage(text: String, inGame: Boolean) {
        withContext(Dispatchers.Main) {

            val useSdl = SdlBridge.sdlEnabled && SdlBridge.isSdlRenderActive()

            fun sendText() {
                for (ch in text) {
                    if (useSdl) {
                        SdlTextSender.sendChar(ch)
                    } else {
                        LWJGLCharSender.sendChar(ch)
                    }
                }
            }

            if (inGame) {

                mapToKeycode(OPEN_CHAT, OPEN_CHAT_VALUE)?.let { openChat ->
                    if (useSdl) {
                        SdlTextSender.sendKey(openChat)

                        if (SdlTextSender.awaitTextInputActive()) {
                            sendText()
                            delay(50L.milliseconds)
                            SdlTextSender.sendEnter()
                        }
                    } else {
                        CallbackBridge.sendKeyPress(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        LWJGLCharSender.sendEnter()
                    }
                }
            } else {

                sendText()
            }
        }
    }
}

@Composable
private fun rememberGameViewModel(
    version: Version,
    onChangeTextInputMode: (TextInputMode?) -> Unit
) = viewModel(
    key = version.toString()
) {
    GameViewModel(version, onChangeTextInputMode)
}

@Composable
private fun rememberEditorViewModel(
    key: String
)= viewModel(
    key = key
) {
    EditorViewModel()
}

@Composable
fun GameScreen(
    version: Version,
    gameHandler: GameHandler,
    showGameInfo: Boolean,
    onInfoBoxClose: () -> Unit,
    logState: LogState,
    onLogStateChange: (LogState) -> Unit,
    textInputMode: TextInputMode,
    isTouchProxyEnabled: Boolean,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    getAccountName: () -> String?,
    eventViewModel: EventViewModel,
    gamepadViewModel: GamepadViewModel,
) {
    val context = LocalContext.current
    val viewModel = rememberGameViewModel(version) { mode ->
        eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(mode))
    }
    val editorViewModel = rememberEditorViewModel("ControlEditor_Times=${viewModel.editorRefresh}")
    val cursorMode by ZLBridgeStates.cursorMode.collectAsStateWithLifecycle()
    val isGrabbing = remember(cursorMode) {
        cursorMode == CURSOR_DISABLED
    }
    val terracottaViewModel = rememberTerracottaViewModel(
        keyTag = gameHandler.toString() + "_Terracotta",
        gameHandler = gameHandler,
        eventViewModel = eventViewModel,
        getUserName = getAccountName
    )

    LaunchedEffect(viewModel.isEditingLayout, viewModel.gameMenuState) {

        val allowKeyHandle = !viewModel.isEditingLayout && viewModel.gameMenuState != MenuState.SHOW
        eventViewModel.sendEvent(EventViewModel.Event.Game.KeyHandle(allowKeyHandle))
    }

    SendKeycodeOperation(
        operation = viewModel.sendKeycodeState,
        onChange = { viewModel.sendKeycodeState = it },
        lifecycleScope = viewModel.viewModelScope
    )

    ForceCloseOperation(
        operation = viewModel.forceCloseState,
        onChange = { viewModel.forceCloseState = it },
        onForceClose = {
            Terracotta.setWaiting(false)
            ZLNativeInvoker.jvmExit(0, false)
        },
        text = stringResource(R.string.game_menu_option_force_close_text)
    )

    ReplacementControlOperation(
        operation = viewModel.replacementControlState,
        onChange = { viewModel.replacementControlState = it },
        currentLayout = viewModel.currentControlFile,
        replacementControl = { viewModel.replaceControlLayout(it) }
    )

    TerracottaOperation(
        viewModel = terracottaViewModel,
        onShowToast = { text, duration ->
            eventViewModel.sendToast(text, duration)
        }
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenSize = rememberBoxSize()

        if (!viewModel.isEditingLayout) {
            if (AllSettings.gamepadControl.state) {
                GamepadOnActionListener(
                    gamepadViewModel = gamepadViewModel,
                    onAction = {
                        viewModel.switchControlLayer(HideLayerWhen.WhenGamepad)
                    }
                )
            }

            if (AllSettings.gamepadControl.state && gamepadViewModel.gamepadEngaged) {

                GamepadKeyListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { events, pressed ->
                        events.forEach { event ->
                            viewModel.onKeyEvent(event, pressed)
                        }
                    }
                )

                GamepadStickMovementListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { event, pressed ->
                        viewModel.onKeyEvent(event, pressed)
                    }
                )
            }

            ControlBoxLayout(
                modifier = Modifier.fillMaxSize(),
                observedLayout = viewModel.observableLayout,
                eventHandler = viewModel.eventHandler,
                checkOccupiedPointers = { viewModel.occupiedPointers.contains(it) },
                opacity = { (AllSettings.controlsOpacity.state.toFloat() / 100f).coerceIn(0f, 1f) },
                markPointerAsMoveOnly = { viewModel.moveOnlyPointers.add(it) },
                onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                onReleasePointer = { viewModel.occupiedPointers.remove(it) },
                isCursorGrabbing = isGrabbing,
                hideLayerWhen = viewModel.controlLayerHideState,
                isDark = isLauncherInDarkTheme()
            ) {

                MouseControlLayout(
                    isTouchProxyEnabled = isTouchProxyEnabled,
                    modifier = Modifier.fillMaxSize(),
                    cursorMode = cursorMode,
                    screenSize = screenSize,
                    onInputAreaRectUpdated = onInputAreaRectUpdated,
                    textInputMode = textInputMode,
                    isMoveOnlyPointer = { viewModel.moveOnlyPointers.contains(it) },
                    onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                    onReleasePointer = {
                        viewModel.occupiedPointers.remove(it)
                        viewModel.moveOnlyPointers.remove(it)
                    },
                    onMouseMoved = { viewModel.switchControlLayer(HideLayerWhen.WhenMouse) },
                    onTouch = { viewModel.switchControlLayer(HideLayerWhen.None) },
                    gamepadViewModel = gamepadViewModel.takeIf { AllSettings.gamepadControl.state }
                )
            }

            val gameDisplayLayout = currentGameDisplayLayout(screenSize)
            MinecraftHotbar(
                screenSize = screenSize,
                rule = AllSettings.hotbarRule.state,
                widthPercentage = AllSettings.hotbarWidth.state.hotbarPercentage(),
                heightPercentage = AllSettings.hotbarHeight.state.hotbarPercentage(),
                sendKeycode = { keycode ->
                    CallbackBridge.sendKeyPress(keycode)
                },
                isGrabbing = isGrabbing,
                displayOffset = gameDisplayLayout.offset,
                onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                onReleasePointer = { viewModel.occupiedPointers.remove(it) }
            )
        }

        val isGyroscopeAvailable = remember(context) {
            isGyroscopeAvailable(context = context)
        }
        if (isGrabbing && isGyroscopeAvailable && AllSettings.gyroscopeControl.state) {
            GyroscopeReader(
                xEvent = { delta ->
                    CallbackBridge.sendCursorDelta(if (AllSettings.gyroscopeInvertX.state) -delta else delta, 0f)
                },
                yEvent = { delta ->
                    CallbackBridge.sendCursorDelta(0f, if (AllSettings.gyroscopeInvertY.state) delta else -delta)
                },
                sampleRate = AllSettings.gyroscopeSampleRate.state,
                smoothing = AllSettings.gyroscopeSmoothing.state,
                smoothingWindow = AllSettings.gyroscopeSmoothingWindow.state,
                sensitivity = AllSettings.gyroscopeSensitivity.state / 100f
            )
        }

        GameInfoBox(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(all = 16.dp),
            versionName = version.getVersionName(),
            versionInfo = version.getVersionInfo()?.getInfoString(),
            visible = showGameInfo,
            onClose = onInfoBoxClose
        )

        LogBox(
            enableLog = !viewModel.isEditingLayout && logState.value,
            onClose = {
                onLogStateChange(LogState.CLOSE)
            },
            modifier = Modifier.fillMaxSize()
        )

        GameMenuSubscreen(
            state = viewModel.gameMenuState,
            closeScreen = { viewModel.gameMenuState = MenuState.HIDE },
            onForceClose = { viewModel.forceCloseState = ForceCloseOperation.Show },
            onSwitchLog = { onLogStateChange(logState.next()) },
            enableTerracotta = AllSettings.enableTerracotta.state,
            onOpenTerracottaMenu = { terracottaViewModel.openMenu() },
            onRefreshWindowSize = { eventViewModel.sendEvent(EventViewModel.Event.Game.RefreshSize) },
            onInputMethod = {
                eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(null))
            },
            onSendKeycode = { viewModel.sendKeycodeState = SendKeycodeState.ShowDialog },
            onReplacementControl = { viewModel.replacementControlState = ReplacementControlState.Show },
            onEditLayout = {
                viewModel.startControlEditor(
                    editorVM = editorViewModel
                )
            },
            onShowToast = { text, duration ->
                eventViewModel.sendToast(text, duration)
            }
        )

        if (AllSettings.gamepadControl.state) {

            SimpleGamepadCapture(
                gamepadViewModel = gamepadViewModel
            )
        }

        GamepadModePromptDialog(
            visible = gamepadViewModel.modePromptVisible,
            onConfirm = { mode ->
                gamepadViewModel.confirmModePrompt(mode)
            }
        )

        if (viewModel.isEditingLayout) {
            viewModel.currentControlFile?.let {
                ControlEditor(
                    viewModel = editorViewModel,
                    targetFile = it,
                    exit = {
                        viewModel.exitControlEditor()
                    },
                    menuExit = {
                        editorViewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                viewModel.exitControlEditor()
                            }
                        )
                    }
                )
            }
        } else {
            if (AllSettings.showMenuBall.state) {

                val showFps = AllSettings.showFPS.state
                DisposableEffect(showFps) {
                    if (showFps) viewModel.startFpsCapture()
                    onDispose {
                        viewModel.stopFpsCapture()
                    }
                }

                DraggableGameBall(
                    position = AllSettings.menuBallPos.state,
                    onPositionChanged = {
                        AllSettings.menuBallPos.updateState(it)
                    },
                    onSavePos = {
                        AllSettings.menuBallPos.save()
                    },
                    gameFps = { if (showFps) viewModel.gameFps else null },
                    fpsDisplayMode = AllSettings.fpsDisplayMode.state,
                    fpsHistory = { viewModel.fpsHistory },
                    fpsMax = { viewModel.fpsMax },
                    fpsMin = { viewModel.fpsMin },
                    showMemory = AllSettings.showMemory.state,
                    opened = viewModel.gameMenuState == MenuState.SHOW,
                    alpha = AllSettings.menuBallOpacity.state / 100f,
                    onClick = {
                        viewModel.switchMenu()
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        eventViewModel.events
            .filterIsInstance<EventViewModel.Event.Game>()
            .collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.OnBack -> {
                        if (viewModel.isEditingLayout) {

                            editorViewModel.onBackPressed(
                                context = context,
                                onExit = {
                                    viewModel.exitControlEditor()
                                }
                            )
                        } else if (!AllSettings.showMenuBall.getValue()) {
                            viewModel.switchMenu()
                        } else {

                            val event = ClickEvent(
                                type = ClickEvent.Type.Key,
                                key = ControlEventKeycode.GLFW_KEY_ESCAPE
                            )
                            viewModel.onKeyEvent(event, true)
                            delay(10L.milliseconds)
                            viewModel.onKeyEvent(event, false)
                        }
                    }
                    is EventViewModel.Event.Game.OnResume -> {
                        viewModel.clearState()
                    }
                    else -> {  }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameInfoBox(
    versionName: String,
    versionInfo: String?,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (visible)  {
        BackgroundCard(
            modifier = modifier,
            influencedByBackground = false,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_loading),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.game_loading_version_name, versionName),
                            style = MaterialTheme.typography.labelLarge
                        )
                        versionInfo?.let { info ->
                            Text(
                                text = stringResource(R.string.game_loading_version_info, info),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                IconButton(
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                    onClick = onClose
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
}

@Preview(showBackground = false)
@Composable
private fun PreviewGameInfoBox() {
    MaterialExpressiveTheme {
        GameInfoBox(
            versionName = "1.21.11",
            versionInfo = "1.21.11",
            visible = true,
            onClose = {}
        )
    }
}

@Composable
private fun MouseControlLayout(
    isTouchProxyEnabled: Boolean,
    modifier: Modifier = Modifier,
    cursorMode: Int,
    screenSize: IntSize,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    textInputMode: TextInputMode,
    isMoveOnlyPointer: (PointerId) -> Boolean,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    onMouseMoved: () -> Unit,
    onTouch: () -> Unit,
    gamepadViewModel: GamepadViewModel?
) {
    Box(
        modifier = modifier
            .then(
                if (isTouchProxyEnabled) {
                    Modifier
                        .touchControllerTouchModifier(
                            screenSize = screenSize
                        )
                        .touchControllerInputModifier(
                            screenSize = screenSize,
                            onInputAreaRectUpdated = onInputAreaRectUpdated,
                        )
                } else Modifier
            )
    ) {

        val capturedSpeedFactor = AllSettings.mouseCaptureSensitivity.state / 100f
        val capturedTapMouseAction = AllSettings.gestureTapMouseAction.state.toAction()
        val capturedLongPressMouseAction = AllSettings.gestureLongPressMouseAction.state.toAction()

        SwitchableMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenSize = screenSize,
            cursorMode = cursorMode,
            onTouch = onTouch,
            onMouse = onMouseMoved,
            gamepadViewModel = gamepadViewModel,
            onTap = { position ->
                val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(position)
                CallbackBridge.putMouseEventWithCoords(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), gamePosition.x, gamePosition.y)
            },
            onCapturedTap = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedTapMouseAction)
                }
            },
            onLongPress = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), true)
            },
            onLongPressEnd = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), false)
            },
            onCapturedLongPress = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, true)
                }
            },
            onCapturedLongPressEnd = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, false)
                }
            },
            onPointerMove = { pos ->
                pos.sendPosition(screenSize)
            },
            onCapturedMove = { delta ->
                CallbackBridge.sendCursorDelta(
                    delta.x * capturedSpeedFactor,
                    delta.y * capturedSpeedFactor
                )
            },
            onMouseScroll = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            },
            onMouseButton = { button, pressed ->
                val code = LWJGLCharSender.getMouseButton(button) ?: return@SwitchableMouseLayout
                CallbackBridge.sendMouseButton(code.toInt(), pressed)
            },
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            enableScrollGesture = AllSettings.gestureControl.state,
            onScrollGesture = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            }
        )
    }
}

private fun Offset.sendPosition(screenSize: IntSize) {
    val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(this)
    CallbackBridge.sendCursorPos(gamePosition.x, gamePosition.y)
}
