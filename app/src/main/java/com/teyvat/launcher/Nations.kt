package com.teyvat.launcher

import androidx.compose.ui.graphics.Color
import java.util.Calendar

data class Nation(
    val name: String,
    val element: String,
    val motto: String,
    val archon: String,
    val color: Color,
    val iconRes: Int,
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
        Nation("Mondstadt", "Anemo", "Freedom", "Barbatos", Color(0xFF4FD1B5), R.drawable.el_anemo),
        Nation("Liyue", "Geo", "Contracts", "Morax", Color(0xFFE3B04B), R.drawable.el_geo),
        Nation("Inazuma", "Electro", "Eternity", "Raiden Shogun", Color(0xFFB58CFF), R.drawable.el_electro),
        Nation("Sumeru", "Dendro", "Wisdom", "Nahida", Color(0xFF8FD14F), R.drawable.el_dendro),
        Nation("Fontaine", "Hydro", "Justice", "Focalors", Color(0xFF5DA9FF), R.drawable.el_hydro),
        Nation("Natlan", "Pyro", "War", "Mavuika", Color(0xFFFF7A5C), R.drawable.el_pyro),
        Nation("Snezhnaya", "Cryo", "Love", "The Tsaritsa", Color(0xFF9FE6F5), R.drawable.el_cryo),
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
