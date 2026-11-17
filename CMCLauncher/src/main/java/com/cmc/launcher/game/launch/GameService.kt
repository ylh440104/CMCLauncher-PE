package com.cmc.launcher.game.launch

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cmc.launcher.R
import com.cmc.launcher.notification.NOTIFICATION_ID_GAME_SERVICE
import com.cmc.launcher.notification.NotificationChannelData
import com.cmc.launcher.utils.logging.Logger

private const val TAG = "GameService"

class GameService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForegroundNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val data = NotificationChannelData.GAME_SERVICE_CHANNEL

        val notification: Notification = NotificationCompat.Builder(this, data.channelId)
            .setContentTitle(getString(R.string.notification_jvm_running_name))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_GAME_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_GAME_SERVICE, notification)
            }
        } catch (e: Exception) {

            Logger.error(TAG, "Failed to start foreground notification", e)
        }
    }
}
