package com.cmc.launcher.filemanager.events

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class FileManagerEvent(
    val type: Type,
    val changedDirs: List<String>,
    val id: String = UUID.randomUUID().toString()
) : Parcelable {
    enum class Type {

        CREATE,

        RENAME,

        DELETE,

        COPY_PASTE,

        ARCHIVE,

        EXTRACT,

        IMPORT,

        TRASH_RESTORE,

        TRASH_PURGE,

        TRASH_CLEAR
    }

    companion object {
        const val KEY_EVENT = "fm_event"

        fun toMessage(event: FileManagerEvent): Message = Message.obtain().apply {
            what = MSG_EVENT
            data = Bundle().apply {
                putParcelable(KEY_EVENT, event)
            }
        }

        fun fromMessage(message: Message): FileManagerEvent? {
            if (message.what != MSG_EVENT) return null
            val data = message.data ?: return null
            data.classLoader = FileManagerEvent::class.java.classLoader
            return data.getParcelable(KEY_EVENT)
        }

        const val MSG_REGISTER_CLIENT = 1
        const val MSG_UNREGISTER_CLIENT = 2
        const val MSG_EVENT = 100
    }
}

fun Messenger.sendEvent(event: FileManagerEvent) {
    runCatching {
        send(FileManagerEvent.toMessage(event))
    }
}
