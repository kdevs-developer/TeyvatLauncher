package com.teyvat.launcher

import androidx.compose.ui.graphics.Color

/** Easy knobs. Edit and rebuild. */
object Config {
    /** "Auto" = a different nation each day, or one of:
     *  Mondstadt, Liyue, Inazuma, Sumeru, Fontaine, Natlan, Snezhnaya
     *  (Clicking the nation button in the top bar overrides this and is remembered.) */
    const val NATION_PICK = "Auto"

    /** "Auto" (follows the clock) or Morning, Afternoon, Evening, Night */
    const val TIME_OF_DAY = "Auto"

    val ACCENT = Color(0xFFE3C27A)

    /** How many recently used apps are pinned to the start of the row. */
    const val RECENT_COUNT = 6

    /** Any app whose package name or label contains one of these words (any case) is never listed.
     *  Add more words here to hide more apps. */
    val HIDDEN_WORDS = listOf("factorymenu", "factory menu", "factory_menu")
}
