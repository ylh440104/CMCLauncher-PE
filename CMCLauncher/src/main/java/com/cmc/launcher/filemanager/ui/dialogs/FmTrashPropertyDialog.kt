package com.cmc.launcher.filemanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cmc.launcher.R
import com.cmc.launcher.filemanager.ui.components.PropertyRow
import com.cmc.launcher.filemanager.viewmodel.DirScanUiState
import com.cmc.launcher.ui.components.SimpleAlertDialog
import com.cmc.launcher.utils.file.formatFileSize

@Composable
fun FmTrashPropertyDialog(
    name: String,
    sourcePath: String,
    isDirectory: Boolean,
    sizeText: String,
    deletedText: String,
    dirScan: DirScanUiState? = null,
    onDismiss: () -> Unit
) {
    val dirStats = dirScan?.stats

    SimpleAlertDialog(
        title = stringResource(R.string.fm_property),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                PropertyRow(R.string.fm_new_name, name)
                PropertyRow(R.string.fm_property_source_path, sourcePath)
                PropertyRow(
                    R.string.fm_property_type,
                    stringResource(if (isDirectory) R.string.resource_pack_manage_type_folder else R.string.fm_new_file)
                )
                if (isDirectory) {
                    val scanning = dirScan?.running == true
                    PropertyRow(
                        R.string.fm_property_size,
                        if (dirStats != null) {
                            formatFileSize(dirStats.totalSize)
                        } else if (scanning) {
                            stringResource(R.string.fm_calculating)
                        } else sizeText
                    )
                    PropertyRow(
                        R.string.fm_property_files,
                        dirStats?.fileCount?.toString() ?: if (scanning) stringResource(R.string.fm_calculating) else "-"
                    )
                    PropertyRow(
                        R.string.fm_property_folders,
                        dirStats?.dirCount?.toString() ?: if (scanning) stringResource(R.string.fm_calculating) else "-"
                    )
                    if (scanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                } else {
                    PropertyRow(R.string.fm_property_size, sizeText)
                }
                PropertyRow(R.string.fm_property_deleted_at, deletedText)
            }
        },
        confirmText = stringResource(R.string.generic_close),
        onDismiss = onDismiss
    )
}
