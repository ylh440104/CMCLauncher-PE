package com.movtery.cardgrid.engine

import androidx.compose.ui.unit.IntOffset
import com.movtery.cardgrid.model.CardLimits
import com.movtery.cardgrid.model.CardRect
import com.movtery.cardgrid.model.ResizeEdge
import kotlin.math.abs
import kotlin.math.roundToInt

object GridEngine {

    internal enum class PushAxis { Horizontal, Vertical }

    data class ResizeResult(
        val layout: CardRect,
        val pushed: Map<String, CardRect>
    )

    fun isInGrid(rect: CardRect, columns: Int): Boolean =
        rect.x >= 0 && rect.y >= 0 && rect.right <= columns

    fun hasOverlap(cards: List<CardRect>): Boolean {
        for (i in cards.indices) {
            for (j in i + 1 until cards.size) {
                if (cards[i].intersects(cards[j])) return true
            }
        }
        return false
    }

    fun totalRows(cards: List<CardRect>): Int = cards.maxOfOrNull { it.bottom } ?: 0

    fun findNearestFreeSlot(
        width: Int,
        height: Int,
        origin: IntOffset,
        columns: Int,
        obstacles: List<CardRect>
    ): IntOffset? {
        if (width <= 0 || height <= 0 || width > columns) return null
        val maxRow = obstacles.maxOfOrNull { it.bottom } ?: 0
        val originCenterX = origin.x + width / 2f
        val originCenterY = origin.y + height / 2f
        var best: IntOffset? = null
        var bestDistance = Float.MAX_VALUE
        for (cy in 0..maxRow) {
            for (cx in 0..columns - width) {
                val candidate = CardRect("", cx, cy, width, height)
                if (obstacles.any { it.intersects(candidate) }) continue
                val dx = cx + width / 2f - originCenterX
                val dy = cy + height / 2f - originCenterY
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = IntOffset(cx, cy)
                }
            }
        }
        return best
    }

    fun resolveDisplacements(
        moving: CardRect,
        columns: Int,
        cards: List<CardRect>,
        pointer: IntOffset? = null,
        directions: Map<String, IntOffset> = emptyMap()
    ): Map<String, CardRect>? {
        val displaced = cards
            .filter { it.id != moving.id && it.intersects(moving) }
            .sortedWith(readingOrder())

        val occupied = mutableListOf(moving)
        occupied.addAll(cards.filter { it.id != moving.id })
        val result = mutableMapOf<String, CardRect>()
        for (card in displaced) {
            val current = occupied.first { it.id == card.id }

            if (!current.intersects(moving)) continue
            val rawDirection = directions[current.id]
                ?: pointer?.let { displacementDirection(it, current) }
                ?: return null

            val direction = unitAxis(rawDirection) ?: return null

            val perpendicular = if (direction.x != 0) {
                IntOffset(0, if (pointer != null && pointer.y > current.y + current.height / 2f) -1 else 1)
            } else {
                IntOffset(if (pointer != null && pointer.x > current.x + current.width / 2f) -1 else 1, 0)
            }
            val chain = listOf(
                direction,
                IntOffset(-direction.x, -direction.y),
                perpendicular,
                IntOffset(-perpendicular.x, -perpendicular.y)
            ).firstNotNullOfOrNull { candidate ->
                slideChain(current, candidate, moving, columns, occupied)
            } ?: return null
            occupied.removeAll { it.id in chain }
            occupied.addAll(chain.values)
            result.putAll(chain)
        }
        return result
    }

    internal fun displacementDirection(pointer: IntOffset, card: CardRect): IntOffset {
        val dx = (pointer.x - (card.x + card.width / 2f)) / card.width
        val dy = (pointer.y - (card.y + card.height / 2f)) / card.height
        return if (abs(dx) >= abs(dy)) {
            IntOffset(if (dx > 0) -1 else 1, 0)
        } else {
            IntOffset(0, if (dy > 0) -1 else 1)
        }
    }

    internal fun unitAxis(vector: IntOffset): IntOffset? =
        if (abs(vector.x) + abs(vector.y) == 1) vector else null

    internal fun slideChain(
        head: CardRect,
        direction: IntOffset,
        mover: CardRect,
        columns: Int,
        obstacles: List<CardRect>
    ): Map<String, CardRect>? {
        val moved = linkedMapOf(head.id to head)
        while (moved.getValue(head.id).intersects(mover)) {

            while (true) {
                val blockers = obstacles.filter { obstacle ->
                    obstacle.id != mover.id && obstacle.id !in moved &&
                        moved.values.any { member -> obstacle.intersects(member.shift(direction)) }
                }
                if (blockers.isEmpty()) break
                blockers.forEach { moved[it.id] = it }
            }

            val overflow = moved.values.any { member ->
                val next = member.shift(direction)
                (direction.x != 0 && (next.x < 0 || next.right > columns)) ||
                    (direction.y < 0 && next.y < 0)
            }
            if (overflow) return null

            moved.replaceAll { _, member -> member.shift(direction) }
        }
        return moved
    }

    fun resolveResize(
        current: CardRect,
        edge: ResizeEdge,
        pointer: IntOffset,
        columns: Int,
        limits: CardLimits,
        obstacles: List<CardRect>
    ): ResizeResult {
        val lim = limits.clampedFor(columns)
        val horizontal = edge == ResizeEdge.Start || edge == ResizeEdge.End
        val minSpan = if (horizontal) lim.minWidth else lim.minHeight
        val maxSpan = if (horizontal) lim.maxWidth else lim.maxHeight

        val hardMax = when (edge) {
            ResizeEdge.End -> columns - current.x
            ResizeEdge.Start -> current.right
            ResizeEdge.Bottom -> Int.MAX_VALUE
            ResizeEdge.Top -> current.bottom
        }.coerceAtLeast(1)
        val upper = minOf(maxSpan, hardMax).coerceAtLeast(1)
        val span = when (edge) {
            ResizeEdge.End -> pointer.x - current.x
            ResizeEdge.Start -> current.right - pointer.x
            ResizeEdge.Bottom -> pointer.y - current.y
            ResizeEdge.Top -> current.bottom - pointer.y
        }.coerceIn(minSpan.coerceAtMost(upper), upper)

        val desired = withSpan(current, edge, span)
        if (span <= spanOf(current, edge)) return ResizeResult(desired, emptyMap())

        val axis = if (horizontal) PushAxis.Horizontal else PushAxis.Vertical
        val forward = edge == ResizeEdge.End || edge == ResizeEdge.Bottom
        var candidate = desired
        while (true) {
            val pushed = push(candidate, axis, forward, columns, obstacles)
            if (pushed != null) return ResizeResult(candidate, pushed)
            val shrunk = spanOf(candidate, edge) - 1
            if (shrunk < spanOf(current, edge)) return ResizeResult(current, emptyMap())
            candidate = withSpan(candidate, edge, shrunk)
        }
    }

    internal fun push(
        mover: CardRect,
        axis: PushAxis,
        forward: Boolean,
        columns: Int,
        obstacles: List<CardRect>
    ): Map<String, CardRect>? {
        val horizontal = axis == PushAxis.Horizontal

        fun leading(card: CardRect): Int = when {
            horizontal && forward -> card.x
            horizontal -> card.right
            forward -> card.y
            else -> card.bottom
        }
        fun span(card: CardRect): Int = if (horizontal) card.width else card.height
        fun placedAt(card: CardRect, front: Int): CardRect = when {
            horizontal && forward -> card.copy(x = front)
            horizontal -> card.copy(x = front - card.width)
            forward -> card.copy(y = front)
            else -> card.copy(y = front - card.height)
        }

        var front = when {
            horizontal && forward -> mover.right
            horizontal -> mover.x
            forward -> mover.bottom
            else -> mover.y
        }
        val pushed = mutableMapOf<String, CardRect>()

        val queue = obstacles
            .filter { it.id != mover.id && it.intersects(mover) }
            .toMutableList()
        while (queue.isNotEmpty()) {
            queue.sortBy { if (forward) leading(it) else -leading(it) }
            val card = queue.removeAt(0)

            if (if (forward) leading(card) >= front else leading(card) <= front) break
            val placed = placedAt(card, front)
            front += if (forward) span(card) else -span(card)
            val blocked = when {
                horizontal && forward -> front > columns
                forward -> false
                else -> front < 0
            }
            if (blocked) return null
            pushed[card.id] = placed
            queue.addAll(
                obstacles.filter {
                    it.id != mover.id && it.id !in pushed && it.intersects(placed) && it !in queue
                }
            )
        }
        return pushed
    }

    private fun spanOf(rect: CardRect, edge: ResizeEdge): Int = when (edge) {
        ResizeEdge.Start, ResizeEdge.End -> rect.width
        ResizeEdge.Top, ResizeEdge.Bottom -> rect.height
    }

    private fun withSpan(rect: CardRect, edge: ResizeEdge, span: Int): CardRect = when (edge) {
        ResizeEdge.End -> rect.copy(width = span)
        ResizeEdge.Start -> rect.copy(x = rect.right - span, width = span)
        ResizeEdge.Bottom -> rect.copy(height = span)
        ResizeEdge.Top -> rect.copy(y = rect.bottom - span, height = span)
    }

    fun findTopLeftFreeSlot(
        width: Int,
        height: Int,
        columns: Int,
        obstacles: List<CardRect>
    ): IntOffset {
        val maxRow = obstacles.maxOfOrNull { it.bottom } ?: 0
        for (cy in 0..maxRow) {
            for (cx in 0..columns - width) {
                val candidate = CardRect("", cx, cy, width, height)
                if (obstacles.none { it.intersects(candidate) }) {
                    return IntOffset(cx, cy)
                }
            }
        }
        return IntOffset(0, maxRow)
    }

    fun reflow(
        cards: List<CardRect>,
        oldColumns: Int,
        columns: Int,
        limits: (CardRect) -> CardLimits = { CardLimits.DEFAULT }
    ): List<CardRect> {
        if (cards.isEmpty()) return cards
        val scale = columns.toFloat() / oldColumns.coerceAtLeast(1)
        val placed = mutableListOf<CardRect>()
        for (card in cards.sortedWith(readingOrder())) {
            val lim = limits(card).clampedFor(columns)
            val width = (card.width * scale).roundToInt().let { lim.clampWidth(it) }
            val height = (card.height * scale).roundToInt().let { lim.clampHeight(it) }
            val slot = findTopLeftFreeSlot(width, height, columns, placed)
            placed.add(card.copy(x = slot.x, y = slot.y, width = width, height = height))
        }
        return placed
    }

    fun validate(
        cards: List<CardRect>,
        columns: Int,
        limits: (CardRect) -> CardLimits = { CardLimits.DEFAULT }
    ): List<CardRect> {
        val seen = mutableSetOf<String>()
        val clamped = mutableListOf<CardRect>()
        for (card in cards) {
            if (!seen.add(card.id)) continue
            val lim = limits(card).clampedFor(columns)
            val width = lim.clampWidth(card.width.coerceAtLeast(1))
            val height = lim.clampHeight(card.height.coerceAtLeast(1))
            val x = card.x.coerceIn(0, columns - width)
            val y = card.y.coerceAtLeast(0)
            clamped.add(card.copy(x = x, y = y, width = width, height = height))
        }
        val settled = mutableListOf<CardRect>()
        for (card in clamped.sortedWith(readingOrder())) {
            val position = if (settled.any { it.intersects(card) }) {
                findNearestFreeSlot(
                    width = card.width,
                    height = card.height,
                    origin = IntOffset(card.x, card.y),
                    columns = columns,
                    obstacles = settled
                ) ?: IntOffset(0, totalRows(settled))
            } else {
                IntOffset(card.x, card.y)
            }
            settled.add(card.positionAt(position))
        }
        return settled.sortedWith(readingOrder())
    }

    private fun CardRect.shift(direction: IntOffset): CardRect =
        positionAt(IntOffset(x + direction.x, y + direction.y))

    private fun readingOrder() = compareBy<CardRect>({ it.y }, { it.x })
}
