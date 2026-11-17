package com.cmc.launcher.ui.screens.content.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.cardgrid.model.CardLimits
import com.movtery.cardgrid.model.CardType
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.R
import com.cmc.launcher.ui.components.BackgroundCard
import com.cmc.launcher.ui.screens.content.home.server.ServerCardContent
import com.cmc.launcher.ui.screens.content.home.version.VersionCardContent

class SystemCard(val id: String, val content: @Composable () -> Unit)

val CardIconShape = RoundedCornerShape(percent = 28)

object HomeCards {

    const val VERSION_CARD_TYPE_ID = "version_card"

    const val SERVER_CARD_TYPE_ID = "server_card"

    private val versionCardType = CardType(
        typeId = VERSION_CARD_TYPE_ID,
        defaultSpan = IntOffset(10, 6),
        limits = CardLimits(
            minWidth = 10,
            minHeight = 4,
            maxHeight = 12
        ),
        content = { cardId ->
            VersionCardContent(cardId)
        }
    )

    private val serverCardType = CardType(
        typeId = SERVER_CARD_TYPE_ID,
        defaultSpan = IntOffset(10, 6),
        limits = CardLimits(
            minWidth = 10,
            minHeight = 4,
            maxHeight = 12
        ),
        content = { cardId ->
            ServerCardContent(cardId)
        }
    )

    fun versionCardType(): CardType = versionCardType

    fun serverCardType(): CardType = serverCardType

    val userCardTypes: List<CardType> = listOf(versionCardType, serverCardType)

    fun systemCards(): List<SystemCard> = emptyList()
}
