package com.cmc.launcher.ui.control.input

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnKeyboardClosed(
    onClosed: () -> Unit
) {
    val imeVisible = WindowInsets.isImeVisible
    val localView = LocalView.current
    var lastImeVisible by remember { mutableStateOf(imeVisible) }

    LaunchedEffect(imeVisible) {
        if (lastImeVisible && !imeVisible) {

            delay(100.milliseconds)
            if (localView.hasWindowFocus()) {
                onClosed()
            }
        }
        lastImeVisible = imeVisible
    }
}
