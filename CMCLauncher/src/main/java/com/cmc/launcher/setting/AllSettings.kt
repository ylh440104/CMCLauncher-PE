package com.cmc.launcher.setting

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.movtery.layer_controller.utils.snap.SnapMode
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.path.GamePathManager
import com.cmc.launcher.game.version.installed.GraphicsApi
import com.cmc.launcher.setting.enums.ActionMenuSide
import com.cmc.launcher.setting.enums.AppLanguage
import com.cmc.launcher.setting.enums.BackgroundBlur
import com.cmc.launcher.setting.enums.DarkMode
import com.cmc.launcher.setting.enums.FpsDisplayMode
import com.cmc.launcher.setting.enums.GamepadInputMode
import com.cmc.launcher.setting.enums.GestureActionType
import com.cmc.launcher.setting.enums.MirrorSourceType
import com.cmc.launcher.setting.enums.MouseControlMode
import com.cmc.launcher.setting.enums.MouseTouchBlockMode
import com.cmc.launcher.setting.enums.ResolutionRule
import com.cmc.launcher.ui.control.HotbarRule
import com.cmc.launcher.ui.control.gamepad.JoystickMode
import com.cmc.launcher.ui.control.mouse.CENTER_HOTSPOT
import com.cmc.launcher.ui.control.mouse.CursorHotspot
import com.cmc.launcher.ui.control.mouse.LEFT_TOP_HOTSPOT
import com.cmc.launcher.ui.theme.ColorThemeType

object AllSettings : SettingsRegistry() {

    val renderer = stringSetting("renderer", "")

    val sfpewPluginLayer = boolSetting("sfpewPluginLayer", false)

    val sfpewRelaxedContext = boolSetting("sfpewRelaxedContext", false)

    val vulkanDriver = stringSetting("vulkanDriver", "default turnip")

    val graphicsApi = enumSetting("graphicsApi", GraphicsApi.DEFAULT_OPENGL)

    val resolutionRatio = intSetting("resolutionRatio", 100, 25..300)

    val resolutionRule = enumSetting("resolutionRule", ResolutionRule.PERCENTAGE)

    val customResolutionWidth = intSetting("customResolutionWidth", 0)

    val customResolutionHeight = intSetting("customResolutionHeight", 0)

    val gameFullScreen = boolSetting("gameFullScreen", true)

    val sustainedPerformance = boolSetting("sustainedPerformance", false)

    val requestMaxRefreshRate = boolSetting("requestMaxRefreshRate", true)

    val zinkPreferSystemDriver = boolSetting("zinkPreferSystemDriver", false)

    val vsyncInZink = boolSetting("vsyncInZink", false)

    val dumpShaders = boolSetting("dumpShaders", false)

    val versionIsolation = boolSetting("versionIsolation", true)

    val skipGameIntegrityCheck = boolSetting("skipGameIntegrityCheck", false)

    val versionCustomInfo = stringSetting("versionCustomInfo", "${BuildKeys.LAUNCHER_IDENTIFIER}[zl_version]")

    val javaRuntime = stringSetting("javaRuntime", "")

    val autoPickJavaRuntime = boolSetting("autoPickJavaRuntime", true)

    val ramAllocation = intSetting("ramAllocation", null, min = 256)

    val jvmArgs = stringSetting("jvmArgs", "")

    val disableNativeLibPlugins = stringListSetting("nativeLibPlugins", emptyList())

    val showLogAutomatic = boolSetting("showLogAutomatic", false)

    val logTextSize = intSetting("logTextSize", 15, 5..20)

    val logBufferFlushInterval = intSetting("logBufferFlushInterval", 200, 100..1000)

    val physicalMouseMode = boolSetting("physicalMouseMode", true)

    val physicalKeyImeCode = intSetting("physicalKeyImeCode", null)

    val hideMouse = boolSetting("hideMouse", false)

    val mouseSize = intSetting("mouseSize", 24, 5..50)

    val arrowMouseHotspot = parcelableSetting("arrowMouseHotspot", LEFT_TOP_HOTSPOT)

