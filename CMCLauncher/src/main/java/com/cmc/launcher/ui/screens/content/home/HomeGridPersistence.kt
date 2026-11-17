package com.cmc.launcher.ui.screens.content.home

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.movtery.cardgrid.state.GridCard
import com.tencent.mmkv.MMKV

data class HomeCardSnapshot(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("x")
    val x: Int = 0,
    @SerializedName("y")
    val y: Int = 0,
    @SerializedName("width")
    val width: Int = 0,
    @SerializedName("height")
    val height: Int = 0
)

data class HomeGridSnapshot(
    @SerializedName("columns")
    val columns: Int = 0,
    @SerializedName("cards")
    val cards: List<HomeCardSnapshot> = emptyList()
)

object HomeGridStore {
    private const val KEY_LAYOUT = "homeCardLayout"

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("home_grid") }

    private val gson = Gson()

    fun load(): HomeGridSnapshot? {
        val json = mmkv.decodeString(KEY_LAYOUT, "") ?: ""
        if (json.isBlank()) return null
        return runCatching {
            gson.fromJson(json, HomeGridSnapshot::class.java)
        }.getOrNull()?.takeIf { it.cards.isNotEmpty() }
    }

    fun save(cards: List<GridCard>, columns: Int) {
        val snapshot = HomeGridSnapshot(
            columns = columns,
            cards = cards.map { card ->
                HomeCardSnapshot(
                    id = card.id,
                    type = card.type.typeId,
                    x = card.layout.x,
                    y = card.layout.y,
                    width = card.layout.width,
                    height = card.layout.height
                )
            }
        )
        mmkv.encode(KEY_LAYOUT, gson.toJson(snapshot))
    }
}
