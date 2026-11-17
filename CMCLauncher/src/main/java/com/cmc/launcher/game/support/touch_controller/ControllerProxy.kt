package com.cmc.launcher.game.support.touch_controller

import android.content.Context
import android.os.Vibrator
import android.system.Os
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.bridge.LoggerBridge
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient
import top.fifthlight.touchcontroller.proxy.client.PlatformCapability
import top.fifthlight.touchcontroller.proxy.client.android.transport.UnixSocketTransport

private const val TAG = "ControllerProxy"

object ControllerProxy {
    private val _proxyClient = MutableStateFlow<LauncherProxyClient?>(null)
    val proxyClient = _proxyClient.asStateFlow()

    fun startProxy(
        context: Context,
        vibrateDuration: Int?,
        vibrateKind: VibrationHandler.VibrateKind?,
    ) {
        if (proxyClient.value == null) {
            try {
                val transport = UnixSocketTransport(BuildKeys.LAUNCHER_NAME)
                Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", BuildKeys.LAUNCHER_NAME, true)
                val client = LauncherProxyClient(
                    transport = transport,
                    capabilities = setOf(PlatformCapability.TEXT_STATUS),
                )
                val vibrator = context.getSystemService(Vibrator::class.java)
                val handler = VibrationHandler(vibrator, vibrateDuration, vibrateKind)
                client.vibrationHandler = handler
                client.run()
                LoggerBridge.append("TouchController: TouchController Proxy Client has been created!")
                _proxyClient.value = client
            } catch (ex: Throwable) {
                Logger.warning(TAG, "TouchController proxy client create failed", ex)
            }
        }
    }
}