    val linkMouseHotspot = parcelableSetting("linkMouseHotspot", CursorHotspot(xPercent = 23, yPercent = 0))

    val iBeamMouseHotspot = parcelableSetting("iBeamMouseHotspot", CENTER_HOTSPOT)

    val crossHairMouseHotspot = parcelableSetting("crossHairMouseHotspot", CENTER_HOTSPOT)

    val resizeNSMouseHotspot = parcelableSetting("resizeNSMouseHotspot", CENTER_HOTSPOT)

    val resizeEWMouseHotspot = parcelableSetting("resizeEWMouseHotspot", CENTER_HOTSPOT)

    val resizeAllMouseHotspot = parcelableSetting("resizeAllMouseHotspot", CENTER_HOTSPOT)

    val notAllowedMouseHotspot = parcelableSetting("notAllowedMouseHotspot", CENTER_HOTSPOT)

    val cursorSensitivity = intSetting("cursorSensitivity", 100, 25..300)

    val mouseCaptureSensitivity = intSetting("mouseCaptureSensitivity", 100, 25..300)

    val mouseControlMode = enumSetting("mouseControlMode", MouseControlMode.SLIDE)

    val mouseTouchBlockMode = enumSetting("mouseTouchBlockMode", MouseTouchBlockMode.NONE)

    val mouseLongPressDelay = intSetting("mouseLongPressDelay", 300, 100..1000)

    val enableMouseClick = boolSetting("enableMouseClick", true)

    val gamepadControl = boolSetting("gamepadControl", true)

    val sdlAutoShowIme = boolSetting("sdlAutoShowIme", true)

    val imeMagnifierAutoShow = boolSetting("imeMagnifierAutoShow", true)

    val gamepadInputMode = enumSetting("gamepadInputMode", GamepadInputMode.Mapped)

    val gamepadInputModePrompted = boolSetting("gamepadInputModePrompted", false)

    val gamepadDeadZoneScale = intSetting("gamepadDeadZoneScale", 100, 50..200)

    val gamepadMappingConfig = stringSetting("gamepadMappingConfig", "default")

    val joystickControlMode = enumSetting("joystickControlMode", JoystickMode.LeftMovement)

    val gamepadCursorSensitivity = intSetting("gamepadCursorSensitivity", 100, 25..300)

    val gamepadCameraSensitivity = intSetting("gamepadCameraSensitivity", 100, 25..300)

    val gestureControl = boolSetting("gestureControl", false)

    val gestureTapMouseAction = enumSetting("gestureTapMouseAction", GestureActionType.MOUSE_RIGHT)

    val gestureLongPressMouseAction = enumSetting("gestureLongPressMouseAction", GestureActionType.MOUSE_LEFT)

    val gestureLongPressDelay = intSetting("gestureLongPressDelay", 300, 100..1000)

    val gyroscopeControl = boolSetting("gyroscopeControl", false)

    val gyroscopeSensitivity = intSetting("gyroscopeSensitivity", 100, 25..300)

    val gyroscopeSampleRate = intSetting("gyroscopeSampleRate", 16, 5..50)

    val gyroscopeSmoothing = boolSetting("gyroscopeSmoothing", true)

    val gyroscopeSmoothingWindow = intSetting("gyroscopeSmoothingWindow", 4, 2..10)

    val gyroscopeInvertX = boolSetting("gyroscopeInvertX", false)

    val gyroscopeInvertY = boolSetting("gyroscopeInvertY", false)

    val launcherColorTheme = enumSetting(
        "launcherColorTheme",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorThemeType.DYNAMIC
        else ColorThemeType.EMBERMIRE
    )

    val launcherCustomColor = intSetting("launcherCustomColor", Color.Blue.toArgb())

    val launcherCustomPaletteStyle = enumSetting("launcherCustomPaletteStyle", PaletteStyle.TonalSpot)

    val launcherDarkMode = enumSetting("launcherDarkMode", DarkMode.FollowSystem)

