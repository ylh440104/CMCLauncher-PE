package com.cmc.launcher.filemanager.logic

import com.cmc.launcher.utils.file.InvalidFilenameException
import com.cmc.launcher.utils.file.checkFilenameValidity

object FilenameValidator {

    @Throws(FmFilenameException::class)
    fun check(name: String) {
        try {
            checkFilenameValidity(name)
        } catch (e: InvalidFilenameException) {
            throw FmFilenameException(
                message = e.message ?: "Invalid filename",
                type = when {
                    e.isLeadingOrTrailingSpace -> FmFilenameError.LEADING_OR_TRAILING_SPACE
                    e.isInvalidLength -> FmFilenameError.INVALID_LENGTH
                    else -> FmFilenameError.ILLEGAL_CHARACTERS
                },
                invalidLength = if (e.isInvalidLength) e.invalidLength else -1,
                illegalCharacters = if (e.containsIllegalCharacters()) e.illegalCharacters else null
            )
        }
    }

    fun verify(name: String): FmFilenameException? {
        return try {
            check(name)
            null
        } catch (e: FmFilenameException) {
            e
        }
    }
}
