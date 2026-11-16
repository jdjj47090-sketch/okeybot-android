package com.okeybot.brain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverTest {
    /** Indicator K13 -> the Okey (wild) is K1. */
    private val ctx = OkeyContext(Tile.parse("K13"))
    private val solver = Solver()
    private val wrapSolver = Solver(RulesConfig(allowWrapRuns = true))

    private fun hand(s: String) = Tile.parseAll(s)

    private fun assertConsistent(h: List<Tile>, arr: Arrangement, config: RulesConfig = RulesConfig()) {
        arr.melds.forEach { assertTrue(Rules.isValidMeld(it, config), "invalid meld $it for hand $h") }
        arr.melds.flatMap { it.slots }.filter { it.isWild }.forEach { assertEquals(ctx.okey, it.tile) }
        val used = (arr.melds.flatMap { m -> m.slots.map { it.tile } } + arr.leftovers).map { it.toString() }.sorted()
        assertEquals(h.map { it.toString() }.sorted(), used, "tiles must be conserved")
    }

    @Test fun completeHandOfFourSets() {
        val h = hand("R1 R2 R3 R4 B7 B8 B9 Y5 B5 R5 K9 K10 K11 K12")
        val arr = solver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        assertEquals(4, arr.melds.size)
        assertConsistent(h, arr)
    }

    @Test fun groupOfFourAndLeftover() {
        val h = hand("R5 B5 K5 Y5 R9 R10 R11 B1 B2 B3 Y8 Y9 Y10 K7")
        val arr = solver.solve(h, ctx)
        assertEquals(listOf(Tile.parse("K7")), arr.leftovers)
        assertConsistent(h, arr)
    }

    @Test fun wildFillsGapInRun() {
        val h = hand("R1 R3 K1 Y8 Y9 Y10 B2 B3 B4")
        val arr = solver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        assertTrue(arr.melds.any { m -> m.slots.any { it.isWild } })
        assertConsistent(h, arr)
    }

    @Test fun twoWildsWithOneTileMakeASet() {
        val h = hand("R7 K1 K1")
        val arr = solver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        assertConsistent(h, arr)
    }

    @Test fun leftoverWildExtendsAnExistingMeld() {
        val h = hand("R1 R2 R3 K1")
        val arr = solver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        assertEquals(1, arr.melds.size)
        assertEquals(4, arr.melds[0].slots.size)
        assertConsistent(h, arr)
    }

    @Test fun wildAloneWithNothingElseIsUnmatched() {
        val arr = solver.solve(hand("K1"), ctx)
        assertEquals(1, arr.unmatched)
    }

    @Test fun wildCompletesAGroup() {
        val h = hand("R9 B9 K1")
        assertEquals(0, solver.solve(h, ctx).unmatched)
    }

    @Test fun fakeJokerTakesTheOkeyIdentity() {
        // Okey is K1, so the fake joker acts as a normal K1: K1(FJ) K2 K3.
        val h = hand("K2 K3 FJ")
        val arr = solver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        val slot = arr.melds.single().slots.first { it.tile.isFakeJoker }
        assertEquals(false, slot.isWild)
        assertEquals(1, slot.number)
        assertConsistent(h, arr)
    }

    @Test fun fakeJokerIsNotAWildcard() {
        assertEquals(3, solver.solve(hand("R5 R9 FJ"), ctx).unmatched)
    }

    @Test fun realOkeyTileIsWildInsideRun() {
        // The real K1 is wild, so it can complete K2 K3 as well.
        assertEquals(0, solver.solve(hand("K1 K2 K3"), ctx).unmatched)
    }

    @Test fun wrapRunOnlyWithOption() {
        val h = hand("R12 R13 R1")
        assertEquals(3, solver.solve(h, ctx).unmatched)
        val arr = wrapSolver.solve(h, ctx)
        assertEquals(0, arr.unmatched)
        assertConsistent(h, arr, RulesConfig(allowWrapRuns = true))
    }

    @Test fun wrapRunWithWildInTheMiddle() {
        // R12 W(R13) R1 needs the wrap.
        val h = hand("R12 K1 R1")
        assertEquals(0, wrapSolver.solve(h, ctx).unmatched)
    }

    @Test fun sharedTileChoosesBestSplit() {
        val h = hand("R3 R4 R5 B5 K5 Y5")
        assertEquals(0, solver.solve(h, ctx).unmatched)
    }

    @Test fun doubleTileCanServeTwoSets() {
        val h = hand("R3 R4 R5 R5 B5 K5")
        assertEquals(0, solver.solve(h, ctx).unmatched)
    }

    @Test fun ambiguousChoiceLeavesTwo() {
        assertEquals(2, solver.solve(hand("R3 R4 R5 B5 K5"), ctx).unmatched)
    }

    @Test fun longRunOf13() {
        val h = Tile.parseAll((2..13).joinToString(" ") { "R$it" })
        assertEquals(0, solver.solve(h, ctx).unmatched)
    }

    @Test fun wrapNeverHurts() {
        val rnd = Random(7)
        repeat(60) {
            val h = randomHand(rnd, 14)
            val a = solver.solve(h, ctx).unmatched
            val b = wrapSolver.solve(h, ctx).unmatched
            assertTrue(b <= a, "wrap allowed must not be worse: $h")
        }
    }

    @Test fun randomHandsAlwaysProduceValidConsistentArrangements() {
        val rnd = Random(42)
        repeat(100) {
            val h = randomHand(rnd, if (it % 2 == 0) 14 else 15)
            assertConsistent(h, solver.solve(h, ctx))
            assertConsistent(h, wrapSolver.solve(h, ctx), RulesConfig(allowWrapRuns = true))
        }
    }

    @Test fun rackOrderSeparatesMeldsWithGaps() {
        val h = hand("R1 R2 R3 B5 K5 Y5 R9")
        val order = solver.solve(h, ctx).rackOrder()
        assertEquals(2, order.count { it == null })
        assertEquals(h.size + 2, order.size)
    }

    private fun randomHand(rnd: Random, n: Int): List<Tile> {
        val deck = Deck.full().shuffled(rnd).toMutableList()
        deck.remove(Tile.parse("K13")) // the indicator tile is not in a player's hand
        return deck.take(n)
    }
}
