package com.teyvat.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Circle that grows out of a point (given as fractions of the screen) and reveals what is inside it. */
private class RevealShape(private val progress: Float, private val fx: Float, private val fy: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width * fx
        val cy = size.height * fy
        val dx = max(cx, size.width - cx)
        val dy = max(cy, size.height - cy)
        val r = sqrt(dx * dx + dy * dy) * progress
        return Outline.Generic(Path().apply { addOval(Rect(Offset(cx, cy), r)) })
    }
}

/**
 * The scene for the chosen nation. When the nation changes, the new scene grows out of the top bar
 * (where the nation button is) as a circle over the old one.
 */
@Composable
fun NationScene(nation: Nation, phase: Phase) {
    var shown by remember { mutableStateOf(nation) }
    var previous by remember { mutableStateOf<Nation?>(null) }
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(nation) {
        if (nation != shown) {
            previous = shown
            shown = nation
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            previous = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        previous?.let { SceneBackground(it, phase) }
        SceneBackground(
            shown, phase,
            Modifier.graphicsLayer {
                val p = reveal.value
                if (p < 1f) {
                    shape = RevealShape(p, 0.66f, 0.07f)
                    clip = true
                } else {
                    shape = RectangleShape
                    clip = false
                }
            },
        )
    }
}

private class Mote(val x: Float, val r: Float, val cycles: Int, val sway: Int, val phase: Float, val warm: Boolean)

/** Slow glowing motes drifting upward in the nation colour - sits between the scene and the UI. */
@Composable
fun Particles(color: Color) {
    val motes = remember {
        val rnd = java.util.Random(11)
        List(36) {
            Mote(rnd.nextFloat(), 1.6f + rnd.nextFloat() * 3.6f, 1 + rnd.nextInt(3), 1 + rnd.nextInt(3), rnd.nextFloat(), rnd.nextInt(4) == 0)
        }
    }
    val inf = rememberInfiniteTransition(label = "motes")
    val t by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        val unit = size.width / 1920f
        motes.forEach { m ->
            val prog = (t * m.cycles + m.phase) % 1f
            val swayX = sin(2f * PI.toFloat() * (t * m.sway + m.phase)) * 28f * unit
            val x = m.x * size.width + swayX
            val y = size.height * (1.05f - 1.10f * prog)
            val a = sin(PI.toFloat() * prog) * 0.65f
            val c = if (m.warm) Color(0xFFFFE9B0) else color
            drawCircle(c.copy(alpha = a * 0.25f), m.r * 3.2f * unit, Offset(x, y))
            drawCircle(c.copy(alpha = a), m.r * unit, Offset(x, y))
        }
    }
}

/** All scenes are drawn on the same 1920x1080 grid as the web design, then scaled to the screen. */
@Composable
fun SceneBackground(nation: Nation, phase: Phase, modifier: Modifier = Modifier) {
    val clock = rememberInfiniteTransition(label = "scene")
    val secs = clock.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Restart),
        label = "secs",
    )
    Canvas(modifier.fillMaxSize()) {
        val sec = secs.value
        scale(size.width / 1920f, size.height / 1080f, Offset.Zero) {
            when (nation.name) {
                "Mondstadt" -> mondstadt(sec)
                "Liyue" -> liyue(sec)
                "Inazuma" -> inazuma(sec)
                "Sumeru" -> sumeru(sec)
                "Fontaine" -> fontaine(sec)
                "Natlan" -> natlan(sec)
                else -> snezhnaya(sec)
            }
//            sky(phase, sec)
        }
    }
}

// ---------- tiny drawing helpers ----------
private fun osc(sec: Float, period: Float) = 0.5f - 0.5f * cos(2f * PI.toFloat() * sec / period)
private fun c(hex: Long) = Color(0xFF000000 or hex)

private fun keyframes(p: Float, times: FloatArray, vals: FloatArray): Float {
    for (i in 1 until times.size) if (p <= times[i]) {
        val f = (p - times[i - 1]) / (times[i] - times[i - 1])
        return vals[i - 1] + (vals[i] - vals[i - 1]) * f
    }
    return vals.last()
}

