package com.cmc.launcher.game.account.wardrobe

enum class SkinType {

    Invalid,

    DualLayer,

    Classic
}

fun SkinType.isValid(): Boolean = this != SkinType.Invalid
