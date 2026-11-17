package com.cmc.launcher.filemanager.ui

import androidx.navigation3.runtime.NavBackStack
import java.nio.file.Path

fun NavBackStack<FmNavKey>.openTrash() {
    if (lastOrNull() == FmNavKey.Trash) return
    add(FmNavKey.Trash)
}

fun NavBackStack<FmNavKey>.closeTrash() {
    if (lastOrNull() == FmNavKey.Trash) {
        removeLastOrNull()
    }
}

fun NavBackStack<FmNavKey>.openEditor(path: Path) {
    if (lastOrNull() is FmNavKey.Editor) return
    add(FmNavKey.Editor(path.toString()))
}

fun NavBackStack<FmNavKey>.closeEditor() {
    if (lastOrNull() is FmNavKey.Editor) {
        removeLastOrNull()
    }
}
