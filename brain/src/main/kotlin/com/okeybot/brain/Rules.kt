package com.okeybot.brain

data class RulesConfig(
    /** Allow runs like 12-13-1 (a single wrap at the end). */
    val allowWrapRuns: Boolean = false
)

enum class MeldType { RUN, GROUP }

/**
 * One position of a meld. [tile] is the physical tile in the hand;
 * [color]/[number] is the identity it represents inside the meld.
 */
data class Slot(val tile: Tile, val color: TileColor, val number: Int, val isWild: Boolean)

data class Meld(val type: MeldType, val slots: List<Slot>) {
    override fun toString(): String =
        slots.joinToString(" ", "[", "]") { if (it.isWild) "*${it.color.code}${it.number}" else "${it.color.code}${it.number}" }
}

object Rules {
    fun isValidMeld(meld: Meld, config: RulesConfig = RulesConfig()): Boolean {
        if (meld.slots.none { !it.isWild }) return false
        return when (meld.type) {
            MeldType.RUN -> isValidRun(meld.slots, config)
            MeldType.GROUP -> isValidGroup(meld.slots)
        }
    }

    private fun isValidRun(slots: List<Slot>, config: RulesConfig): Boolean {
        if (slots.size !in 3..13) return false
        if (slots.any { it.color != slots[0].color }) return false
        for (i in 0 until slots.size - 1) {
            val a = slots[i].number
            val b = slots[i + 1].number
            val normal = b == a + 1
            val wrap = config.allowWrapRuns && a == 13 && b == 1 && i == slots.size - 2
            if (!normal && !wrap) return false
        }
        return true
    }

    private fun isValidGroup(slots: List<Slot>): Boolean {
        if (slots.size !in 3..4) return false
        if (slots.any { it.number != slots[0].number }) return false
        return slots.map { it.color }.toSet().size == slots.size
    }
}
