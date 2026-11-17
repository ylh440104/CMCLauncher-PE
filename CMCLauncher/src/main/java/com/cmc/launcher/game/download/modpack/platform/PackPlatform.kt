package com.cmc.launcher.game.download.modpack.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.platform.Platform

enum class PackPlatform(val identifier: String) {
    CurseForge(Platform.CURSEFORGE.displayName) {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_curseforge)
        }
    },
    Modrinth(Platform.MODRINTH.displayName) {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_modrinth)
        }
    },
    MultiMC("MultiMC") {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_multimc)
        }
    },
    MCBBS("MCBBS") {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_chest)
        }
    };

    @Composable
    open fun getText(): String = this.identifier

    @Composable
    abstract fun getIcon(): Painter
}
