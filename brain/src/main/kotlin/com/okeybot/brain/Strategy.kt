package com.okeybot.brain

enum class DrawSource { PILE, DISCARD }

data class DrawDecision(val source: DrawSource, val reason: String)

data class DiscardChoice(
    val tile: Tile,
    /** Best arrangement of the 14 tiles that remain after discarding [tile]. */
    val arrangement: Arrangement
) {
    val canWin: Boolean get() = arrangement.isComplete
}

class Strategy(private val solver: Solver = Solver()) {

    /** hand15 = 14 tiles + the tile just drawn (or the starter's 15). */
    fun chooseDiscard(hand15: List<Tile>, ctx: OkeyContext): DiscardChoice {
        require(hand15.isNotEmpty()) { "Empty hand" }
        var bestChoice: DiscardChoice? = null
        var bestKey: Triple<Int, Int, Int>? = null
        for (candidate in hand15.distinct()) {
            val rest = hand15.toMutableList().also { it.remove(candidate) }
            val arr = solver.solve(rest, ctx)
            val key = Triple(
                arr.unmatched,
                if (ctx.isWild(candidate)) 1 else 0, // never throw the wild unless forced
                -potential(arr.leftovers, ctx)
            )
            if (bestKey == null || compare(key, bestKey) < 0) {
                bestKey = key
                bestChoice = DiscardChoice(candidate, arr)
            }
        }
        return bestChoice!!
    }

    /** Take the visible discard only if it strictly reduces the number of unmatched tiles. */
    fun decideDraw(hand14: List<Tile>, topDiscard: Tile?, ctx: OkeyContext): DrawDecision {
        if (topDiscard == null) return DrawDecision(DrawSource.PILE, "discard pile empty")
        val before = solver.solve(hand14, ctx).unmatched
        val choice = chooseDiscard(hand14 + topDiscard, ctx)
        val after = choice.arrangement.unmatched
        return when {
            choice.canWin -> DrawDecision(DrawSource.DISCARD, "discard $topDiscard completes the hand")
            after < before -> DrawDecision(DrawSource.DISCARD, "discard $topDiscard: unmatched $before -> $after")
            else -> DrawDecision(DrawSource.PILE, "discard $topDiscard does not help (unmatched $before)")
        }
    }

    /** True if the 14 tiles are all in valid sets. */
    fun isWinning(hand14: List<Tile>, ctx: OkeyContext): Boolean = solver.solve(hand14, ctx).isComplete

    private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }

    /** How many pairs among the leftovers are one tile away from a set (near-run or same-number pair). */
    private fun potential(leftovers: List<Tile>, ctx: OkeyContext): Int {
        val tiles = leftovers.filter { !ctx.isWild(it) }.map { ctx.effective(it) }
        var score = 0
        for (i in tiles.indices) for (j in i + 1 until tiles.size) {
            val a = tiles[i]
            val b = tiles[j]
            if (a.color == b.color) {
                val d = Math.abs(a.number - b.number)
                if (d == 1) score += 2 else if (d == 2) score += 1
            } else if (a.number == b.number) {
                score += 2
            }
        }
        return score
    }
}
