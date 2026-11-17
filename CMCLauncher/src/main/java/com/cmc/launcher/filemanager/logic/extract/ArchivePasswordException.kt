package com.cmc.launcher.filemanager.logic.extract

class ArchivePasswordException(
    val type: Type,
    message: String = type.message,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Type(val message: String) {

        REQUIRED("Archive requires a password"),

        WRONG("Archive password is incorrect"),
    }
}