private fun DrawScope.vgrad(top: Long, bottom: Long) =
    drawRect(Brush.verticalGradient(listOf(c(top), c(bottom)), startY = 0f, endY = 1080f), size = Size(1920f, 1080f))

private fun DrawScope.fillP(d: String, color: Color, a: Float = 1f) = drawPath(P[d], color, alpha = a)
private fun DrawScope.strokeP(d: String, color: Color, w: Float, a: Float = 1f) =
    drawPath(P[d], color, alpha = a, style = Stroke(w, cap = StrokeCap.Round))
private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, color: Color, a: Float = 1f) =
    drawRect(color, Offset(x, y), Size(w, h), alpha = a)
private fun DrawScope.circ(x: Float, y: Float, r: Float, color: Color, a: Float = 1f) =
    drawCircle(color, r, Offset(x, y), alpha = a)
private fun DrawScope.oval(x: Float, y: Float, rx: Float, ry: Float, color: Color, a: Float = 1f) =
    drawOval(color, Offset(x - rx, y - ry), Size(2 * rx, 2 * ry), alpha = a)

// ---------- nations ----------
private fun DrawScope.mondstadt(sec: Float) {
    vgrad(0x2B6CA3, 0xA9DCE0)
    circ(1500f, 250f, 90f, c(0xFFF3C4), 0.85f)
    fillP("M0 700 L220 460 L360 580 L560 380 L760 620 L900 520 L1100 670 L1300 470 L1500 640 L1720 450 L1920 620 V1080 H0Z", c(0x6E97B8))
    fillP("M560 380 L610 450 L585 440 L560 470 L535 440 L510 450Z M1300 470 L1345 530 L1320 522 L1300 548 L1280 522 L1255 530Z", c(0xEAF4F8))
    fillP("M0 840 Q300 720 640 810 T1300 790 T1920 820 V1080 H0Z", c(0x4FA073))
    fillP("M0 950 Q400 870 900 940 T1920 910 V1080 H0Z", c(0x2F7D5B))
    translate(1700f, 640f) {
        fillP("M-26 130 L-16 0 H16 L26 130Z", c(0xE8D9B5))
        fillP("M-24 0 L0 -36 L24 0Z", c(0xC9553F))
        rotate(360f * (sec % 40f) / 40f, Offset(0f, -8f)) {
            strokeP("M0 -8 L-80 -80 M0 -8 L80 -80 M0 -8 L-80 80 M0 -8 L80 80", c(0xF3EBDD), 8f)
        }
    }
}

private fun DrawScope.liyue(sec: Float) {
    vgrad(0x3A2A55, 0xE6A95E)
    circ(1400f, 430f, 120f + 10f * osc(sec, 10f), c(0xFFD27A), 0.9f)
    fillP("M300 1080 L340 380 Q370 330 410 380 L470 1080Z", c(0x4C6E73))
    fillP("M900 1080 L950 300 Q990 240 1040 300 L1110 1080Z", c(0x3A5A62))
    fillP("M1600 1080 L1640 450 Q1670 410 1710 450 L1780 1080Z", c(0x4C6E73))
    rect(0f, 830f, 1920f, 250f, c(0x2C5C70))
    translate(120f * osc(sec, 15f), 0f) {
        strokeP("M-240 880 Q0 860 240 880 T720 880 T1200 880 T1680 880 T2160 880", c(0x8FC3C8), 4f)
    }
    translate(1180f, 760f) {
        rect(-70f, 0f, 140f, 70f, c(0x8C3B2E))
        fillP("M-130 6 Q0 -80 130 6Z", c(0xC9553F))
        strokeP("M-130 6 Q0 -80 130 6", c(0xE3B04B), 6f)
    }
}

