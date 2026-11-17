package com.cmc.launcher.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationManager {

    fun initManager(context: Context) {
        NotificationChannelData.entries.forEach { data ->
            createNotificationChannel(context, data)
        }
    }

    fun checkNotificationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {

            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_DENIED
        }
    }

    fun createNotificationChannel(context: Context, channelData: NotificationChannelData) {
        val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelData.channelId, channelData.channelName(context), channelData.level).apply {
            channelData.channelDescription?.invoke(context)?.let { desc ->
                description = desc
            }
            setShowBadge(channelData.showBadge)
        }
        manager.createNotificationChannel(channel)
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
            context.startActivity(intent)
        } catch (e: Exception) {

            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        }
    }
}
