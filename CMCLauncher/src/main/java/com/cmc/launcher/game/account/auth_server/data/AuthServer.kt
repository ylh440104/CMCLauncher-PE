package com.cmc.launcher.game.account.auth_server.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class AuthServer(

    @PrimaryKey
    val baseUrl: String,

    var serverName: String,

    var register: String? = null
)
