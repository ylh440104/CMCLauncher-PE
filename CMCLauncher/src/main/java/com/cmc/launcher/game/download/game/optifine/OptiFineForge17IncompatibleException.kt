package com.cmc.launcher.game.download.game.optifine

class OptiFineForge17IncompatibleException(

    val buildof: String
) : RuntimeException(
    "OptiFine (build $buildof) is incompatible with Forge 1.17+, " +
            "OptiFine H1 Pre2 (20210924-190833) or newer is required"
)
