package com.cmc.launcher.filemanager.events

import android.os.Messenger
import com.cmc.launcher.filemanager.os.FmLog
import java.util.concurrent.CopyOnWriteArrayList

object FileManagerEventBus {
    private const val TAG = "FmEventBus"

    private val clients = CopyOnWriteArrayList<Messenger>()

    internal fun register(client: Messenger) {
        clients.addIfAbsent(client)
        FmLog.debug(TAG, "client registered: total=${clients.size}")
    }

    internal fun unregister(client: Messenger) {
        clients.remove(client)
        FmLog.debug(TAG, "client unregistered: total=${clients.size}")
    }

    fun dispatch(event: FileManagerEvent) {
        if (clients.isEmpty()) {
            FmLog.debug(TAG, "dispatch skipped (no listeners): type=${event.type}, dirs=${event.changedDirs}")
            return
        }
        FmLog.info(TAG, "dispatch: type=${event.type}, dirs=${event.changedDirs}")
        val dead = mutableListOf<Messenger>()
        clients.forEach { client ->
            val ok = runCatching { client.send(FileManagerEvent.toMessage(event)) }.isSuccess
            if (!ok) dead += client
        }
        if (dead.isNotEmpty()) {
            clients.removeAll(dead.toSet())
        }
    }
}
