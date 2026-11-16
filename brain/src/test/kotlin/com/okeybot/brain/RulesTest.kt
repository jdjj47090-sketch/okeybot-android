package com.okeybot.brain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    private fun meld(type: MeldType, vararg tiles: String, wildAt: Set<Int> = emptySet()): Meld =
        Meld(type, tiles.mapIndexed { i, s ->
            val t = Tile.parse(s)
            Slot(t, t.color!!, t.number, i in wildAt)
        })

    private val wrap = RulesConfig(allowWrapRuns = true)

    @Test fun deckHas106Tiles() {
        val deck = Deck.full()
        assertEquals(106, deck.size)
        assertEquals(2, deck.count { it.isFakeJoker })
        assertEquals(2, deck.count { it == Tile.parse("R7") })
    }

    @Test fun okeyIsIndicatorPlusOne() {
        assertEquals(Tile.parse("Y5"), OkeyContext(Tile.parse("Y4")).okey)
    }

    @Test fun okeyWrapsFrom13To1() {
        assertEquals(Tile.parse("R1"), OkeyContext(Tile.parse("R13")).okey)
    }

    @Test fun fakeJokerIsEffectivelyTheOkeyIdentityButNotWild() {
        val ctx = OkeyContext(Tile.parse("B8"))
        assertEquals(Tile.parse("B9"), ctx.effective(Tile.FAKE_JOKER))
        assertFalse(ctx.isWild(Tile.FAKE_JOKER))
        assertTrue(ctx.isWild(Tile.parse("B9")))
    }

    @Test fun validRun() = assertTrue(Rules.isValidMeld(meld(MeldType.RUN, "R3", "R4", "R5")))

    @Test fun runNeedsSameColor() = assertFalse(Rules.isValidMeld(meld(MeldType.RUN, "R3", "B4", "R5")))

    @Test fun runNeedsConsecutiveNumbers() = assertFalse(Rules.isValidMeld(meld(MeldType.RUN, "R3", "R5", "R6")))

    @Test fun runNeedsThreeTiles() = assertFalse(Rules.isValidMeld(meld(MeldType.RUN, "R3", "R4")))

    @Test fun wrapRunOnlyWhenEnabled() {
        val m = meld(MeldType.RUN, "R12", "R13", "R1")
        assertFalse(Rules.isValidMeld(m))
        assertTrue(Rules.isValidMeld(m, wrap))
    }

    @Test fun wrapRunCannotWrapTwice() {
        assertFalse(Rules.isValidMeld(meld(MeldType.RUN, "R13", "R1", "R2"), wrap))
    }

    @Test fun validGroups() {
        assertTrue(Rules.isValidMeld(meld(MeldType.GROUP, "R5", "B5", "K5")))
        assertTrue(Rules.isValidMeld(meld(MeldType.GROUP, "R5", "B5", "K5", "Y5")))
    }

    @Test fun groupNeedsDistinctColors() = assertFalse(Rules.isValidMeld(meld(MeldType.GROUP, "R5", "R5", "K5")))

    @Test fun groupCannotExceedFour() {
        val m = Meld(MeldType.GROUP, List(5) { Slot(Tile.parse("R5"), TileColor.values()[it % 4], 5, false) })
        assertFalse(Rules.isValidMeld(m))
    }

    @Test fun meldOfOnlyWildsIsInvalid() {
        assertFalse(Rules.isValidMeld(meld(MeldType.RUN, "R3", "R4", "R5", wildAt = setOf(0, 1, 2))))
    }
}
