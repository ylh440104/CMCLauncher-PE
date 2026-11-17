package com.cmc.launcher.filemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun fmBackgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainer
@Composable
fun fmOnBackgroundColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun fmCardColor(): Color = MaterialTheme.colorScheme.surfaceBright
@Composable
fun fmOnCardColor(): Color = MaterialTheme.colorScheme.onSurface

@Composable
fun fmSecondaryTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun fmErrorColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun fmSelectionColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

@Composable
fun fmTopbarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    navigationIconContentColor = fmOnBackgroundColor(),
    titleContentColor = fmOnBackgroundColor(),
    actionIconContentColor = fmOnBackgroundColor(),
    subtitleContentColor = fmOnBackgroundColor(),
)

object FmAnimations {

    const val SWIPE_BACK_MS = 220

    const val LOCATE_HIGHLIGHT_MS = 1400L

    const val FADE_OUT_MS = 120

    const val FADE_IN_MS = 180
}
