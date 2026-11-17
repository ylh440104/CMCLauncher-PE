package com.cmc.launcher.game.input

import android.view.KeyEvent

interface CharacterSenderStrategy {

    fun sendBackspace()

    fun sendEnter()

    fun sendTab()

    fun sendLeft()

    fun sendRight()

    fun sendUp()

    fun sendDown()

    fun sendChar(character: Char)

    fun sendOther(key: KeyEvent)

    fun sendCopy()

    fun sendCut()

    fun sendPaste()

    fun sendSelectAll()

    fun sendModifierShift(press: Boolean)

    fun sendModifierCtrl(press: Boolean)
}