private fun DrawScope.inazuma(sec: Float) {
    vgrad(0x1A0F33, 0x6B3FA0)
    circ(1450f, 300f, 120f, c(0xF2E8FF), 0.9f)
    val flash = keyframes(
        (sec % 10f) / 10f,
        floatArrayOf(0f, 0.8f, 0.82f, 0.85f, 0.87f, 0.9f, 1f),
        floatArrayOf(0f, 0f, 0.9f, 0f, 0.6f, 0f, 0f),
    )
    if (flash > 0.01f) strokeP("M1000 0 L940 220 L1010 220 L900 480", c(0xE5D2FF), 8f, flash)
    rect(0f, 880f, 1920f, 200f, c(0x1E1440))
    fillP("M1000 900 Q1300 760 1600 900Z", c(0x2A1A4A))
    translate(520f, 560f) {
        val red = c(0xC23B3B)
        rect(-120f, 0f, 30f, 320f, red); rect(90f, 0f, 30f, 320f, red)
        rect(-175f, -30f, 350f, 34f, red); rect(-135f, 70f, 270f, 20f, red)
    }
    val o = osc(sec, 15f)
    translate(-40f * o, 50f * o) {
        val pink = c(0xF5B6D6)
        circ(300f, 420f, 6f, pink); circ(700f, 650f, 5f, pink); circ(1100f, 380f, 6f, pink)
        circ(1700f, 700f, 5f, pink); circ(1300f, 560f, 4f, pink)
    }
}

private fun DrawScope.sumeru(sec: Float) {
    vgrad(0x0F3A2C, 0x7CC47A)
    fillP("M1100 0 L1300 0 L900 1080 L700 1080Z M1500 0 L1620 0 L1300 1080 L1180 1080Z", c(0xE8F5B0), 0.10f + 0.14f * osc(sec, 10f))
    val bark = c(0x3B2A1E)
    rect(180f, 0f, 120f, 1080f, bark); rect(780f, 0f, 90f, 1080f, bark); rect(1560f, 0f, 130f, 1080f, bark)
    oval(240f, 120f, 330f, 170f, c(0x2F8F4E)); oval(830f, 80f, 280f, 150f, c(0x2A7F45)); oval(1620f, 120f, 340f, 170f, c(0x2F8F4E))
    fillP("M0 900 Q500 820 1000 900 T1920 880 V1080 H0Z", c(0x1F5A3A))
    rect(1000f, 760f, 24f, 140f, c(0xE8D9B5))
    oval(1012f, 760f, 110f, 60f, c(0xF4A6C0), 1f - 0.35f * osc(sec, 6f))
    rect(420f, 800f, 18f, 100f, c(0xE8D9B5))
    oval(429f, 800f, 80f, 44f, c(0x7FD6F5))
}

private fun DrawScope.fontaine(sec: Float) {
    vgrad(0x1B3A6B, 0x9CCBEA)
    translate(80f * osc(sec, 60f), 0f) { oval(400f, 260f, 220f, 50f, Color.White, 0.4f) }
    translate(-70f * osc(sec, 60f), 0f) { oval(1500f, 200f, 260f, 56f, Color.White, 0.35f) }
    fillP("M1100 800 Q1300 540 1500 800Z", c(0xE9E2D0))
    strokeP("M1100 800 Q1300 540 1500 800Z", c(0xE3C27A), 6f)
    rect(1000f, 620f, 60f, 180f, c(0xD9D2C0)); fillP("M990 620 L1030 520 L1070 620Z", c(0xE3C27A))
    rect(1560f, 600f, 60f, 200f, c(0xD9D2C0)); fillP("M1550 600 L1590 500 L1630 600Z", c(0xE3C27A))
    rect(0f, 800f, 1920f, 280f, c(0x2F6FA8))
    translate(-80f * osc(sec, 15f), 0f) {
        strokeP("M0 860 Q200 840 400 860 T800 860 T1200 860 T1600 860 T2000 860 M0 960 Q200 940 400 960 T800 960 T1200 960 T1600 960 T2000 960", c(0x8FC3E8), 4f)
    }
}

