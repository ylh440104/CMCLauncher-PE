package com.cmc.launcher.utils.file

import java.io.File

class FolderFileCounter(
    private val dir: File
) {
    private var counts: Int? = null

    fun checkDir(): Boolean {
        val tempCount = if (dir.isFile) {
            0
        } else {
            dir.list()?.size ?: 0
        }
        val result = getRecordedCount() != tempCount
        counts = tempCount
        return result
    }

    fun getRecordedCount(): Int = counts ?: 0

    fun isUnchecked(): Boolean = counts == null
}
