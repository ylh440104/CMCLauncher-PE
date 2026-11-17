package com.cmc.launcher.ui.screens.main.crashlogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cmc.launcher.R
import com.cmc.launcher.crashlogs.LinkNotFoundException
import com.cmc.launcher.ui.components.SimpleAlertDialog
import com.cmc.launcher.utils.string.getMessageOrToString

sealed interface ShareLinkOperation {
    data object None : ShareLinkOperation

    data object Tip : ShareLinkOperation

    data class Uploading(val apiRoot: String) : ShareLinkOperation

    data class Error(val error: Throwable) : ShareLinkOperation
}

@Composable
fun ShareLinkOperation(
    operation: ShareLinkOperation,
    onChange: (ShareLinkOperation) -> Unit,
    onUpload: () -> Unit,
    onUploadChancel: () -> Unit
) {
    when (operation) {
        is ShareLinkOperation.None -> {}
        is ShareLinkOperation.Tip -> {
            SimpleAlertDialog(
                title = stringResource(R.string.crash_link_share_button),
                text = stringResource(R.string.crash_link_share_tip),
                dismissByDialog = false,
                onConfirm = onUpload,
                onDismiss = {
                    onChange(ShareLinkOperation.None)
                }
            )
        }
        is ShareLinkOperation.Uploading -> {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        text = stringResource(R.string.crash_link_share_button),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.crash_link_share_uploading, operation.apiRoot)
                        )
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = onUploadChancel
                    ) {
                        Text(text = stringResource(R.string.generic_cancel))
                    }
                }
            )
        }
        is ShareLinkOperation.Error -> {
            SimpleAlertDialog(
                title = stringResource(R.string.crash_link_share_failed),
                text = when (val error = operation.error) {
                    is LinkNotFoundException -> {
                        stringResource(R.string.crash_link_share_failed_link_not_found)
                    }
                    else -> {
                        error.getMessageOrToString()
                    }
                },
                dismissByDialog = false,
                onDismiss = {
                    onChange(ShareLinkOperation.None)
                }
            )
        }
    }
}
