package com.cmc.launcher.game.version.download

import com.cmc.launcher.game.versioninfo.models.GameManifest

data class LibraryReplacement(
    val newName: String,
    val newPath: String,
    val newSha1: String,
    val newUrl: String,

    val newSize: Long = -1L
)

fun getLibraryReplacement(libraryName: String, versionParts: List<String>): LibraryReplacement? {
    val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0

    return when {
        libraryName.startsWith("net.java.dev.jna:jna:") -> {

            if (major >= 5 && minor >= 13) null
            else LibraryReplacement(
                newName = "net.java.dev.jna:jna:5.13.0",
                newPath = "net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar",
                newSha1 = "1200e7ebeedbe0d10062093f32925a912020e747",
                newUrl = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar",
                newSize = 1879325L
            )
        }
        libraryName.startsWith("com.github.oshi:oshi-core:") -> {

            if (major != 6 || minor != 2) null
            else LibraryReplacement(
                newName = "com.github.oshi:oshi-core:6.3.0",
                newPath = "com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar",
                newSha1 = "9e98cf55be371cafdb9c70c35d04ec2a8c2b42ac",
                newUrl = "https://repo1.maven.org/maven2/com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar",
                newSize = 957945L
            )
        }
        libraryName.startsWith("org.ow2.asm:asm-all:") -> {

            if (major >= 5) null
            else LibraryReplacement(
                newName = "org.ow2.asm:asm-all:5.0.4",
                newPath = "org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar",
                newSha1 = "e6244859997b3d4237a552669279780876228909",
                newUrl = "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar",
                newSize = 241810L
            )
        }
        else -> null
    }
}

fun GameManifest.Library.filterLibrary(): Boolean {
    return when {
        name?.contains("org.lwjgl") == true -> true
        name?.contains("jinput-platform") == true -> true
        name?.contains("twitch-platform") == true -> true
        else -> false
    }
}
