package com.okeybot.brain

enum class TileColor(val code: Char) {
    RED('R'), YELLOW('Y'), BLUE('B'), BLACK('K');

    companion object {
        fun fromCode(c: Char): TileColor =
            values().firstOrNull { it.code == c.uppercaseChar() } ?: error("Unknown color code '$c'")
    }
}

/** A physical tile. The fake joker has color == null and number == 0. */
data class Tile(val color: TileColor?, val number: Int) {
    init {
        require(if (color == null) number == 0 else number in 1..13) { "Invalid tile $color/$number" }
    }

    val isFakeJoker: Boolean get() = color == null

    override fun toString(): String = if (color == null) "FJ" else "${color.code}$number"

    companion object {
        val FAKE_JOKER = Tile(null, 0)

        /** "R5", "k13", "FJ". */
        fun parse(s: String): Tile {
            val t = s.trim()
            if (t.equals("FJ", ignoreCase = true)) return FAKE_JOKER
            return Tile(TileColor.fromCode(t[0]), t.substring(1).toInt())
        }

        /** "R1 R2 R3 FJ" -> list of tiles. */
        fun parseAll(s: String): List<Tile> =
            s.split(Regex("\\s+")).filter { it.isNotBlank() }.map(::parse)
    }
}

object Deck {
    /** 106 tiles: 4 colors x 1..13 x 2 + 2 fake jokers. */
    fun full(): List<Tile> {
        val tiles = ArrayList<Tile>(106)
        repeat(2) {
            for (c in TileColor.values()) for (n in 1..13) tiles.add(Tile(c, n))
        }
        tiles.add(Tile.FAKE_JOKER)
        tiles.add(Tile.FAKE_JOKER)
        return tiles
    }
}

/**
 * Round context. The Okey (wild) identity is the indicator's color, number + 1 (13 wraps to 1).
 * Real tiles with that identity are WILD. The fake joker stands for the Okey identity as an
 * ordinary (non-wild) tile.
 */
data class OkeyContext(val indicator: Tile) {
    init {
        require(!indicator.isFakeJoker) { "Indicator cannot be a fake joker" }
    }

    val okey: Tile = Tile(indicator.color, indicator.number % 13 + 1)

    fun isWild(tile: Tile): Boolean = tile == okey

    /** The identity (color, number) a tile represents when used as a normal tile. */
    fun effective(tile: Tile): Tile = if (tile.isFakeJoker) okey else tile
}
