package com.cmc.launcher.game.keycodes

import com.movtery.inputmap.keycodes.Lwjgl2Keycode
import com.movtery.inputmap.keycodes.MinecraftKeyBindingMapper
import com.cmc.launcher.game.launch.MCOptions

fun mapToKeycode(bindingKey: String?, defaultValue: String): Int? {
    val binding = bindingKey?.let { MCOptions.get(it) } ?: defaultValue

    return if (binding.startsWith("key.")) {

        MinecraftKeyBindingMapper.getGlfwKeycode(binding)?.toInt()
    } else {
        binding.toIntOrNull()?.let { lwjgl2Code ->

            Lwjgl2Keycode.lwjgl2ToGlfw(lwjgl2Code)
        }
    }
}

fun mapToControlEvent(bindingKey: String?, defaultValue: String): String? {
    val binding = bindingKey?.let { MCOptions.get(it) } ?: defaultValue

    return if (binding.startsWith("key.")) {
        MinecraftKeyBindingMapper.getControlEvent(binding)
    } else {
        binding.toIntOrNull()?.let { lwjgl2Code ->

            Lwjgl2Keycode.lwjgl2ToControlEvent(lwjgl2Code)
        }
    }
}
