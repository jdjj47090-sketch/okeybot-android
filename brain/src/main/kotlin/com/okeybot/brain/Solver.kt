package com.okeybot.brain

data class Arrangement(val melds: List<Meld>, val leftovers: List<Tile>) {
    /** Number of tiles not part of any valid set. 0 means every tile is placed. */
    val unmatched: Int get() = leftovers.size
    val isComplete: Boolean get() = leftovers.isEmpty()

    /** Tiles in the order they should appear on the rack: melds separated by null gaps, then leftovers. */
    fun rackOrder(): List<Tile?> {
        val out = ArrayList<Tile?>()
        for (m in melds) {
            if (out.isNotEmpty()) out.add(null)
            m.slots.forEach { out.add(it.tile) }
        }
        if (leftovers.isNotEmpty()) {
            if (out.isNotEmpty()) out.add(null)
            out.addAll(leftovers)
        }
        return out
    }
}

/**
 * Finds the arrangement of a hand into valid melds that leaves the fewest tiles unmatched.
 * Exhaustive memoized backtracking over the 52 distinct (color, number) cells.
 */
class Solver(private val config: RulesConfig = RulesConfig()) {

    fun solve(hand: List<Tile>, ctx: OkeyContext): Arrangement {
        val counts = IntArray(52)
        var wilds = 0
        for (t in hand) {
            if (ctx.isWild(t)) {
                wilds++
            } else {
                val e = ctx.effective(t)
                counts[idx(e.color!!.ordinal, e.number)]++
            }
        }
        require(counts.all { it <= 3 }) { "Impossible hand: more than 3 identical tiles" }
        val search = Search(config, counts, wilds)
        search.solve()
        return search.build(ctx)
    }

    fun unmatchedCount(hand: List<Tile>, ctx: OkeyContext): Int = solve(hand, ctx).unmatched
}

private fun idx(colorOrdinal: Int, number: Int) = colorOrdinal * 13 + number - 1

private data class MSlot(val colorOrdinal: Int, val number: Int, val wild: Boolean)

/** Either a meld (type != null) or "leave tile [skip] unmatched". */
private class Move(val type: MeldType?, val slots: List<MSlot>, val skip: Int)

private data class Key(val a: Long, val b: Long, val wilds: Int, val hasSet: Boolean)

private class Search(val config: RulesConfig, val counts: IntArray, var wilds: Int) {
    var hasSet = false
    private val memo = HashMap<Key, Int>()
    private val best = HashMap<Key, Move>()
    private val colors = TileColor.values()

    private fun key(): Key {
        var a = 0L
        var b = 0L
        for (i in 0 until 26) a = a * 4 + counts[i]
        for (i in 26 until 52) b = b * 4 + counts[i]
        return Key(a, b, wilds, hasSet)
    }

    private fun firstIndex(): Int {
        for (i in 0 until 52) if (counts[i] > 0) return i
        return -1
    }

    private fun apply(mv: Move) {
        if (mv.skip >= 0) {
            counts[mv.skip]--
        } else {
            for (s in mv.slots) if (s.wild) wilds-- else counts[idx(s.colorOrdinal, s.number)]--
            hasSet = true
        }
    }

    private fun undo(mv: Move) {
        if (mv.skip >= 0) {
            counts[mv.skip]++
        } else {
            for (s in mv.slots) if (s.wild) wilds++ else counts[idx(s.colorOrdinal, s.number)]++
        }
    }

    /** Minimal cost = unmatched real tiles (+ stranded wilds if no meld exists at all). */
    fun solve(): Int {
        val first = firstIndex()
        if (first < 0) return if (hasSet) 0 else wilds
        val k = key()
        memo[k]?.let { return it }

        var bestCost = Int.MAX_VALUE
        var bestMove: Move? = null
        val prevHasSet = hasSet
        for (mv in moves(first)) {
            apply(mv)
            val cost = (if (mv.skip >= 0) 1 else 0) + solve()
            undo(mv)
            hasSet = prevHasSet
            if (cost < bestCost) {
                bestCost = cost
                bestMove = mv
            }
        }
        memo[k] = bestCost
        best[k] = bestMove!!
        return bestCost
    }

