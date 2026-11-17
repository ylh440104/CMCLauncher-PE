package com.cmc.launcher.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cmc.launcher.R
import com.cmc.launcher.notification.NotificationManager

@Composable
fun NotificationCheck(
    title: String = stringResource(R.string.notification_title),
    text: String,
    onGranted: () -> Unit = {},
    onIgnore: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onIgnore()
            }
        }

    SimpleAlertDialog(
        title = title,
        text = { Text(text = text) },
        confirmText = stringResource(R.string.notification_request),
        dismissText = stringResource(R.string.generic_ignore),
        onConfirm = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {

                NotificationManager.openNotificationSettings(context)
                onDismiss()
            } else {

                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onCancel = {
            onIgnore()
        },
        onDismissRequest = {
            onDismiss()
        }
    )
}
