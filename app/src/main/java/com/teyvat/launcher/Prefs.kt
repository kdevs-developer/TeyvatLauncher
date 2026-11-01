package com.teyvat.launcher

import android.content.Context

/** Tiny SharedPreferences wrapper: launch history (for "recent apps") and the chosen nation. */
object Prefs {
    private const val FILE = "teyvat"
    private const val LAUNCH_PREFIX = "last_"
    private const val NATION = "nation_override"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recordLaunch(ctx: Context, pkg: String) {
        sp(ctx).edit().putLong(LAUNCH_PREFIX + pkg, System.currentTimeMillis()).apply()
    }

    /** package -> time (ms) it was last opened from this launcher. */
    fun launches(ctx: Context): Map<String, Long> {
        val out = HashMap<String, Long>()
        for ((key, value) in sp(ctx).all) {
            if (key.startsWith(LAUNCH_PREFIX) && value is Long) out[key.removePrefix(LAUNCH_PREFIX)] = value
        }
        return out
    }

    /** -1 = automatic (nation of the day), otherwise an index into Nations.all */
    fun nationOverride(ctx: Context): Int = sp(ctx).getInt(NATION, -1)

    fun setNationOverride(ctx: Context, index: Int) {
        sp(ctx).edit().putInt(NATION, index).apply()
    }
}
