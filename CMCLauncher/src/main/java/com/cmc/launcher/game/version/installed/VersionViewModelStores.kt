package com.cmc.launcher.game.version.installed

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelStore

object VersionViewModelStores {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stores = HashMap<Version, ViewModelStore>()

    private val holderCounts = HashMap<Version, Int>()

    fun acquire(version: Version) = synchronized(lock) {
        holderCounts.merge(version, 1, Int::plus)
    }

    fun storeOf(version: Version): ViewModelStore = synchronized(lock) {
        stores.getOrPut(version) { ViewModelStore() }
    }

    fun releaseAndClearIfStale(version: Version, currentVersions: Collection<Version>) = synchronized(lock) {
        val remaining = holderCounts[version]?.dec() ?: 0
        if (remaining <= 0) {
            holderCounts.remove(version)
            if (currentVersions.none { it === version }) {

                stores.remove(version)?.clear()
            }
        } else {
            holderCounts[version] = remaining
        }
    }

    fun clearStale(currentVersions: Collection<Version>) = synchronized(lock) {
        val staleStores = stores.keys.filter { key ->
            key !in holderCounts && currentVersions.none { it === key }
        }.mapNotNull { version ->
            stores.remove(version)
        }
        if (staleStores.isNotEmpty()) {
            mainHandler.post {
                staleStores.forEach { it.clear() }
            }
        }
    }
}
