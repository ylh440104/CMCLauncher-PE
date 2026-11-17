package com.cmc.launcher.filemanager.logic.task

enum class TaskKind {
    LIST,
    SEARCH,
    COPY,
    MOVE,
    DELETE,
    COMPRESS,
    EXTRACT,
    IMPORT,
    TRASH_RESTORE,
    TRASH_PURGE,
    TRASH_CLEAR;

    val shouldShowProgressDialog: Boolean
        get() = this != LIST && this != SEARCH
}
