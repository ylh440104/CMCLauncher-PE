package com.cmc.launcher.ui.screens.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import com.cmc.launcher.R
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.kit.CmcCard
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.elements.BaseFileItem
import com.cmc.launcher.ui.screens.content.elements.CreateNewDirDialog
import com.cmc.launcher.ui.screens.navigateTo
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.Radius
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.utils.file.sortWithFileName
import com.cmc.launcher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun NavBackStack<TitledNavKey>.navigateToFileSelector(
    startPath: String,
    selectFile: Boolean,
    saveKey: TitledNavKey,
    onSelected: (path: String) -> Unit
) = this.navigateTo(
    screenKey = NormalNavKey.FileSelector(
        startPath = startPath,
        selectFile = selectFile,
        saveKey = saveKey,
        onSelected = onSelected
    ),
    useClassEquality = true
)

private sealed interface SelectorOperation {
    data object None : SelectorOperation
    data object CreateDir : SelectorOperation
}

@Composable
private fun SelectorOperation(
    operation: SelectorOperation,
    onChange: (SelectorOperation) -> Unit,
    currentPath: String,
    onCreatePath: (File) -> Unit
) {
    when (operation) {
        SelectorOperation.None -> {}
        SelectorOperation.CreateDir -> {
            CreateNewDirDialog(
                onDismissRequest = { onChange(SelectorOperation.None) },
                createDir = { onCreatePath(File(currentPath, it)) }
            )
        }
    }
}

@Composable
fun FileSelectorScreen(
    key: NormalNavKey.FileSelector,
    backScreenViewModel: ScreenBackStackViewModel,
    back: () -> Unit
) {
    var currentPath by remember(key.startPath) { mutableStateOf(key.startPath) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var operation by remember { mutableStateOf<SelectorOperation>(SelectorOperation.None) }

    LaunchedEffect(currentPath, key.selectFile) {
        files = withContext(Dispatchers.IO) {
            val path = File(currentPath)
            path.listFiles()?.toList()
                ?.filter { if (!key.selectFile) it.isDirectory else true }
                ?.sortedWith { o1, o2 -> sortWithFileName(o1, o2) }
                ?: emptyList()
        }
    }

    val scope = rememberCoroutineScope()
    val createDir = { dirName: String ->
        scope.launch(Dispatchers.IO) {
            val newDir = File(currentPath, dirName)
            if (newDir.mkdirs()) {
                withContext(Dispatchers.Main) { currentPath = newDir.absolutePath }
            }
        }
    }

    SelectorOperation(
        operation = operation,
        onChange = { operation = it },
        currentPath = currentPath,
        onCreatePath = { newDir -> createDir(newDir.name) }
    )

    BaseScreen(
        screenKey = key,
        currentKey = backScreenViewModel.mainScreen.currentKey,
        useClassEquality = true
    ) { isVisible ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.screenH)
        ) {
            Spacer(Modifier.height(Space.sm))

            Toolbar(
                backEnabled = currentPath != key.startPath,
                onBack = {
                    File(currentPath).parentFile?.let { currentPath = it.absolutePath }
                },
                onCreateDir = { operation = SelectorOperation.CreateDir },
                onSelect = {
                    key.onSelected(currentPath)
                    back()
                }
            )

            Spacer(Modifier.height(Space.sm))

            PathBar(path = currentPath)

            Spacer(Modifier.height(Space.sm))

            if (files.isNotEmpty()) {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(files.size) { index ->
                        val file = files[index]
                        CmcCard(
                            shape = RoundedCornerShape(Radius.medium),
                            onClick = {
                                if (!key.selectFile && file.isDirectory) {
                                    currentPath = file.absolutePath
                                }
                            }
                        ) {
                            BaseFileItem(file = file)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.files_no_selectable_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    backEnabled: Boolean,
    onBack: () -> Unit,
    onCreateDir: () -> Unit,
    onSelect: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        ToolbarButton(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_arrow_left_rounded,
            text = stringResource(R.string.files_back_to_parent),
            enabled = backEnabled,
            highlighted = false,
            onClick = onBack
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_create_new_folder_filled,
            text = stringResource(R.string.files_create_dir),
            enabled = true,
            highlighted = false,
            onClick = onCreateDir
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_check,
            text = stringResource(R.string.files_select_dir),
            enabled = true,
            highlighted = true,
            onClick = onSelect
        )
    }
}

@Composable
private fun ToolbarButton(
    modifier: Modifier = Modifier,
    icon: Int,
    text: String,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainer
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.medium)
        highlighted -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.medium))
            .clickable(enabled = enabled, onClick = onClick),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(Radius.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PathBar(path: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(Radius.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_outlined),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
