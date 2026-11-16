package com.okeybot.brain

data class DragMove(val fromIndex: Int, val toIndex: Int, val tile: Tile)

/** Plans the minimum number of insertion drags for a fixed-slot rack (null is an empty slot). */
object ReorderPlanner {
    fun plan(current: List<Tile?>, target: List<Tile?>): List<DragMove> {
        require(current.size == target.size) { "Current rack and target must have the same number of slots" }
        require(current.filterNotNull().groupingBy { it }.eachCount() == target.filterNotNull().groupingBy { it }.eachCount()) {
            "Current rack and target must contain the same tiles"
        }
        val n = current.size
        // LCS identifies the largest set of rack items that can stay in relative order.
        val dp = Array(n + 1) { IntArray(n + 1) }
        for (i in n - 1 downTo 0) for (j in n - 1 downTo 0) {
            dp[i][j] = if (current[i] == target[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val keepTarget = BooleanArray(n)
        var i = 0; var j = 0
        while (i < n && j < n) {
            if (current[i] == target[j] && dp[i][j] == 1 + dp[i + 1][j + 1]) { keepTarget[j] = true; i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        val slots = current.toMutableList()
        val moves = mutableListOf<DragMove>()
        for (to in 0 until n) {
            val wanted = target[to]
            if (keepTarget[to] || slots[to] == wanted) continue
            val from = slots.indices.firstOrNull { it != to && slots[it] == wanted } ?: continue
            val dragged = slots[from]
            if (from > to) for (k in from downTo to + 1) slots[k] = slots[k - 1]
            else for (k in from until to) slots[k] = slots[k + 1]
            slots[to] = dragged
            if (wanted != null) moves += DragMove(from, to, wanted)
        }
        return moves
    }
}
