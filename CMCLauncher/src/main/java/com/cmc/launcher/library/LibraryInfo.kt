package com.cmc.launcher.library

data class LibraryInfo(
    val name: String,
    val copyrightInfo: String?,
    val license: License,
    val webUrl: String
)