    val launcherLanguage = enumSetting("launcherLanguage", AppLanguage.FOLLOW_SYSTEM)

    val launcherFullScreen = boolSetting("launcherFullScreen", true)

    val launcherFestivalEffects = boolSetting("launcherFestivalEffects", true)

    val launcherSwapAnimEnabled = boolSetting("launcherSwapAnimEnabled", true)

    val launcherAnimateSpeedLevel = intSetting("launcherAnimateSpeedLevel", 10, 1..30)

    val launcherActionMenuSide = enumSetting("launcherActionMenuSide", ActionMenuSide.END)

    val launcherBackgroundOpacity = intSetting("launcherBackgroundOpacity", 80, 20..100)

    val videoBackgroundVolume = intSetting("videoBackgroundVolume", 0, 0..100)

    val backgroundBlur = intSetting("backgroundBlur", 24, 0..40)

    val backgroundBlurType = enumSetting("backgroundBlurType", BackgroundBlur.Foreground)

    val liquidGlass = boolSetting("liquidGlass", true)

    val lastIgnoredVersion = intSetting("lastIgnoredVersion", null)

    val launcherLogRetentionDays = intSetting("launcherLogRetentionDays", 7, 1..14)

    val gameDownloadSource = enumSetting(
        "gameDownloadSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    val addonPlatformSource = enumSetting(

        "assetPlatformSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    val controlLayout = stringSetting("controlLayout", "")

    val currentAccount = stringSetting("currentAccount", "")

    val currentGamePathId = stringSetting("currentGamePathId", GamePathManager.DEFAULT_ID)

    val launcherTaskMenuExpanded = boolSetting("launcherTaskMenuExpanded", true)

    val showFPS = boolSetting("showFPS", true)

    val fpsDisplayMode = enumSetting("fpsDisplayMode", FpsDisplayMode.NUMBER)

    val showMemory = boolSetting("showMemory", false)

    val showMenuBall = boolSetting("showMenuBall", true)

    val menuBallPos = offsetSetting("menuBallPos", Offset.Zero)

    val menuBallOpacity = intSetting("menuBallOpacity", 100, 20..100)

    val hotbarRule = enumSetting("hotbarRule", HotbarRule.Auto)

    val hotbarWidth = intSetting("hotbarWidth", 500, 0..1000)

    val hotbarHeight = intSetting("hotbarHeight", 100, 0..1000)

    val hotbarDoubleClick = boolSetting("hotbarDoubleClick", true)

    val hotbarLongClick = boolSetting("hotbarLongClick", true)

    val hotbarLongClickDelay = intSetting("hotbarLongClickDelay", 300, 100..1000)

    val controlsOpacity = intSetting("controlsOpacity", 100, 0..100)

    val editorEnableWidgetSnap = boolSetting("editorEnableWidgetSnap", true)

    val editorSnapInAllLayers = boolSetting("editorSnapInAllLayers", false)

    val editorWidgetSnapMode = enumSetting("editorWidgetSnapMode", SnapMode.FullScreen)

    val enableTerracotta = boolSetting("enableTerracotta", false)

    val enableTerracottaNodes = boolSetting("enableTerracottaNodes", false)

    val terracottaNodes = stringSetting("terracottaNodes", "")

    val terracottaNoticeVer = intSetting("terracottaNoticeVer", -1)

    val lastUpgradeCheck = longSetting("lastUpgradeCheck", 0L)

    val finishedGame = intSetting("finishedGame", 0)

    val showSponsorship = boolSetting("showSponsorship", true)

    val lastSponsorReportMonth = stringSetting("lastSponsorReportMonth", "")

    val searchModPlatform = enumSetting("searchModPlatform", Platform.MODRINTH)

    val searchModpackPlatform = enumSetting("searchModpackPlatform", Platform.MODRINTH)

    val searchResourcePackPlatform = enumSetting("searchResourcePackPlatform", Platform.MODRINTH)

    val searchShadersPlatform = enumSetting("searchShadersPlatform", Platform.MODRINTH)
}