private fun DrawScope.natlan(sec: Float) {
    vgrad(0x2A0E10, 0xFF7A3C)
    oval(960f, 200f, 420f, 90f, c(0x1A0A0C), 0.55f)
    fillP("M0 1080 L0 800 L300 640 L560 820 L760 560 L1160 560 L1360 820 L1620 640 L1920 800 V1080Z", c(0x3B1A16))
    fillP("M500 1080 L880 400 L1000 360 L1120 400 L1500 1080Z", c(0x2B1512))
    fillP("M880 400 L1000 360 L1120 400 L1060 420 L940 420Z", c(0xFF7A3C), 0.7f + 0.3f * osc(sec, 5f))
    strokeP("M960 420 L930 620 L970 800 L940 1000 M1040 420 L1070 640 L1030 820", c(0xFF7A3C), 10f)
    val p = (sec % 10f) / 10f
    val ember = c(0xFFB070)
    val a = sin(PI.toFloat() * p)
    translate(0f, 40f - 160f * p) {
        circ(900f, 300f, 5f, ember, a); circ(1040f, 260f, 4f, ember, a)
        circ(980f, 200f, 6f, ember, a); circ(1160f, 340f, 4f, ember, a)
    }
}

private fun DrawScope.snezhnaya(sec: Float) {
    vgrad(0x0B1D33, 0x5B9AC0)
    translate(0f, 24f * osc(sec, 15f)) {
        strokeP("M0 300 Q400 180 800 320 T1600 260 T2000 300", c(0x7CF5C8), 90f, 0.25f + 0.25f * osc(sec, 10f))
    }
    fillP("M0 760 L260 460 L480 700 L760 400 L1040 720 L1300 480 L1560 700 L1760 520 L1920 680 V1080 H0Z", c(0xB8D4E6))
    fillP("M0 900 Q500 820 1000 900 T1920 880 V1080 H0Z", c(0xEAF4F8))
    val tower = c(0x8FB0C8)
    rect(1380f, 620f, 50f, 160f, tower); fillP("M1370 620 L1405 520 L1440 620Z", tower)
    rect(1480f, 660f, 40f, 120f, tower); fillP("M1472 660 L1500 584 L1528 660Z", tower)
    val p = (sec % 12f) / 12f
    translate(0f, -20f + 90f * p) {
        circ(300f, 200f, 4f, Color.White, 0.8f); circ(700f, 140f, 3f, Color.White, 0.8f)
        circ(1100f, 100f, 4f, Color.White, 0.8f); circ(1700f, 180f, 3f, Color.White, 0.8f)
        circ(500f, 640f, 3f, Color.White, 0.8f)
    }
}

// ---------- time-of-day glow, tint and legibility gradient ----------
private fun DrawScope.glow(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), Offset(cx, cy), r),
        r, Offset(cx, cy),
    )
}

private fun DrawScope.sky(phase: Phase, sec: Float) {
    when (phase) {
        Phase.Morning -> {
            glow(1500f, 760f, 380f + 30f * osc(sec, 8f), c(0xFFE2B0), 0.95f)
        }
        Phase.Evening -> {
            glow(420f, 780f, 400f, c(0xFF9A5C), 0.9f)
        }
        Phase.Night -> {
            val tw = 0.5f + 0.5f * osc(sec, 5f)
            listOf(
                200f to 120f, 520f to 260f, 840f to 90f, 1120f to 300f,
                1700f to 110f, 1800f to 380f, 360f to 420f, 980f to 460f,
            ).forEachIndexed { i, (x, y) -> circ(x, y, if (i % 3 == 1) 2.5f else 2.5f + (i % 2) * 0.5f, Color.White, tw) }
        }
        Phase.Afternoon -> Unit
    }
    val tint = when (phase) {
        Phase.Morning -> Color(0x4DFFC496)
        Phase.Evening -> Color(0x59FF6E46)
        Phase.Night -> Color(0x9E080C32)
        Phase.Afternoon -> Color.Transparent
    }
    drawRect(tint, size = Size(1920f, 1080f))
    drawRect(
        Brush.verticalGradient(
            0f to Color(0x99 shl 24 or 0x12172B), 0.45f to Color(0x33 shl 24 or 0x12172B), 1f to Color(0xA6 shl 24 or 0x12172B),
            startY = 0f, endY = 1080f,
        ),
        size = Size(1920f, 1080f),
    )
}
