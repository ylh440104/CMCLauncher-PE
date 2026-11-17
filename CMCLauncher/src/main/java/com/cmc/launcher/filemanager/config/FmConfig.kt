package com.cmc.launcher.filemanager.config

import com.tencent.mmkv.MMKV

private const val KEY_SHOW_HIDDEN = "show_hidden"
private const val KEY_SORT_FIELD = "sort_field"
private const val KEY_SORT_ASC = "sort_ascending"
private const val KEY_FOLDER_FIRST = "folder_first"
private const val KEY_TRASH_SORT_FIELD = "trash_sort_field"
private const val KEY_TRASH_SORT_ASC = "trash_sort_ascending"
private const val KEY_TRASH_FOLDER_FIRST = "trash_folder_first"
private const val KEY_EDITOR_WORDWRAP = "editor_wordwrap"
private const val KEY_EDITOR_COMPLETION = "editor_completion"
private const val KEY_EDITOR_LINE_NUMBER = "editor_line_number"
private const val KEY_EDITOR_HIGHLIGHT_LINE = "editor_highlight_line"
private const val KEY_EDITOR_NON_PRINTABLE = "editor_non_printable"
private const val KEY_EDITOR_FONT_SIZE = "editor_font_size"
private const val KEY_EDITOR_SEARCH_MATCH_CASE = "editor_search_match_case"
private const val KEY_EDITOR_SEARCH_WHOLE_WORD = "editor_search_whole_word"
private const val KEY_EDITOR_SEARCH_REGEX = "editor_search_regex"

object FmConfig {
    private const val MMKV_ID = "cmc_file_manager"

    private fun mmkv(): MMKV = MMKV.mmkvWithID(MMKV_ID, MMKV.SINGLE_PROCESS_MODE)

    fun setShowHidden(value: Boolean) {
        mmkv().putBoolean(KEY_SHOW_HIDDEN, value)
    }

    fun showHidden(): Boolean = mmkv().decodeBool(KEY_SHOW_HIDDEN, true)

    fun setSortField(value: String) {
        mmkv().putString(KEY_SORT_FIELD, value)
    }

    fun sortField(): String = mmkv().decodeString(KEY_SORT_FIELD) ?: SortField.NAME.name

    fun setSortAscending(value: Boolean) {
        mmkv().putBoolean(KEY_SORT_ASC, value)
    }

    fun sortAscending(): Boolean = mmkv().decodeBool(KEY_SORT_ASC, true)

    fun setFolderFirst(value: Boolean) {
        mmkv().putBoolean(KEY_FOLDER_FIRST, value)
    }

    fun folderFirst(): Boolean = mmkv().decodeBool(KEY_FOLDER_FIRST, true)

    fun setTrashSortField(value: String) {
        mmkv().putString(KEY_TRASH_SORT_FIELD, value)
    }

    fun trashSortField(): String = mmkv().decodeString(KEY_TRASH_SORT_FIELD) ?: TrashSortField.DELETED.name

    fun setTrashSortAscending(value: Boolean) {
        mmkv().putBoolean(KEY_TRASH_SORT_ASC, value)
    }

    fun trashSortAscending(): Boolean = mmkv().decodeBool(KEY_TRASH_SORT_ASC, false)

    fun setTrashFolderFirst(value: Boolean) {
        mmkv().putBoolean(KEY_TRASH_FOLDER_FIRST, value)
    }

    fun trashFolderFirst(): Boolean = mmkv().decodeBool(KEY_TRASH_FOLDER_FIRST, true)

    fun setEditorWordwrap(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_WORDWRAP, value)
    }

    fun editorWordwrap(): Boolean = mmkv().decodeBool(KEY_EDITOR_WORDWRAP, true)

    fun setEditorCompletionEnabled(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_COMPLETION, value)
    }

    fun editorCompletionEnabled(): Boolean = mmkv().decodeBool(KEY_EDITOR_COMPLETION, true)

    fun setEditorLineNumber(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_LINE_NUMBER, value)
    }

    fun editorLineNumber(): Boolean = mmkv().decodeBool(KEY_EDITOR_LINE_NUMBER, true)

    fun setEditorHighlightLine(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_HIGHLIGHT_LINE, value)
    }

    fun editorHighlightLine(): Boolean = mmkv().decodeBool(KEY_EDITOR_HIGHLIGHT_LINE, true)

    fun setEditorNonPrintable(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_NON_PRINTABLE, value)
    }

    fun editorNonPrintable(): Boolean = mmkv().decodeBool(KEY_EDITOR_NON_PRINTABLE, false)

    fun setEditorFontSize(value: Float) {
        mmkv().putFloat(KEY_EDITOR_FONT_SIZE, value)
    }

    fun editorFontSize(): Float = mmkv().decodeFloat(KEY_EDITOR_FONT_SIZE, 0f)

    fun setEditorSearchMatchCase(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_MATCH_CASE, value)
    }

    fun editorSearchMatchCase(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_MATCH_CASE, false)

    fun setEditorSearchWholeWord(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_WHOLE_WORD, value)
    }

    fun editorSearchWholeWord(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_WHOLE_WORD, false)

    fun setEditorSearchRegex(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_REGEX, value)
    }

    fun editorSearchRegex(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_REGEX, false)

    enum class SortField { NAME, SIZE, MODIFIED }

    enum class TrashSortField { NAME, DELETED }
}
