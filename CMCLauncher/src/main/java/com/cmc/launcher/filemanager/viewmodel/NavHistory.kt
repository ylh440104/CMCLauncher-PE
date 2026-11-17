package com.cmc.launcher.filemanager.viewmodel

import java.nio.file.Path

class NavHistory(initialCurrent: Path) {

    private val backStack = ArrayDeque<Path>()
    private val forwardStack = ArrayDeque<Path>()
    private var current: Path = initialCurrent

    val canBack: Boolean get() = backStack.isNotEmpty()
    val canForward: Boolean get() = forwardStack.isNotEmpty()
    val currentPath: Path get() = current

    fun navigate(to: Path) {
        if (to == current) return
        backStack.addLast(current)
        forwardStack.clear()
        current = to
    }

    fun back(): Path? {
        if (backStack.isEmpty()) return null
        val prev = backStack.removeLast()
        forwardStack.addLast(current)
        current = prev
        return prev
    }

    fun forward(): Path? {
        if (forwardStack.isEmpty()) return null
        val next = forwardStack.removeLast()
        backStack.addLast(current)
        current = next
        return next
    }

    fun pruneDeleted(deleted: Path) {
        fun inside(p: Path): Boolean {
            return p == deleted || p.startsWith(deleted)
        }
        backStack.removeAll { inside(it) }
        forwardStack.removeAll { inside(it) }
    }
}
