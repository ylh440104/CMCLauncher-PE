package com.movtery.petlib

enum class ArmModel(internal val code: Int) {
    Classic(0),
    Slim(1);

    internal companion object {

        fun codeOf(value: ArmModel?): Int = value?.code ?: -1
    }
}
