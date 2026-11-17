package com.cmc.launcher.utils.festival

import androidx.compose.runtime.compositionLocalOf

val LocalFestivals = compositionLocalOf<List<Festival>> {
    error("No Festivals provided")
}