    private fun moves(first: Int): List<Move> {
        val out = ArrayList<Move>()
        val ci = first / 13
        val n = first % 13 + 1

        // Runs in which the first remaining tile is the lowest real tile.
        for (s in maxOf(1, n - wilds)..n) {
            var wildsUsed = n - s
            for (e in n..13) {
                if (e > n && counts[idx(ci, e)] == 0) wildsUsed++
                if (wildsUsed > wilds) break
                if (e - s + 1 >= 3) {
                    val slots = ArrayList<MSlot>()
                    for (p in s..e) {
                        val wild = p < n || (p > n && counts[idx(ci, p)] == 0)
                        slots.add(MSlot(ci, p, wild))
                    }
                    out.add(Move(MeldType.RUN, slots, -1))
                }
            }
        }

        // Wrap runs ...12-13-1 where this tile is the final "1".
        if (config.allowWrapRuns && n == 1) {
            for (s in 2..12) {
                var wildsUsed = 0
                val slots = ArrayList<MSlot>()
                for (p in s..13) {
                    val wild = counts[idx(ci, p)] == 0
                    if (wild) wildsUsed++
                    slots.add(MSlot(ci, p, wild))
                }
                if (wildsUsed > wilds) continue
                slots.add(MSlot(ci, 1, false))
                out.add(Move(MeldType.RUN, slots, -1))
            }
        }

        // Groups: this tile plus tiles of the same number in later colors, padded with wilds.
        val others = ((ci + 1)..3).filter { counts[idx(it, n)] > 0 }
        for (mask in 0 until (1 shl others.size)) {
            val chosen = others.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }
            for (target in 3..4) {
                val m = target - 1 - chosen.size
                if (m < 0 || m > wilds) continue
                val used = HashSet<Int>().apply { add(ci); addAll(chosen) }
                val free = (0..3).filter { it !in used }
                if (m > free.size) continue
                val slots = ArrayList<MSlot>()
                slots.add(MSlot(ci, n, false))
                chosen.forEach { slots.add(MSlot(it, n, false)) }
                free.take(m).forEach { slots.add(MSlot(it, n, true)) }
                slots.sortBy { it.colorOrdinal }
                out.add(Move(MeldType.GROUP, slots, -1))
            }
        }

        out.add(Move(null, emptyList(), first))
        return out
    }

    fun build(ctx: OkeyContext): Arrangement {
        val melds = ArrayList<Meld>()
        val left = ArrayList<Tile>()
        while (true) {
            val first = firstIndex()
            if (first < 0) break
            solve()
            val mv = best.getValue(key())
            apply(mv)
            if (mv.skip >= 0) {
                left.add(handTile(first / 13, first % 13 + 1, ctx))
            } else {
                melds.add(toMeld(mv, ctx))
            }
        }

        // Leftover wilds extend an existing meld whenever possible.
        val mutable = melds.toMutableList()
        var w = wilds
        while (w > 0 && attachWild(mutable, ctx)) w--
        repeat(w) { left.add(ctx.okey) }

        val sorted = mutable.sortedWith(
            compareBy<Meld>({ it.type.ordinal }, { it.slots[0].color.ordinal }, { it.slots[0].number })
        )
        left.sortWith(compareBy({ it.color?.ordinal ?: -1 }, { it.number }))
        return Arrangement(sorted, left)
    }

    private fun handTile(ci: Int, number: Int, ctx: OkeyContext): Tile {
        val t = Tile(colors[ci], number)
        return if (t == ctx.okey) Tile.FAKE_JOKER else t
    }

    private fun toMeld(mv: Move, ctx: OkeyContext): Meld = Meld(
        mv.type!!,
        mv.slots.map { s ->
            val c = colors[s.colorOrdinal]
            if (s.wild) Slot(ctx.okey, c, s.number, true) else Slot(handTile(s.colorOrdinal, s.number, ctx), c, s.number, false)
        }
    )

    private fun attachWild(melds: MutableList<Meld>, ctx: OkeyContext): Boolean {
        for (i in melds.indices) {
            val m = melds[i]
            if (m.type == MeldType.GROUP) {
                if (m.slots.size >= 4) continue
                val used = m.slots.map { it.color }.toSet()
                val c = colors.first { it !in used }
                melds[i] = Meld(m.type, m.slots + Slot(ctx.okey, c, m.slots[0].number, true))
                return true
            } else {
                val first = m.slots.first()
                val last = m.slots.last()
                val wraps = last.number == 1 && m.slots.size > 1
                if (!wraps && last.number < 13) {
                    melds[i] = Meld(m.type, m.slots + Slot(ctx.okey, last.color, last.number + 1, true))
                    return true
                }
                if (first.number > 1) {
                    melds[i] = Meld(m.type, listOf(Slot(ctx.okey, first.color, first.number - 1, true)) + m.slots)
                    return true
                }
            }
        }
        return false
    }
}
