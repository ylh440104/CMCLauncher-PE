package com.cmc.launcher.filemanager.logic

enum class FmFilenameError {

    ILLEGAL_CHARACTERS,

    INVALID_LENGTH,

    LEADING_OR_TRAILING_SPACE,

    NAME_CONFLICT
}

class FmFilenameException(
    message: String,
    val type: FmFilenameError,
    val invalidLength: Int = -1,
    val illegalCharacters: String? = null
) : Exception(message)
