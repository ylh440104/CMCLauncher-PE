package com.cmc.launcher.ui.screens.content.home

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movtery.cardgrid.model.CardRect
import com.movtery.cardgrid.state.CardGridState
import com.movtery.cardgrid.state.CardSeed
import com.movtery.cardgrid.ui.CardGrid
import com.movtery.cardgrid.ui.CardGridAutoScroll
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.screens.content.elements.backgroundGlass
import com.cmc.launcher.ui.screens.content.home.server.ServerCardManager
import com.cmc.launcher.ui.screens.content.home.server.ServerCardSettingsHost
import com.cmc.launcher.ui.screens.content.home.version.VersionCardManager
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface HomeCardOperation {
    data object None : HomeCardOperation

    data class ServerCardSettings(val cardId: String) : HomeCardOperation
}

@Composable
fun HomeGrid(
    state: CardGridState,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var operation by remember { mutableStateOf<HomeCardOperation>(HomeCardOperation.None) }

    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load()
        state.seed(
            types = HomeCards.userCardTypes,
            seeds = snapshot?.cards?.map { entry ->
                CardSeed(
                    id = entry.id,
                    typeId = entry.type,
                    layout = CardRect(
                        id = entry.id,
                        x = entry.x,
                        y = entry.y,
                        width = entry.width,
                        height = entry.height
                    )
                )
            } ?: emptyList(),
            storedColumns = snapshot?.columns ?: 0
        )
    }

    LaunchedEffect(Unit) {
        state.onLayoutCommitted = {
            HomeGridStore.save(
                cards = state.cards,
                columns = state.geometry.columns
            )
        }
    }

    LaunchedEffect(Unit) {
        state.onCardRemoved = { cardId ->
            VersionCardManager.removeCard(cardId)
            ServerCardManager.removeCard(cardId)
        }
    }

    LaunchedEffect(Unit) {
        VersionCardManager.cards.collect { states ->
            states.forEach { cardState ->
                if (state.cards.none { it.id == cardState.record.cardId }) {
                    state.addCard(HomeCards.versionCardType(), cardState.record.cardId)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ServerCardManager.cards.collect { states ->
            states.forEach { cardState ->
                if (state.cards.none { it.id == cardState.record.cardId }) {
                    state.addCard(HomeCards.serverCardType(), cardState.record.cardId)
                }
            }
        }
    }

    val isVisibleState = rememberUpdatedState(isVisible)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state) {
        snapshotFlow {
            val top = state.viewportTopPx
            val bottom = top + state.viewportHeightPx
            if (!isVisibleState.value || !resumed || bottom <= 0f) {
                emptySet()
            } else {
                state.cards.mapNotNullTo(mutableSetOf()) { card ->
                    if (card.type.typeId != HomeCards.SERVER_CARD_TYPE_ID) return@mapNotNullTo null
                    val rect = state.rootRectOf(card)
                    if (rect.bottom >= top && rect.top <= bottom) card.id else null
                }
            }
        }.distinctUntilChanged().collect { ids ->
            ServerCardManager.onVisibleCardsChanged(ids)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                state.onViewportPositioned(
                    topPx = coordinates.positionInRoot().y,
                    heightPx = coordinates.size.height.toFloat()
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val systemCards = HomeCards.systemCards()
            if (!systemCards.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    systemCards.forEach { systemCard ->
                        key(systemCard.id) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp)) {
                                systemCard.content()
                            }
                        }
                    }
                }
            }

            CardGrid(
                state = state,
                containerColor = cardColor(),
                contentColor = onCardColor(),
                cardBackground = { base ->
                    base.backgroundGlass(
                        blur = AllSettings.backgroundBlur.state,
                        color = cardColor(),
                        enabled = true
                    )
                },
            )
        }
        CardGridAutoScroll(
            state = state,
            scrollState = scrollState
        )

        if (state.isAdjusting)  {
            AdjustingBar(
                state = state,
                onShowSettings = {
                    operation = HomeCardOperation.ServerCardSettings(it)
                }
            )
        }
    }

    HomeCardOperation(
        operation = operation,
        onChange = { operation = it }
    )
}

@Composable
private fun HomeCardOperation(
    operation: HomeCardOperation,
    onChange: (HomeCardOperation) -> Unit
) {
    when (operation) {
        is HomeCardOperation.None -> {}
        is HomeCardOperation.ServerCardSettings -> {
            ServerCardSettingsHost(
                cardId = operation.cardId,
                onDismiss = { onChange(HomeCardOperation.None) }
            )
        }
    }
}

@Composable
private fun AdjustingBar(
    state: CardGridState,
    onShowSettings: (String) -> Unit
) {
    val adjustingId = state.adjustingCardId
    val isServerCard = remember(state.cards, adjustingId) {
        state.cards.firstOrNull { it.id == adjustingId }?.type?.typeId == HomeCards.SERVER_CARD_TYPE_ID
    }

    Surface(
        modifier = Modifier
            .padding(all = 3.dp)
            .barGuard(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(all = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isServerCard && adjustingId != null)  {
                IconButton(
                    modifier = Modifier.size(34.dp),
                    onClick = { adjustingId?.let(onShowSettings) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_filled),
                        contentDescription = stringResource(R.string.generic_setting)
                    )
                }
                Spacer(Modifier.width(2.dp))
            }

            IconButton(
                modifier = Modifier.size(34.dp),
                enabled = adjustingId != null,
                onClick = { adjustingId?.let(state::removeCard) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_filled),
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = stringResource(R.string.generic_delete)
                )
            }

            Spacer(Modifier.width(2.dp))

            IconButton(
                modifier = Modifier.size(34.dp),
                onClick = { state.exitAdjusting() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.generic_done)
                )
            }
        }
    }
}

private fun Modifier.barGuard(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) return@awaitEachGesture
        }
    }
}
