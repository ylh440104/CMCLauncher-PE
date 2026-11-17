package com.cmc.launcher.game.download.modpack.install

class PackNotSupportedException(
    val reason: UnsupportedPackReason
) : RuntimeException(
    reason.reasonText
)

enum class UnsupportedPackReason(
    val reasonText: String
) {

    CorruptedArchive("The archive is corrupted or failed to extract."),

    UnsupportedFormat("The modpack format is not supported.")
}
