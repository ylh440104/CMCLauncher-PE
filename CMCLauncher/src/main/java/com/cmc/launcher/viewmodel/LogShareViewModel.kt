package com.cmc.launcher.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

class LogShareViewModel : ViewModel() {

    var currentLogFile by mutableStateOf<File?>(null)
        private set

    var showMenu by mutableStateOf(false)
        private set

    fun openMenu(logFile: File) {
        currentLogFile = logFile
        showMenu = true
    }

    fun closeMenu() {
        showMenu = false
    }

    fun reset() {
        showMenu = false
        currentLogFile = null
    }
}
