package com.teyvat.launcher

import androidx.compose.ui.graphics.Color
import java.util.Calendar

data class Nation(
    val name: String,
    val element: String,
    val motto: String,
    val archon: String,
    val color: Color,
    val icon: List<String>,
)

enum class Phase {
    Morning, Afternoon, Evening, Night;

    companion object {
        fun of(now: Calendar, choice: String = Config.TIME_OF_DAY): Phase {
            entries.firstOrNull { it.name.equals(choice, ignoreCase = true) }?.let { return it }
            val h = now.get(Calendar.HOUR_OF_DAY)
            return when {
                h in 5..11 -> Morning
                h < 17 && h >= 12 -> Afternoon
                h in 17..20 -> Evening
                else -> Night
            }
        }
    }
}

object Nations {
    val all = listOf(
        Nation("Mondstadt", "Anemo", "Freedom", "Barbatos", Color(0xFF4FD1B5), Glyphs.ANEMO),
        Nation("Liyue", "Geo", "Contracts", "Morax", Color(0xFFE3B04B), Glyphs.GEO),
        Nation("Inazuma", "Electro", "Eternity", "Raiden Shogun", Color(0xFFB58CFF), Glyphs.ELECTRO),
        Nation("Sumeru", "Dendro", "Wisdom", "Nahida", Color(0xFF8FD14F), Glyphs.DENDRO),
        Nation("Fontaine", "Hydro", "Justice", "Focalors", Color(0xFF5DA9FF), Glyphs.HYDRO),
        Nation("Natlan", "Pyro", "War", "Mavuika", Color(0xFFFF7A5C), Glyphs.PYRO),
        Nation("Snezhnaya", "Cryo", "Love", "The Tsaritsa", Color(0xFF9FE6F5), Glyphs.CRYO),
    )

    private fun hash(c: Calendar): Long {
        val s = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        val x = s xor (s ushr 15)
        return (x * -2048144777).toUInt().toLong() // same maths as the web preview
    }

    /** Auto = deterministic "nation of the day", never the same as yesterday. */
    fun pick(now: Calendar, choice: String = Config.NATION_PICK): Nation {
        all.firstOrNull { it.name.equals(choice, ignoreCase = true) }?.let { return it }
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        var i = (hash(now) % 7).toInt()
        if (i == (hash(yesterday) % 7).toInt()) i = (i + 1) % 7
        return all[i]
    }
}
