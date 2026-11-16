package com.okeybot.brain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StrategyTest {
    /** Indicator K13 -> the Okey (wild) is K1. */
    private val ctx = OkeyContext(Tile.parse("K13"))
    private val strategy = Strategy()

    private fun hand(s: String) = Tile.parseAll(s)

    private val complete14 = "R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 K11 K12"

    @Test fun isWinningOnCompleteHand() {
        assertTrue(strategy.isWinning(hand(complete14), ctx))
    }

    @Test fun discardsTheStrayTileAndCanWin() {
        val choice = strategy.chooseDiscard(hand("$complete14 Y13"), ctx)
        assertEquals(Tile.parse("Y13"), choice.tile)
        assertTrue(choice.canWin)
    }

    @Test fun neverDiscardsTheWildWhenAnotherTileIsEquallyGood() {
        val choice = strategy.chooseDiscard(hand("R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 K1 Y13 Y2"), ctx)
        assertNotEquals(Tile.parse("K1"), choice.tile)
        assertEquals(1, choice.arrangement.unmatched)
    }

    @Test fun takesDiscardThatCompletesTheHand() {
        val h = hand("R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 K11 Y13")
        val d = strategy.decideDraw(h, Tile.parse("K12"), ctx)
        assertEquals(DrawSource.DISCARD, d.source)
    }

    @Test fun takesDiscardThatReducesUnmatchedTiles() {
        val h = hand("R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 Y13 Y2")
        val d = strategy.decideDraw(h, Tile.parse("K11"), ctx)
        assertEquals(DrawSource.DISCARD, d.source)
    }

    @Test fun drawsFromPileWhenDiscardIsUseless() {
        val h = hand("R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 K11 Y13")
        val d = strategy.decideDraw(h, Tile.parse("Y2"), ctx)
        assertEquals(DrawSource.PILE, d.source)
    }

    @Test fun drawsFromPileWhenDiscardPileIsEmpty() {
        assertEquals(DrawSource.PILE, strategy.decideDraw(hand(complete14), null, ctx).source)
    }

    @Test fun keepsConnectedLeftoversOverIsolatedOnes() {
        // Leftovers after the sets: Y2 Y3 and K12 K13 (connected) vs B1 (isolated). Discard the isolated tile.
        val h = hand("R5 R6 R7 B9 B10 B11 K4 K5 K6 Y2 Y3 B1 K12 K13")
        val choice = strategy.chooseDiscard(h, ctx)
        assertNotEquals(Tile.parse("Y2"), choice.tile)
        assertNotEquals(Tile.parse("Y3"), choice.tile)
    }
}
