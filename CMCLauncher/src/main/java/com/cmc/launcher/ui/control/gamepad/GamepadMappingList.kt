package com.cmc.launcher.ui.control.gamepad

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class GamepadMappingList(
    val name: String,
    val list: MutableList<GamepadMapping>
) : Parcelable {

    @IgnoredOnParcel
    private val allKeyMappings = mutableMapOf<Int, TargetKeys>()
    @IgnoredOnParcel
    private val allDpadMappings = mutableMapOf<DpadDirection, TargetKeys>()

    data class TargetKeys(
        val inGame: Set<String>,
        val inMenu: Set<String>
    ) {
        fun getKeys(isInGame: Boolean) = if (isInGame) inGame else inMenu
    }

    fun load() {
        allKeyMappings.clear()
        allDpadMappings.clear()

        list.forEach { mapping ->
            addInMappingsMap(mapping)
        }
    }

    private fun addInMappingsMap(mapping: GamepadMapping) {
        val target = TargetKeys(mapping.targetsInGame, mapping.targetsInMenu)
        mapping.dpadDirection?.let {
            allDpadMappings[it] = target
        } ?: run {
            allKeyMappings[mapping.key] = target
        }
    }

    fun resetMapping(gamepadMap: GamepadMap, inGame: Boolean) =
        applyMapping(gamepadMap, inGame)

    fun saveMapping(gamepadMap: GamepadMap, targets: Set<String>, inGame: Boolean) =
        applyMapping(gamepadMap, inGame, customTargets = targets)

    private fun applyMapping(
        gamepadMap: GamepadMap,
        inGame: Boolean,
        customTargets: Set<String>? = null,
    ) {
        val dpad = gamepadMap.dpadDirection
        val isDpad = dpad != null
        val existing = if (isDpad) allDpadMappings[dpad] else allKeyMappings[gamepadMap.gamepad]

        val (targetsInGame, targetsInMenu) = if (inGame) {
            val newTargets = customTargets ?: gamepadMap.defaultKeysInGame
            newTargets to (existing?.inMenu ?: emptySet())
        } else {
            val newTargets = customTargets ?: gamepadMap.defaultKeysInMenu
            (existing?.inGame ?: emptySet()) to newTargets
        }

        val mapping = GamepadMapping(
            key = gamepadMap.gamepad,
            dpadDirection = dpad,
            targetsInGame = targetsInGame,
            targetsInMenu = targetsInMenu
        )
        addInMappingsMap(mapping)
        list.removeIf { mapping0 ->
            if (isDpad) {
                mapping0.dpadDirection == mapping.dpadDirection
            } else {
                mapping0.dpadDirection == null && mapping0.key == mapping.key
            }
        }
        list.add(mapping)
        save()
    }

    fun findByCode(key: Int, inGame: Boolean) =
        allKeyMappings[key]?.getKeys(inGame)

    fun findByDpad(dir: DpadDirection, inGame: Boolean) =
        allDpadMappings[dir]?.getKeys(inGame)

    fun findByMap(map: GamepadMap, inGame: Boolean) =
        (map.dpadDirection?.let { allDpadMappings[it] } ?: allKeyMappings[map.gamepad])
            ?.getKeys(inGame)

    fun save() {
        keyMappingListMMKV().encode(name, this)
    }
}
