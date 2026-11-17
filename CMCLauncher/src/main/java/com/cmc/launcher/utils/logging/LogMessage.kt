package com.cmc.launcher.utils.logging

data class LogMessage(
    val time: Long,
    val tag: String,
    val level: Level,
    val message: String,
    val throwable: Throwable?
)
