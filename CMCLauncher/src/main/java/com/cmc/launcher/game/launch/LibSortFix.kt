package com.cmc.launcher.game.launch

import com.cmc.launcher.game.versioninfo.models.GameManifest

class LibSortFix {

    fun LinkedHashMap<GameManifest.Library, String>.insertLib(
        libItem: GameManifest.Library,
        path: String
    ) {
        val name = libItem.name
        if (name.startsWith("org.ow2.asm:asm:")) {
            removeIf { (key, _) ->
                key.name.startsWith("org.ow2.asm:asm-all:")
            }
        }

        this[libItem] = path
    }

    private fun <K, V> LinkedHashMap<K, V>.removeIf(predicate: (Map.Entry<K, V>) -> Boolean) {
        val iterator = this.entries.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
            }
        }
    }
}
