package com.okeybot.brain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReorderPlannerTest {
    @Test fun plansInsertionMovesAndPreservesGapSlots() {
        val a = Tile.parse("R1"); val b = Tile.parse("R2"); val c = Tile.parse("R3")
        val moves = ReorderPlanner.plan(listOf(a, b, null, c), listOf(b, null, a, c))
        assertEquals(1, moves.size)
    }
    @Test fun rotationUsesOnlyOneDrag() {
        val a = Tile.parse("R1"); val b = Tile.parse("R2"); val c = Tile.parse("R3")
        assertEquals(listOf(DragMove(0, 2, a)), ReorderPlanner.plan(listOf(a,b,c), listOf(b,c,a)))
    }
    @Test fun alreadyOrderedRackNeedsNoMoves() {
        val rack = listOf(Tile.parse("R1"), null, Tile.parse("B2"))
        assertEquals(emptyList(), ReorderPlanner.plan(rack, rack))
    }
}
