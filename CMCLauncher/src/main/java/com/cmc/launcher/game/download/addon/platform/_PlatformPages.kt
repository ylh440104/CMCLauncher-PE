package com.cmc.launcher.game.download.addon.platform

import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonPage

fun previousPage(
    pageNumber: Int,
    pages: List<AddonPage?>,
    index: Int,
    limit: Int,
    onSuccess: (AddonPage) -> Unit = {},
    onSearch: (index: Int) -> Unit = {}
) {
    val targetIndex = pageNumber - 2
    val previousPage = pages.getOrNull(targetIndex)
    if (previousPage != null) {
        onSuccess(previousPage)
    } else {

        onSearch((index - limit).coerceAtLeast(0))
    }
}

fun nextPage(
    pageNumber: Int,
    isLastPage: Boolean,
    pages: List<AddonPage?>,
    index: Int,
    limit: Int,
    onSuccess: (AddonPage) -> Unit = {},
    onSearch: (index: Int) -> Unit = {}
) {
    if (!isLastPage) {
        val nextIndex = pageNumber

        val nextPage = pages.getOrNull(nextIndex)
        if (nextPage != null) {
            onSuccess(nextPage)
        } else {

            onSearch(index + limit)
        }
    }
}

fun navigatePage(
    pageNumber: Int,
    pages: List<AddonPage?>,
    limit: Int,
    onSuccess: (AddonPage) -> Unit = {},
    onSearch: (index: Int) -> Unit = {}
) {
    val targetNumber = pageNumber - 1

    val targetPage = pages.getOrNull(targetNumber)
    if (targetPage != null) {
        onSuccess(targetPage)
    } else {

        onSearch(targetNumber * limit)
    }
}
