package com.teyvat.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Parsed-path cache (SVG path strings -> Compose Path). */
object P {
    private val cache = HashMap<String, Path>()
    operator fun get(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }
}

/** SVG path data (24x24 grid) for every icon used by the launcher. */
object Glyphs {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0Z"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r " +
            "h${-(w - 2 * r)} a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r}Z"

    // Elements
    val ANEMO = listOf("M3 9h10a3 3 0 1 0 0 -3 M3 13h14a3 3 0 1 1 -3 3 M3 17h7")
    val GEO = listOf("M12 3l7 4v10l-7 4-7-4V7z", "M12 8l3 2v4l-3 2-3-2v-4z")
    val ELECTRO = listOf("M13 2L5 13h6l-1 9 8-12h-6z")
    val DENDRO = listOf("M5 19C5 10 10 5 20 4c0 10-5 15-14 15z", "M5 19L14 10")
    val HYDRO = listOf("M12 3C8 9 6 12 6 15a6 6 0 0 0 12 0c0-3-2-6-6-12z")
    val PYRO = listOf("M12 3c1 4 5 6 5 11a5 5 0 0 1 -10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-4 0-6 1-10z")
    val CRYO = listOf("M12 2v20M3.3 7l17.4 10M3.3 17L20.7 7")

    // UI
    val SEARCH = listOf(circle(11f, 11f, 7f), "M20 20l-4-4")
    val WIFI = listOf("M2 9a15 15 0 0 1 20 0 M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0", circle(12f, 19f, 1f))
    val SETTINGS = listOf(
        circle(12f, 12f, 3.5f), circle(12f, 12f, 7f),
        "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"
    )
    val INPUTS = listOf(rrect(3f, 6f, 18f, 12f, 2f), "M8 12h8M13 9l3 3-3 3")
    val BELL = listOf("M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    val SOUND = listOf("M4 9v6h4l5 4V5L8 9z", "M16.5 8.5a5 5 0 0 1 0 7")
    val DISPLAY = listOf(circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2")
    val BLUETOOTH = listOf("M7 8l10 8-5 4V4l5 4-10 8")
    val POWER = listOf("M12 3v8", "M6.5 6.5a8 8 0 1 0 11 0")

    /** Four-point star used as the Teyvat logo (100x100 grid). */
    const val STAR = "M50 0 C54 34 66 46 100 50 C66 54 54 66 50 100 C46 66 34 54 0 50 C34 46 46 34 50 0Z"
    const val STAR_BIG = "M50 14 C52 40 60 48 86 50 C60 52 52 60 50 86 C48 60 40 52 14 50 C40 48 48 40 50 14Z"
}

@Composable
fun SvgIcon(paths: List<String>, size: Dp, color: Color, strokeWidth: Float = 2f) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, Offset.Zero) {
            paths.forEach {
                drawPath(P[it], color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun StarMark(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 100f, Offset.Zero) { drawPath(P[Glyphs.STAR], color) }
    }
}
