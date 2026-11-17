package com.cmc.launcher.filemanager.logic.editor

const val MAX_EDIT_SIZE: Long = 20L * 1024 * 1024

private val KNOWN_TEXT_EXTENSIONS = setOf(

    "txt", "text", "md", "markdown", "log", "rtf",

    "json", "yaml", "yml", "toml", "xml", "html", "htm", "css",

    "ini", "cfg", "conf", "properties", "env", "editorconfig",

    "js", "mjs", "cjs", "ts", "jsx", "tsx", "kt", "kts", "java",
    "c", "h", "cpp", "hpp", "cc", "cxx", "cs", "py", "sh", "bash",
    "zsh", "bat", "cmd", "ps1", "sql", "gradle", "groovy", "rb",
    "go", "rs", "php", "swift", "scala", "lua", "pl", "r",

    "csv", "tsv", "diff", "patch", "gitignore", "gitattributes"
)

fun isKnownTextFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext.isNotEmpty() && ext in KNOWN_TEXT_EXTENSIONS
}
