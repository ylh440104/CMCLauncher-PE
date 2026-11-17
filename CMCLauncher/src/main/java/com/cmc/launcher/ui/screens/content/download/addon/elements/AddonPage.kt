package com.cmc.launcher.ui.screens.content.download.addon.elements

import com.cmc.launcher.game.download.addon.platform.PlatformSearchData
import org.jackhuang.hmcl.addon.AddonProjectInfo

data class AddonPage(
    val pageNumber: Int,
    val pageIndex: Int,
    val totalPage: Int,
    val isLastPage: Boolean,
    val data: List<Pair<PlatformSearchData, AddonProjectInfo?>>
)
