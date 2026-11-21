package com.cmc.launcher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.cmc.launcher.R
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.kit.CmcCard
import com.cmc.launcher.ui.kit.CmcSection
import com.cmc.launcher.ui.screens.NestedNavDisplay
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.settings.JavaManageScreen
import com.cmc.launcher.ui.screens.content.settings.MirrorSettingScreen
import com.cmc.launcher.ui.screens.navigateOnce
import com.cmc.launcher.ui.screens.onBack
import com.cmc.launcher.ui.screens.rememberTransitionSpec
import com.cmc.launcher.ui.theme.Radius
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.viewmodel.ErrorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.ScreenBackStackViewModel

private data class SettingsEntry(
    val key: TitledNavKey,
    val title: String,
    val caption: String,
    val icon: Int
)

@Composable
fun SettingsScreen(
    key: NestedNavKey.Settings,
    backStackViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (key.backStack.size <= 1) {
                SettingsHome(
                    onOpen = { target -> key.backStack.navigateOnce(target) }
                )
            }
            NavigationUI(
                key = key,
                mainScreenKey = backStackViewModel.mainScreen.currentKey,
                settingsScreenKey = backStackViewModel.settingsScreen.currentKey,
                onCurrentKeyChange = { newKey ->
                    backStackViewModel.settingsScreen.currentKey = newKey
                },
                eventViewModel = eventViewModel,
                submitError = submitError,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsHome(onOpen: (TitledNavKey) -> Unit) {
    val entries = listOf(
        SettingsEntry(
            key = NormalNavKey.Settings.Mirror,
            title = "\u5916\u89c2\u4e0e\u4e0b\u8f7d",
            caption = "\u914d\u8272\u3001\u80cc\u666f\u3001\u4e0b\u8f7d\u7ebf\u8def",
            icon = R.drawable.ic_format_paint_outlined
        ),
        SettingsEntry(
            key = NormalNavKey.Settings.JavaManager,
            title = stringResource(R.string.settings_tab_java_manage),
            caption = "\u7ba1\u7406 Java \u8fd0\u884c\u65f6",
            icon = R.drawable.ic_java
        )
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screenH,
            end = Space.screenH,
            top = Space.sm,
            bottom = Space.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item {
            Column(modifier = Modifier.padding(start = Space.xs, top = Space.xs, bottom = Space.xs)) {
                Text(
                    text = stringResource(R.string.generic_setting),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "\u8c03\u6574\u542f\u52a8\u5668\u7684\u5916\u89c2\u4e0e\u8fd0\u884c\u73af\u5883",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { CmcSection(title = "\u8bbe\u7f6e", caption = "\u5171 ${entries.size} \u9879") }
        items(entries.size) { index ->
            val entry = entries[index]
            CmcCard(
                shape = RoundedCornerShape(Radius.medium),
                onClick = { onOpen(entry.key) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(entry.icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationUI(
    key: NestedNavKey.Settings,
    mainScreenKey: TitledNavKey?,
    settingsScreenKey: TitledNavKey?,
    onCurrentKeyChange: (TitledNavKey?) -> Unit,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = key.backStack
    val stackTopKey = backStack.lastOrNull()
    LaunchedEffect(stackTopKey) {
        onCurrentKeyChange(stackTopKey)
    }

    if (backStack.size > 1) {
        NestedNavDisplay(
            ownerKey = key,
            backStack = backStack,
            modifier = modifier,
            onBack = { onBack(backStack) },
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<NormalNavKey.Settings.JavaManager> {
                    JavaManageScreen(key, settingsScreenKey, mainScreenKey, eventViewModel, submitError)
                }
                entry<NormalNavKey.Settings.Mirror> {
                    MirrorSettingScreen(
                        key = key,
                        settingsScreenKey = settingsScreenKey,
                        mainScreenKey = mainScreenKey
                    )
                }
            }
        )
    }
}
