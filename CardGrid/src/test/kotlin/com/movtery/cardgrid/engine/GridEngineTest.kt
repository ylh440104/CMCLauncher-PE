package com.movtery.cardgrid.engine

import androidx.compose.ui.unit.IntOffset
import com.movtery.cardgrid.model.CardLimits
import com.movtery.cardgrid.model.CardRect
import com.movtery.cardgrid.model.CardSizeClass
import com.movtery.cardgrid.model.ResizeEdge
import com.movtery.cardgrid.model.computeGridGeometry
import com.movtery.cardgrid.model.deriveSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class GridEngineTest {

    private fun card(
        id: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) = CardRect(id = id, x = x, y = y, width = width, height = height)

    @Test
    fun testGridColumnsAlwaysEven() {

        assertEquals(26, computeGridGeometry(500f).columns)

        assertEquals(16, computeGridGeometry(300f).columns)

        assertEquals(10, computeGridGeometry(199f).columns)
    }

    @Test
    fun testGridEdgesAlignToContainer() {
        val geometry = computeGridGeometry(500f)
        assertEquals(500f, geometry.columns * geometry.cellSize, 0.01f)

        assertTrue(abs(geometry.cellSize - 20f) < 2f)
    }

    @Test
    fun testGridMinimumColumns() {
        assertEquals(4, computeGridGeometry(30f).columns)
        assertEquals(4, computeGridGeometry(0f).columns)
        assertEquals(4, computeGridGeometry(-10f).columns)
    }

    @Test
    fun testDeriveSizeClassHeightBySpan() {
        val columns = 16

        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(10, 2, columns).height)
        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(10, 4, columns).height)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(10, 5, columns).height)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(10, 6, columns).height)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(10, 7, columns).height)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 8, columns).height)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 9, columns).height)
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(10, 10, columns).height)
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(10, 20, columns).height)
    }

    @Test
    fun testDeriveSizeClassWidthByFraction() {
        val columns = 16

        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(3, 8, columns).width)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(4, 8, columns).width)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(6, 8, columns).width)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(7, 8, columns).width)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 8, columns).width)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(12, 8, columns).width)

        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(13, 8, columns).width)

        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(16, 8, columns).width)

        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(16, 7, columns).height)
    }

    @Test
    fun testDeriveSizeClassDimensionsIndependent() {
        val columns = 16

        val size = deriveSizeClass(width = 3, height = 10, columns = columns)
        assertEquals(CardSizeClass.COMPACT, size.width)
        assertEquals(CardSizeClass.EXTRA_LARGE, size.height)

        val square = deriveSizeClass(width = 7, height = 7, columns = columns)
        assertEquals(CardSizeClass.MEDIUM, square.width)
        assertEquals(CardSizeClass.MEDIUM, square.height)
    }

    @Test
    fun testFindNearestSlotPrefersSidewaysWhenBlockedBelow() {

        val obstacles = listOf(
            card("block1", 8, 0, 8, 4),
            card("block2", 8, 4, 8, 4)
        )
        val slot = GridEngine.findNearestFreeSlot(8, 4, IntOffset(8, 0), 16, obstacles)
        assertEquals(IntOffset(0, 0), slot)
    }

    @Test
    fun testFindNearestSlotFallsBelowFullRow() {

        val obstacles = listOf(card("block", 0, 0, 16, 4))
        val slot = GridEngine.findNearestFreeSlot(16, 4, IntOffset(0, 0), 16, obstacles)
        assertEquals(IntOffset(0, 4), slot)
    }

    @Test
    fun testFindNearestSlotGuaranteedBelowAllObstacles() {

        val obstacles = listOf(
            card("a", 0, 0, 4, 4),
            card("b", 0, 4, 4, 4)
        )
        val slot = GridEngine.findNearestFreeSlot(4, 4, IntOffset(0, 4), 4, obstacles)
        assertEquals(IntOffset(0, 8), slot)
    }

    @Test
    fun testFindNearestSlotImpossibleWidth() {
        assertNull(
            GridEngine.findNearestFreeSlot(20, 4, IntOffset(0, 0), 16, emptyList())
        )
    }

    @Test
    fun testDisplacedCardSlidesSidewaysWithoutCascade() {
        val columns = 16
        val moving = card("A", 0, 0, 8, 4)
        val others = listOf(
            card("B", 4, 0, 8, 4),
            card("C", 0, 4, 8, 4)
        )

        val result = GridEngine.resolveDisplacements(moving, columns, others, IntOffset(5, 2))
        assertEquals(card("B", 8, 0, 8, 4), result?.get("B"))
        assertFalse(result.orEmpty().containsKey("C"))
    }

    @Test
    fun testDisplacedCardFallsToNextRowWhenPointerAbove() {
        val columns = 16
        val moving = card("A", 0, 0, 16, 4)
        val others = listOf(card("B", 0, 0, 8, 4))

        val result = GridEngine.resolveDisplacements(moving, columns, others, IntOffset(4, 1))
        assertEquals(card("B", 0, 4, 8, 4), result?.get("B"))
    }

    @Test
    fun testDragPartsCardsAwayFromPointer() {
        val columns = 16

        val moving = card("B", 4, 0, 8, 4)
        val others = listOf(
            card("L", 2, 0, 4, 4),
            card("R", 10, 0, 4, 4)
        )
        val result = GridEngine.resolveDisplacements(moving, columns, others, IntOffset(8, 2))
        assertEquals(
            mapOf(
                "L" to card("L", 0, 0, 4, 4),
                "R" to card("R", 12, 0, 4, 4)
            ),
            result
        )
        val settled = others.map { result?.get(it.id) ?: it } + moving
        assertFalse(GridEngine.hasOverlap(settled))
    }

    @Test
    fun testDisplacementChainsThroughBlockingCards() {

        val moving = card("A", 0, 0, 4, 4)
        val others = listOf(
            card("B", 0, 0, 4, 4),
            card("D", 0, 4, 4, 4)
        )

        val result = GridEngine.resolveDisplacements(moving, 8, others, IntOffset(2, 1))
        assertEquals(
            mapOf(
                "B" to card("B", 0, 4, 4, 4),
                "D" to card("D", 0, 8, 4, 4)
            ),
            result
        )
    }

    @Test
    fun testDisplacementChainReversesWhenBlocked() {

        val moving = card("A", 0, 2, 4, 4)
        val others = listOf(card("B", 0, 0, 4, 4))

        val result = GridEngine.resolveDisplacements(moving, 8, others, IntOffset(2, 5))
        assertEquals(mapOf("B" to card("B", 0, 6, 4, 4)), result)
    }

    @Test
    fun testDisplacementFallsBackToPerpendicularWhenRowBlocked() {

        val a = card("A", 0, 0, 8, 4)
        val others = listOf(
            card("B", 4, 0, 4, 4),
            card("C", 8, 0, 4, 4),
            card("D", 12, 0, 4, 4)
        )

        val result = GridEngine.resolveDisplacements(a, 16, others, IntOffset(5, 1))
        assertEquals(mapOf("B" to card("B", 4, 4, 4, 4)), result)
    }

    @Test
    fun testDisplacementChainsDownThroughPerpendicularFallback() {

        val moving = card("A", 2, 2, 4, 4)
        val others = listOf(
            card("L", 0, 0, 6, 4),
            card("B", 0, 4, 6, 4)
        )
        val result = GridEngine.resolveDisplacements(moving, 8, others, IntOffset(2, 2))
        assertEquals(
            mapOf(
                "L" to card("L", 0, 6, 6, 4),
                "B" to card("B", 0, 10, 6, 4)
            ),
            result
        )
    }

    @Test
    fun testDisplacementPartsVerticallyWhenHorizontalDead() {

        val moving = card("B", 4, 0, 8, 4)
        val others = listOf(
            card("L", 0, 0, 6, 4),
            card("R", 10, 0, 6, 4)
        )
        val result = GridEngine.resolveDisplacements(moving, 18, others, IntOffset(8, 2))
        assertEquals(
            mapOf(
                "L" to card("L", 0, 4, 6, 4),
                "R" to card("R", 12, 0, 6, 4)
            ),
            result
        )
        val settled = others.map { result?.get(it.id) ?: it } + moving
        assertFalse(GridEngine.hasOverlap(settled))
    }

    @Test
    fun testDisplacementDirectionPicksDominantSide() {

        val b = card("B", 4, 4, 4, 4)

        assertEquals(IntOffset(1, 0), GridEngine.displacementDirection(IntOffset(3, 6), b))

        assertEquals(IntOffset(-1, 0), GridEngine.displacementDirection(IntOffset(9, 6), b))

        assertEquals(IntOffset(0, 1), GridEngine.displacementDirection(IntOffset(6, 3), b))

        assertEquals(IntOffset(0, -1), GridEngine.displacementDirection(IntOffset(6, 9), b))

        assertEquals(IntOffset(-1, 0), GridEngine.displacementDirection(IntOffset(8, 8), b))
    }

    @Test
    fun testDisplacementDirectionNormalizesByCardSpan() {

        val wide = card("W", 0, 5, 28, 5)

        assertEquals(IntOffset(0, -1), GridEngine.displacementDirection(IntOffset(4, 12), wide))

        val tall = card("T", 0, 0, 4, 12)
        assertEquals(IntOffset(-1, 0), GridEngine.displacementDirection(IntOffset(3, 6), tall))
    }

    @Test
    fun testDisplacementFollowsPointerSide() {

        val others = listOf(card("B", 4, 0, 4, 4))
        val result = GridEngine.resolveDisplacements(card("A", 0, 0, 6, 4), 16, others, IntOffset(5, 1))
        assertEquals(mapOf("B" to card("B", 6, 0, 4, 4)), result)

        val downward = GridEngine.resolveDisplacements(card("A", 0, 0, 16, 6), 16, listOf(card("B", 0, 0, 4, 4)), IntOffset(2, 1))
        assertEquals(mapOf("B" to card("B", 0, 6, 4, 4)), downward)
    }

    @Test
    fun testResizeEndPushesOverlappedCard() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 4, 0, 8, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(mapOf("B" to card("B", 8, 0, 8, 4)), result.pushed)
    }

    @Test
    fun testResizeEndBlockedWhenSideFull() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 4, 0, 12, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeEndGrowsIntoFreeSpaceWithLeftNeighbor() {

        val a = card("A", 4, 0, 3, 4)
        val obstacles = listOf(card("B", 0, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(10, 0), 10, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 4, 0, 6, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeBottomKeepsCardAbove() {

        val a = card("A", 0, 4, 4, 4)
        val obstacles = listOf(card("B", 0, 0, 8, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Bottom, IntOffset(0, 100), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 4, 4, 12), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeEndPushesChainOfCards() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(
            card("B", 4, 0, 4, 4),
            card("C", 8, 0, 4, 4)
        )
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(10, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(
            mapOf(
                "B" to card("B", 8, 0, 4, 4),
                "C" to card("C", 12, 0, 4, 4)
            ),
            result.pushed
        )
    }

    @Test
    fun testResizeStartPushesOverlappedCard() {

        val a = card("A", 4, 0, 4, 4)
        val obstacles = listOf(card("B", 2, 0, 2, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Start, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 2, 0, 6, 4), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 0, 2, 4)), result.pushed)
    }

    @Test
    fun testResizeStartBlockedByFlushCard() {

        val a = card("A", 4, 0, 4, 4)
        val obstacles = listOf(card("B", 0, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Start, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 4, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeBottomPushesCardBelow() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 0, 8, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Bottom, IntOffset(0, 100), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 12), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 12, 4, 4)), result.pushed)
    }

    @Test
    fun testResizeTopPushesCardAbove() {

        val a = card("A", 0, 4, 4, 4)
        val obstacles = listOf(card("B", 0, 1, 4, 2))
        val result = GridEngine.resolveResize(a, ResizeEdge.Top, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 2, 4, 6), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 0, 4, 2)), result.pushed)
    }

    @Test
    fun testResizeTopBlockedByGridEdge() {

        val a = card("A", 0, 4, 4, 4)
        val result = GridEngine.resolveResize(a, ResizeEdge.Top, IntOffset(0, 0), 16, CardLimits.DEFAULT, emptyList())
        assertEquals(card("A", 0, 0, 4, 8), result.layout)
    }

    @Test
    fun testResizeShrinkUnaffectedByPush() {

        val a = card("A", 0, 0, 8, 4)
        val obstacles = listOf(card("B", 8, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizePushResolvesPerpendicularOverlap() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(
            card("B", 4, 0, 4, 6),
            card("C", 10, 4, 4, 4)
        )
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(12, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(
            mapOf(
                "B" to card("B", 8, 0, 4, 6),
                "C" to card("C", 12, 4, 4, 4)
            ),
            result.pushed
        )
    }

    @Test
    fun testResizeIgnoresCardsOutsideSpan() {

        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 6, 4, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 16, 4), result.layout)

        val tall = card("A", 0, 0, 4, 8)
        val partial = listOf(card("C", 6, 2, 4, 2))
        val partialResult = GridEngine.resolveResize(tall, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, partial)
        assertEquals(card("A", 0, 0, 12, 8), partialResult.layout)
        assertEquals(mapOf("C" to card("C", 12, 2, 4, 2)), partialResult.pushed)
    }

    @Test
    fun testResizeEndClampsToGridEdge() {

        val a = card("A", 0, 0, 4, 4)
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 6, CardLimits.DEFAULT, emptyList())
        assertEquals(card("A", 0, 0, 6, 4), result.layout)
    }

    @Test
    fun testReflowScalesSpansProportionally() {
        val cards = listOf(card("A", 0, 0, 4, 4))
        val result = GridEngine.reflow(cards, oldColumns = 8, columns = 16)
        assertEquals(card("A", 0, 0, 8, 8), result.first())
    }

    @Test
    fun testReflowKeepsReadingOrder() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 4, 8, 4)
        )
        val result = GridEngine.reflow(cards, oldColumns = 8, columns = 16)
        val a = result.first { it.id == "A" }
        val b = result.first { it.id == "B" }
        assertTrue(a.y <= b.y)
        assertFalse(GridEngine.hasOverlap(result))
    }

    @Test
    fun testReflowClampsToLimits() {
        val cards = listOf(card("A", 0, 0, 16, 16))
        val limits: (CardRect) -> CardLimits = { CardLimits(maxWidth = 8, maxHeight = 8) }
        val result = GridEngine.reflow(cards, oldColumns = 16, columns = 16, limits = limits)
        assertEquals(card("A", 0, 0, 8, 8), result.first())
    }

    @Test
    fun testReflowShrinkingColumnsNeverOverflows() {
        val cards = listOf(
            card("A", 0, 0, 16, 4),
            card("B", 0, 4, 16, 4)
        )
        val result = GridEngine.reflow(cards, oldColumns = 16, columns = 8)
        assertTrue(result.all { GridEngine.isInGrid(it, 8) })
        assertFalse(GridEngine.hasOverlap(result))
    }

    @Test
    fun testValidateClampsOutOfBounds() {
        val result = GridEngine.validate(
            listOf(card("A", 14, -2, 8, 4)),
            columns = 16
        )
        assertEquals(card("A", 8, 0, 8, 4), result.first())
    }

    @Test
    fun testValidateResolvesOverlap() {
        val result = GridEngine.validate(
            listOf(
                card("A", 0, 0, 8, 4),
                card("B", 0, 0, 8, 4)
            ),
            columns = 16
        )
        assertFalse(GridEngine.hasOverlap(result))
        assertEquals(2, result.size)
    }

    @Test
    fun testValidateDeduplicatesIds() {
        val result = GridEngine.validate(
            listOf(
                card("A", 0, 0, 8, 4),
                card("A", 0, 0, 8, 4)
            ),
            columns = 16
        )
        assertEquals(1, result.size)
    }

    @Test
    fun testValidateClampsSizeToLimits() {
        val limits: (CardRect) -> CardLimits = { CardLimits(minWidth = 4, minHeight = 4, maxWidth = 8, maxHeight = 8) }
        val result = GridEngine.validate(
            listOf(card("A", 0, 0, 16, 1)),
            columns = 16,
            limits = limits
        )
        assertEquals(card("A", 0, 0, 8, 4), result.first())
    }

    @Test
    fun testValidatePreservesVerticalGaps() {
        val result = GridEngine.validate(
            listOf(
                card("A", 0, 0, 8, 4),
                card("B", 0, 8, 8, 4)
            ),
            columns = 16
        )
        assertEquals(card("A", 0, 0, 8, 4), result.first { it.id == "A" })
        assertEquals(card("B", 0, 8, 8, 4), result.first { it.id == "B" })
    }

    @Test
    fun testTotalRowsAndOverlap() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 4, 8, 4)
        )
        assertEquals(8, GridEngine.totalRows(cards))
        assertFalse(GridEngine.hasOverlap(cards))
        assertTrue(GridEngine.hasOverlap(cards + card("C", 4, 2, 8, 4)))
    }

    @Test
    fun testRandomOperationsPreserveInvariants() {
        val random = Random(2026L)
        repeat(200) { round ->
            val columns = if (round % 2 == 0) 8 else 12
            val cards = buildList {
                repeat(10) { index ->
                    val width = (2 + random.nextInt(3)) * 2
                    val height = (2 + random.nextInt(3)) * 2
                    val slot = GridEngine.findTopLeftFreeSlot(width, height, columns, this)
                    add(card("c$index", slot.x, slot.y, width, height))
                }
            }

            repeat(20) {
                val mover = cards[random.nextInt(cards.size)]
                val others = cards.filter { it.id != mover.id }
                val settled: List<CardRect> = if (random.nextBoolean()) {
                    val edge = ResizeEdge.entries[random.nextInt(ResizeEdge.entries.size)]
                    val pointer = when (edge) {
                        ResizeEdge.End -> IntOffset(mover.x + random.nextInt(columns * 2), mover.y + random.nextInt(4))
                        ResizeEdge.Start -> IntOffset(random.nextInt(columns + 8) - 4, mover.y + random.nextInt(4))
                        ResizeEdge.Bottom -> IntOffset(mover.x, mover.y + random.nextInt(30) - 5)
                        ResizeEdge.Top -> IntOffset(mover.x, random.nextInt(20) - 5)
                    }
                    val resized = GridEngine.resolveResize(mover, edge, pointer, columns, CardLimits.DEFAULT, others)
                    others.map { resized.pushed[it.id] ?: it } + resized.layout
                } else {

                    val target = IntOffset(random.nextInt(columns - mover.width + 1), random.nextInt(40))
                    val preview = mover.positionAt(target)
                    val pointer = IntOffset(
                        preview.x + random.nextInt(preview.width),
                        preview.y + random.nextInt(preview.height)
                    )
                    val displaced = GridEngine.resolveDisplacements(preview, columns, others, pointer)
                    val slot = displaced?.let { target }
                        ?: GridEngine.findNearestFreeSlot(preview.width, preview.height, target, columns, others)
                    val settledPreview = slot?.let(preview::positionAt) ?: preview
                    others.map { displaced?.get(it.id) ?: it } + settledPreview
                }
                assertFalse(
                    "Round $round: settled layout must not overlap: $settled",
                    GridEngine.hasOverlap(settled)
                )
                assertTrue(
                    "Round $round: settled layout must stay in grid: $settled",
                    settled.all { GridEngine.isInGrid(it, columns) }
                )
            }
        }
    }
}
