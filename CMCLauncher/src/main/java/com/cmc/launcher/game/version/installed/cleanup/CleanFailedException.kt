package com.cmc.launcher.game.version.installed.cleanup

import java.io.File
import java.io.IOException

class CleanFailedException(
    val files: List<File>
) : IOException()
