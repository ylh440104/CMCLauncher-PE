package com.movtery.cardgrid.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.movtery.cardgrid.engine.GridEngine
import com.movtery.cardgrid.model.CardInteraction
import com.movtery.cardgrid.model.CardLimits
import com.movtery.cardgrid.model.CardRect
import com.movtery.cardgrid.model.CardSpacing
import com.movtery.cardgrid.model.CardState
import com.movtery.cardgrid.model.CardType
import com.movtery.cardgrid.model.GridGeometry
import com.movtery.cardgrid.model.MIN_GRID_COLUMNS
import com.movtery.cardgrid.model.ResizeEdge
import com.movtery.cardgrid.model.computeGridGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class GridCard(
    val id: String,
    val type: CardType,
    val layout: CardRect,

    val reflowBase: ReflowBase? = null
)

data class ReflowBase(
    val width: Int,
    val height: Int,
    val columns: Int
)

data class CardSeed(
    val id: String,
    val typeId: String,
    val layout: CardRect
)

@Stable
class CardGridState internal constructor(
    private val scope: CoroutineScope
) {

    private data class AdjustSession(
        val card: GridCard,
        val grabOffset: Offset,
        val mode: Mode
    ) {
        sealed interface Mode {
            data object Move : Mode
            data class Resize(val edge: ResizeEdge) : Mode
        }
    }

    var geometry by mutableStateOf(GridGeometry(MIN_GRID_COLUMNS, 20f))
        private set

    var cellPx by mutableFloatStateOf(20f)
        private set

    private var densityFactor by mutableFloatStateOf(1f)

    var cardInsetPx by mutableIntStateOf(2)
        private set

    var cards by mutableStateOf<List<GridCard>>(emptyList())
        private set

    var adjustingCardId by mutableStateOf<String?>(null)
        private set

    val isAdjusting: Boolean get() = adjustingCardId != null

    var dragPreview by mutableStateOf<CardRect?>(null)
        private set

    var dragRawRect by mutableStateOf<Rect?>(null)
        private set

    var pointerPosition by mutableStateOf<Offset?>(null)
        private set

    internal var areaOffsetInRoot by mutableStateOf(Offset.Zero)
        private set

    var viewportTopPx by mutableFloatStateOf(0f)
        private set

    var viewportHeightPx by mutableFloatStateOf(0f)
        private set

    fun onAreaPositioned(offsetInRoot: Offset) {
        areaOffsetInRoot = offsetInRoot
    }

    fun onViewportPositioned(topPx: Float, heightPx: Float) {
        viewportTopPx = topPx
        viewportHeightPx = heightPx
    }

    private var pointerAnchorInRoot: Offset? = null

    var displaced by mutableStateOf<Map<String, CardRect>>(emptyMap())
        private set

    private val lockedDirections = mutableMapOf<String, IntOffset>()

    var onLayoutCommitted: () -> Unit = {}

    var onCardRemoved: (cardId: String) -> Unit = {}

    private fun commitLayout() {
        if (geometryReady) onLayoutCommitted()
    }

    internal var fastSpec: AnimationSpec<Rect> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    internal var defaultSpec: AnimationSpec<Rect> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    private var session by mutableStateOf<AdjustSession?>(null)

    val hasSession: Boolean get() = session != null

    private var pendingSeeds: List<CardSeed>? = null
    private var pendingColumns: Int = 0

    private var typeById: Map<String, CardType> = emptyMap()

    private var geometryReady = false

    private val animators = mutableMapOf<String, Animatable<Rect, AnimationVector4D>>()

    fun seed(types: List<CardType>, seeds: List<CardSeed>, storedColumns: Int) {
        typeById = types.associateBy { it.typeId }
        val distinct = seeds.distinctBy { it.id }.filter { it.typeId in typeById }
        if (distinct.isEmpty()) return

        pendingSeeds = distinct
        pendingColumns = storedColumns
        if (geometryReady) materializePending(newColumns = geometry.columns)
    }

    private fun materializePending(newColumns: Int): Boolean {
        val pending = pendingSeeds ?: return false
        pendingSeeds = null
        val typeIdById = pending.associate { it.id to it.typeId }
        val layouts = if (pendingColumns == newColumns || pendingColumns <= 0) {
            GridEngine.validate(pending.map { it.layout }, newColumns) { rect ->
                typeById[typeIdById[rect.id]]?.limits ?: CardLimits.DEFAULT
            }
        } else {
            GridEngine.reflow(
                cards = pending.map { it.layout },
                oldColumns = pendingColumns,
                columns = newColumns
            ) { rect ->
                typeById[typeIdById[rect.id]]?.limits ?: CardLimits.DEFAULT
            }
        }.associateBy { it.id }

        val materialized = pending.mapNotNull { seed ->
            typeById[seed.typeId]?.let { type ->
                val layout = layouts.getValue(seed.id)

                val base = if (pendingColumns > 0) {
                    ReflowBase(seed.layout.width, seed.layout.height, pendingColumns)
                } else {
                    ReflowBase(layout.width, layout.height, newColumns)
                }
                GridCard(id = seed.id, type = type, layout = layout, reflowBase = base)
            }
        }

        val seededIds = materialized.mapTo(mutableSetOf()) { it.id }
        cards = cards.filterNot { it.id in seededIds } + materialized
        materialized.forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
        return true
    }

    fun updateGeometry(widthDp: Float, density: Density) {
        if (widthDp <= 0f) return
        val wasReady = geometryReady
        val oldColumns = geometry.columns
        val newGeometry = computeGridGeometry(widthDp)
        cellPx = newGeometry.cellSize * density.density
        cardInsetPx = with(density) {
            CardSpacing.roundToPx()
        }
        densityFactor = density.density
        geometry = newGeometry
        geometryReady = true

        val materialized = materializePending(newColumns = newGeometry.columns)

        if (!materialized && wasReady && newGeometry.columns != oldColumns && cards.isNotEmpty()) {
            reflowTo(newColumns = newGeometry.columns, oldColumns = oldColumns)
        }
    }

    fun rootRectOf(card: GridCard): Rect {
        val rect = rectFor(card.layout)
        val offset = areaOffsetInRoot
        return Rect(
            left = rect.left + offset.x,
            top = rect.top + offset.y,
            right = rect.right + offset.x,
            bottom = rect.bottom + offset.y
        )
    }

    fun rectFor(layout: CardRect): Rect = Rect(
        left = layout.x * cellPx + cardInsetPx,
        top = layout.y * cellPx + cardInsetPx,
        right = layout.right * cellPx - cardInsetPx,
        bottom = layout.bottom * cellPx - cardInsetPx
    )

    fun gridHeightPx(): Float {
        val rows = max(
            GridEngine.totalRows(cards.map { it.layout }),
            dragPreview?.bottom ?: 0
        )
        return (rows + 1) * cellPx
    }

    fun addCard(type: CardType, id: String = UUID.randomUUID().toString()): GridCard? {
        if (cards.any { it.id == id }) return null
        val lim = type.limits.clampedFor(geometry.columns)
        val width = type.defaultSpan.x.coerceIn(lim.minWidth, lim.maxWidth)
        val height = type.defaultSpan.y.coerceIn(lim.minHeight, lim.maxHeight)
        val slot = GridEngine.findTopLeftFreeSlot(
            width = width,
            height = height,
            columns = geometry.columns,
            obstacles = layouts()
        )
        val card = GridCard(
            id = id,
            type = type,
            layout = CardRect(id = id, x = slot.x, y = slot.y, width = width, height = height),
            reflowBase = ReflowBase(width, height, geometry.columns)
        )
        cards = cards + card
        commitLayout()
        return card
    }

    fun removeCard(id: String) {
        val removed = cards.firstOrNull { it.id == id } ?: return
        cards = cards.filterNot { it.id == id }
        animators.remove(removed.id)
        if (adjustingCardId == id) adjustingCardId = null
        if (session?.card?.id == id) endSession()
        commitLayout()
        onCardRemoved(id)
    }

    fun cardAt(position: Offset): GridCard? {
        val ordered = cards
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<GridCard>> { (_, card) ->
                    if (card.id == adjustingCardId) 1 else 0
                }.thenByDescending { it.index }
            )
        return ordered.firstOrNull { (_, card) ->
            rectFor(effectiveLayout(card)).contains(position)
        }?.value
    }

    fun resizeEdgeAt(position: Offset): Pair<GridCard, ResizeEdge>? {
        val card = adjustingCardId?.let { id -> cards.firstOrNull { it.id == id } } ?: return null
        val rect = renderRectOf(card)
        val hitRadiusPx = EDGE_HIT_RADIUS_DP * densityFactor
        return ResizeEdge.entries
            .map { edge -> edge to edgeCenter(edge, rect) }
            .map { (edge, center) -> edge to (position - center).getDistance() }
            .filter { (_, distance) -> distance <= hitRadiusPx }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (edge, _) -> card to edge }
    }

    private fun edgeCenter(edge: ResizeEdge, rect: Rect): Offset = when (edge) {
        ResizeEdge.Start -> Offset(rect.left, rect.center.y)
        ResizeEdge.Top -> Offset(rect.center.x, rect.top)
        ResizeEdge.End -> Offset(rect.right, rect.center.y)
        ResizeEdge.Bottom -> Offset(rect.center.x, rect.bottom)
    }

    fun onCardDragStart(card: GridCard, pointer: Offset) {
        val layout = effectiveLayout(card)
        session = AdjustSession(
            card = card,
            grabOffset = pointer - rectFor(layout).topLeft,
            mode = AdjustSession.Mode.Move
        )
        adjustingCardId = card.id
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        dragRawRect = rectFor(layout)
        pointerPosition = pointer
        applyPreview(card.layout, emptyMap())
    }

    fun onCardDrag(pointer: Offset) {
        val current = session ?: return
        check(current.mode is AdjustSession.Mode.Move) { "Current session is not a drag session" }

        pointerAnchorInRoot = areaOffsetInRoot + pointer
        pointerPosition = pointer
        val size = rectFor(current.card.layout).size
        val topLeft = Offset(
            pointer.x - current.grabOffset.x,
            pointer.y - current.grabOffset.y
        )
        dragRawRect = Rect(offset = topLeft, size = size)

        val target = IntOffset(
            (topLeft.x / cellPx).roundToInt().coerceIn(0, geometry.columns - current.card.layout.width),
            (topLeft.y / cellPx).roundToInt().coerceAtLeast(0)
        )
        val preview = current.card.layout.positionAt(target)
        val pointerCell = IntOffset(
            (pointer.x / cellPx).roundToInt(),
            (pointer.y / cellPx).roundToInt()
        )
        lockDisplacementDirections(preview, pointerCell)
        val displacements = GridEngine.resolveDisplacements(
            moving = preview,
            columns = geometry.columns,
            cards = layouts(),
            pointer = pointerCell,
            directions = lockedDirections
        )
        if (displacements == null) {

            val clamped = GridEngine.findNearestFreeSlot(
                width = current.card.layout.width,
                height = current.card.layout.height,
                origin = target,
                columns = geometry.columns,
                obstacles = layouts().filterNot { it.id == current.card.id }
            ) ?: return
            applyPreview(current.card.layout.positionAt(clamped), emptyMap())
            return
        }
        applyPreview(preview, displacements)
    }

    private fun lockDisplacementDirections(preview: CardRect, pointerCell: IntOffset) {
        val sessionId = session?.card?.id
        val overlapped = layouts().filter { it.id != sessionId && it.intersects(preview) }
        lockedDirections.keys.retainAll(overlapped.mapTo(mutableSetOf()) { it.id })
        overlapped.forEach { card ->
            lockedDirections.getOrPut(card.id) {
                GridEngine.displacementDirection(pointerCell, card)
            }
        }
    }

    fun onCardDragEnd() {
        val current = session ?: return
        commit(previewLayoutOf(current))
    }

    fun onCardDragCancel() {
        cancelSession()
    }

    fun onResizeStart(card: GridCard, edge: ResizeEdge, pointer: Offset) {
        session = AdjustSession(
            card = card,
            grabOffset = pointer,
            mode = AdjustSession.Mode.Resize(edge)
        )
        adjustingCardId = card.id
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        dragRawRect = rectFor(card.layout)
        pointerPosition = pointer
        applyPreview(card.layout, emptyMap())
    }

    fun onResize(pointer: Offset) {
        val current = session ?: return
        val edge = (current.mode as? AdjustSession.Mode.Resize)?.edge ?: return
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        pointerPosition = pointer
        val result = GridEngine.resolveResize(
            current = current.card.layout,
            edge = edge,
            pointer = IntOffset(
                (pointer.x / cellPx).roundToInt(),
                (pointer.y / cellPx).roundToInt()
            ),
            columns = geometry.columns,
            limits = current.card.type.limits,
            obstacles = layouts().filterNot { it.id == current.card.id }
        )
        dragRawRect = rawRectForResize(current.card, edge, pointer, result.layout)
        applyPreview(result.layout, result.pushed)
    }

    fun onResizeEnd() {
        val current = session ?: return
        commit(previewLayoutOf(current))
    }

    fun onResizeCancel() {
        cancelSession()
    }

    internal fun onAutoScroll() {
        val anchor = pointerAnchorInRoot ?: return
        val local = anchor - areaOffsetInRoot
        when (session?.mode) {
            is AdjustSession.Mode.Move -> onCardDrag(local)
            is AdjustSession.Mode.Resize -> onResize(local)
            null -> Unit
        }
    }

    fun interactionOf(cardId: String): CardInteraction {
        val current = session
        return when {
            current?.card?.id == cardId && current.mode is AdjustSession.Mode.Resize -> CardInteraction.Resizing
            current?.card?.id == cardId && current.mode is AdjustSession.Mode.Move -> CardInteraction.Dragging
            adjustingCardId == cardId -> CardInteraction.Adjusting
            else -> CardInteraction.Idle
        }
    }

    fun isSessionCard(cardId: String): Boolean = session?.card?.id == cardId

    fun exitAdjusting() {
        if (session != null) cancelSession()
        adjustingCardId = null
    }

    fun cardStateOf(card: GridCard): CardState {
        val layout = if (isSessionCard(card.id)) dragPreview ?: card.layout else card.layout
        return CardState(
            spanWidth = layout.width,
            spanHeight = layout.height,
            columns = geometry.columns,
            interaction = interactionOf(card.id)
        )
    }

    internal fun animatorFor(card: GridCard): Animatable<Rect, AnimationVector4D> =
        animators.getOrPut(card.id) {
            Animatable(rectFor(card.layout), Rect.VectorConverter)
        }

    internal fun renderRectOf(card: GridCard): Rect {
        if (isSessionCard(card.id)) return dragRawRect ?: animatorFor(card).value
        return animatorFor(card).value
    }

    private fun animateTo(card: GridCard, layout: CardRect, spec: AnimationSpec<Rect>) {
        val animatable = animatorFor(card)
        val target = rectFor(layout)
        scope.launch { animatable.animateTo(target, spec) }
    }

    private fun animateAll() {
        cards.forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
    }

    private fun settleSessionCard(card: GridCard, rawRect: Rect?, target: CardRect) {
        val animatable = animatorFor(card)
        scope.launch {
            rawRect?.let { animatable.snapTo(it) }
            animatable.animateTo(rectFor(target), defaultSpec)
        }
    }

    internal fun effectiveLayout(card: GridCard): CardRect =
        displaced[card.id] ?: card.layout

    private fun previewLayoutOf(session: AdjustSession): CardRect =
        dragPreview ?: session.card.layout

    private fun layouts(): List<CardRect> = cards.map { it.layout }

    private fun applyPreview(preview: CardRect, displacements: Map<String, CardRect>) {
        if (dragPreview == preview && displaced.keys == displacements.keys) return
        dragPreview = preview
        val affected = displaced.keys + displacements.keys
        displaced = displacements
        affected.forEach { id ->
            cards.firstOrNull { it.id == id }?.let { card ->
                animateTo(card, displacements[id] ?: card.layout, fastSpec)
            }
        }
    }

    private fun commit(target: CardRect) {
        val current = session ?: return

        val rawRect = dragRawRect
        val settled = displaced
        cards = cards.map { card ->
            when {
                card.id == current.card.id -> card.copy(
                    layout = target,

                    reflowBase = ReflowBase(target.width, target.height, geometry.columns)
                )
                else -> settled[card.id]?.let { card.copy(layout = it) } ?: card
            }
        }
        endSession()
        settleSessionCard(current.card, rawRect, target)
        cards.filterNot { it.id == current.card.id }
            .forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
        commitLayout()
    }

    private fun cancelSession() {
        val current = session ?: return
        val rawRect = dragRawRect
        val affected = displaced.keys
        endSession()
        affected.forEach { id ->
            cards.firstOrNull { it.id == id }?.let { animateTo(it, it.layout, defaultSpec) }
        }
        settleSessionCard(current.card, rawRect, current.card.layout)
    }

    private fun endSession() {
        session = null
        dragPreview = null
        dragRawRect = null
        pointerPosition = null
        displaced = emptyMap()
        lockedDirections.clear()
    }

    private fun reflowTo(newColumns: Int, oldColumns: Int) {

        val respanned = cards.map { card ->
            val base = card.reflowBase
            if (base == null) {
                card.layout
            } else {
                val lim = card.type.limits.clampedFor(newColumns)
                card.layout.copy(
                    width = (base.width * newColumns.toFloat() / base.columns).roundToInt()
                        .let(lim::clampWidth),
                    height = (base.height * newColumns.toFloat() / base.columns).roundToInt()
                        .let(lim::clampHeight)
                )
            }
        }

        val layouts = GridEngine.reflow(
            cards = respanned,
            oldColumns = newColumns,
            columns = newColumns
        ) { rect -> cards.firstOrNull { it.id == rect.id }?.type?.limits ?: CardLimits.DEFAULT }
            .associateBy { it.id }
        cards = cards.map { card -> card.copy(layout = layouts.getValue(card.id)) }
        endSession()
        adjustingCardId = null
        animateAll()
        commitLayout()
    }

    private fun rawRectForResize(
        card: GridCard,
        edge: ResizeEdge,
        pointer: Offset,
        settled: CardRect
    ): Rect {
        val layout = card.layout
        val lim = card.type.limits.clampedFor(geometry.columns)
        val minSpan = if (edge == ResizeEdge.Start || edge == ResizeEdge.End) lim.minWidth else lim.minHeight
        val maxSpan = when (edge) {
            ResizeEdge.Start, ResizeEdge.End -> settled.width
            ResizeEdge.Top, ResizeEdge.Bottom -> settled.height
        }
        val range = minSpan..maxSpan
        val left = layout.x * cellPx + cardInsetPx
        val top = layout.y * cellPx + cardInsetPx
        val right = layout.right * cellPx - cardInsetPx
        val bottom = layout.bottom * cellPx - cardInsetPx

        fun edgePxStart(span: Int) = (layout.right - span) * cellPx + cardInsetPx
        fun edgePxTop(span: Int) = (layout.bottom - span) * cellPx + cardInsetPx
        fun edgePxEnd(span: Int) = (layout.x + span) * cellPx - cardInsetPx
        fun edgePxBottom(span: Int) = (layout.y + span) * cellPx - cardInsetPx
        return when (edge) {
            ResizeEdge.End -> Rect(left, top, pointer.x.coerceIn(edgePxEnd(range.first), edgePxEnd(range.last)), bottom)
            ResizeEdge.Start -> Rect(pointer.x.coerceIn(edgePxStart(range.last), edgePxStart(range.first)), top, right, bottom)
            ResizeEdge.Bottom -> Rect(left, top, right, pointer.y.coerceIn(edgePxBottom(range.first), edgePxBottom(range.last)))
            ResizeEdge.Top -> Rect(left, pointer.y.coerceIn(edgePxTop(range.last), edgePxTop(range.first)), right, bottom)
        }
    }

    companion object {

        private const val EDGE_HIT_RADIUS_DP = 24f
    }
}

@Composable
fun rememberCardGridState(): CardGridState {
    val scope = rememberCoroutineScope()
    return remember { CardGridState(scope) }
}
