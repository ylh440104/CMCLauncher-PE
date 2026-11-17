package com.cmc.launcher.game.path

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gamePaths")
data class GamePath(

    @PrimaryKey
    val id: String,

    var title: String,

    val path: String
)
