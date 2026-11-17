package com.cmc.launcher.filemanager.viewmodel.controllers

import com.cmc.launcher.R
import com.cmc.launcher.filemanager.config.FmConfig
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.SearchResult
import com.cmc.launcher.filemanager.logic.task.RunResult
import com.cmc.launcher.filemanager.logic.task.TaskKind
import com.cmc.launcher.filemanager.logic.task.TaskManager
import com.cmc.launcher.filemanager.viewmodel.DialogIntent
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import com.cmc.launcher.filemanager.viewmodel.SearchHitView
import com.cmc.launcher.filemanager.viewmodel.SearchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchController(
    private val logic: FileManagerLogic,
    private val taskManager: TaskManager,
    private val store: FmStateStore,
    private val coroutineScope: CoroutineScope
) {

    fun showSearchDialog() {
        store.dismissDialog()
        val hasResult = store.searchUiValue().lastKeyword.isNotBlank()
        store.updateState {
            it.copy(dialogIntent = if (hasResult) DialogIntent.SearchResult else DialogIntent.Search)
        }
    }

    fun submitSearch(keyword: String, caseSensitive: Boolean) {
        if (keyword.isBlank()) return
        val startDir = store.history.currentPath
        store.setSearchUi(
            SearchUiState(
                running = true,
                currentDir = startDir,
                hits = emptyList(),
                lastKeyword = keyword
            )
        )
        store.updateState {
            it.copy(dialogIntent = DialogIntent.SearchTask)
        }

        coroutineScope.launch(Dispatchers.IO) {
            val result = taskManager.run(TaskKind.SEARCH) {
                logic.search(startDir, keyword, caseSensitive) { dir ->
                    report(currentName = dir.toString())
                    store.updateSearchUi { it.copy(currentDir = dir) }
                }
            }
            when (result) {
                is RunResult.Ok -> {
                    when (val search = result.value) {
                        is SearchResult.Ok -> {

                            val showHidden = FmConfig.showHidden()
                            val hits = search.hits
                                .filterNot { !showHidden && it.hidden }
                                .map { SearchHitView(it.path, it.name, it.isDirectory, it.size) }

                            store.setSearchUi(SearchUiState(running = false, currentDir = null, hits = hits, lastKeyword = keyword))
                            store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                        }
                        is SearchResult.Failed -> {
                            store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                            store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_search_failed)))
                            store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                        }
                    }
                }
                is RunResult.Failed -> {
                    store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_search_failed)))
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
                RunResult.Cancelled -> {
                    store.updateSearchUi { it.copy(running = false, currentDir = null) }
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
                is RunResult.Rejected -> {
                    store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
            }
        }
    }

    fun clearSearch() {
        store.setSearchUi(SearchUiState())
        store.updateState {
            it.copy(dialogIntent = DialogIntent.Search)
        }
    }

    fun backToSearchSetup() {
        store.updateState {
            it.copy(dialogIntent = DialogIntent.Search)
        }
    }
}
