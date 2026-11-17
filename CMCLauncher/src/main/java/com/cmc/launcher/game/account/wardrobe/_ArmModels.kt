package com.cmc.launcher.game.account.wardrobe

import com.movtery.petlib.ArmModel

fun SkinModelType.toArmModel(): ArmModel? = when (this) {
    SkinModelType.STEVE -> ArmModel.Classic
    SkinModelType.ALEX -> ArmModel.Slim
    SkinModelType.NONE -> null
}
