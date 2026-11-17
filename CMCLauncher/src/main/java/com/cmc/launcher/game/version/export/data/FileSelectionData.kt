package com.cmc.launcher.game.version.export.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

class FileSelectionData(
    val file: File,
    val alias: Int? = null,
    val child: List<FileSelectionData>? = null
): Comparable<FileSelectionData> {
    private val _selected = MutableStateFlow(Selected.Unselected)

    val selected = _selected.asStateFlow()

    private val _expand = MutableStateFlow(false)

    val expand = _expand.asStateFlow()

    private var _cachedSelected: Int = 0
    private var _cachedTotal: Int = 0

    fun updateSelectState(new: Selected) {
        if (new == Selected.Indeterminate) {
            error("File node cannot be set to \"Indeterminate\" selection state")
        }

        if (child != null && child.isEmpty()) {

            iterativeSelect(Selected.Unselected)
        } else {
            iterativeSelect(new)
        }
    }

    private fun iterativeSelect(new: Selected) {
        val stack = ArrayDeque<FileSelectionData>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node._selected.update { new }

            val children = node.child ?: continue

            for (childNode in children) {
                if (childNode.child != null && childNode.child.isEmpty()) {

                    childNode._selected.update { Selected.Unselected }
                } else {
                    stack.add(childNode)
                }
            }
        }
    }

    fun expandDirs(state: Boolean) {

        _expand.update { state }

        if (!state && child != null) {
            val stack = ArrayDeque<FileSelectionData>()
            child.let { stack.addAll(it) }

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()

                node._expand.update { false }
                node.child?.let { children ->
                    stack.addAll(children)
                }
            }
        }
    }

    override fun compareTo(other: FileSelectionData): Int {
        val thisIsFile = isFile()
        val otherIsFile = other.isFile()

        return when {
            thisIsFile != otherIsFile -> {
                if (!thisIsFile) -1 else 1
            }
            else -> {
                val nameCompare = file.name.compareTo(other.file.name)
                if (nameCompare != 0) {
                    nameCompare
                } else {

                    file.absolutePath.compareTo(other.file.absolutePath)
                }
            }
        }
    }

    companion object {

        suspend fun refreshTreeSelect(
            list: List<FileSelectionData>
        ): Int {
            return withContext(Dispatchers.Default) {
                ensureActive()

                var selectedFiles = 0
                val stack = ArrayDeque<Pair<FileSelectionData, Boolean>>()

                list.forEach { stack.add(it to false) }

                while (stack.isNotEmpty()) {
                    ensureActive()
                    val (node, visited) = stack.removeLast()

                    if (!visited) {
                        stack.add(node to true)
                        node.child?.forEach {
                            stack.add(it to false)
                        }
                    } else {
                        val children = node.child

                        if (children == null) {

                            if (node._selected.value == Selected.Selected) {
                                selectedFiles++
                                node._cachedSelected = 1
                            } else {
                                node._cachedSelected = 0
                            }
                            node._cachedTotal = 1
                        } else {
                            var total = 0
                            var selected = 0

                            for (child in children) {
                                total += child._cachedTotal
                                selected += child._cachedSelected
                            }

                            node._cachedTotal = total
                            node._cachedSelected = selected

                            node._selected.update {
                                when {
                                    total == 0 -> Selected.Unselected
                                    selected <= 0 -> Selected.Unselected
                                    selected < total -> Selected.Indeterminate
                                    else -> Selected.Selected
                                }
                            }

                            selectedFiles += selected
                        }
                    }
                }

                selectedFiles
            }
        }
    }
}

fun FileSelectionData.isFile(): Boolean = child == null

fun List<FileSelectionData>.getSelectedFiles(): List<File> {
    return asSequence()
        .flatMap { node ->
            when {
                node.child == null && node.selected.value == Selected.Selected -> sequenceOf(node.file)
                !node.child.isNullOrEmpty() -> node.child.getSelectedFiles().asSequence()
                else -> emptySequence()
            }
        }
        .toList()
}
