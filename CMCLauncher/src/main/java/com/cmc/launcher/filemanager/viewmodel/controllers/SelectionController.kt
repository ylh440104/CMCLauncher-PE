package com.cmc.launcher.filemanager.viewmodel.controllers

import com.cmc.launcher.filemanager.logic.entry.FmEntry
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import com.cmc.launcher.filemanager.viewmodel.entryPathKey

class SelectionController(private val store: FmStateStore) {

    fun enterMultiSelectWith(entry: FmEntry) {
        val key = entryPathKey(entry)
        store.rangeAnchorKey = key
        store.setSelection(store.selection + key, true)
    }

    fun toggleSelection(entry: FmEntry) {
        val key = entryPathKey(entry)
        val newSelection = if (key in store.selection) {
            store.selection - key
        } else {
            store.selection + key
        }
        if (newSelection.isEmpty()) {
            store.rangeAnchorKey = null
            store.setSelection(newSelection, false)
        } else {
            store.setSelection(newSelection, true)
        }
    }

    fun swipeRangeSelect(entry: FmEntry) {
        val list = store.stateValue().visibleEntries
        val swipeKey = entryPathKey(entry)
        val swipeIndex = list.indexOfFirst { entryPathKey(it) == swipeKey }
        if (swipeIndex < 0) return

        if (!store.stateValue().multiSelect) {
            enterMultiSelectWith(entry)
            return
        }

        val anchorIndex = store.rangeAnchorKey?.let { a ->
            list.indexOfFirst { entryPathKey(it) == a }
        } ?: -1
        if (anchorIndex < 0) {

            store.rangeAnchorKey = swipeKey
            store.setSelection(store.selection + swipeKey, true)
            return
        }

        val from = minOf(anchorIndex, swipeIndex)
        val to = maxOf(anchorIndex, swipeIndex)
        val newSelection = store.selection + list.subList(from, to + 1).map { entryPathKey(it) }

        store.rangeAnchorKey = null
        store.setSelection(newSelection, true)
    }

    fun selectAll() {
        store.rangeAnchorKey = null
        store.setSelection(store.stateValue().visibleEntries.map { entryPathKey(it) }.toSet(), true)
    }

    fun clearSelection() {
        store.rangeAnchorKey = null
        store.setSelection(emptySet(), false)
    }
}
