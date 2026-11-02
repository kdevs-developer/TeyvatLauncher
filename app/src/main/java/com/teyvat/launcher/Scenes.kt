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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
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
    val bg = ImageBitmap.imageResource(sceneRes(nation.name))
    Canvas(modifier.fillMaxSize()) {
        val sec = secs.value
        scale(size.width / 1920f, size.height / 1080f, Offset.Zero) {
            drawImage(bg, dstSize = IntSize(1920, 1080), filterQuality = FilterQuality.High)
            when (nation.name) {
                "Mondstadt" -> mondstadt(sec)
                "Liyue" -> liyue(sec)
                "Inazuma" -> inazuma(sec)
                "Sumeru" -> sumeru(sec)
                "Fontaine" -> fontaine(sec)
                "Natlan" -> natlan(sec)
                else -> snezhnaya(sec)
            }
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

// ---------- shared animated bits ----------
private class Bit(val x: Float, val y: Float, val r: Float, val ph: Float, val k: Int)

private fun bits(n: Int, seed: Long, kMax: Int = 2): List<Bit> {
    val rnd = java.util.Random(seed)
    return List(n) { Bit(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat(), 1 + rnd.nextInt(kMax)) }
}

private val dandelions = bits(9, 3, 1)
private val petals = bits(22, 21, 2)
private val skyLanterns = bits(7, 8, 1)
private val fireflies = bits(24, 5, 2)
private val embers = bits(28, 13, 3)
private val flakes = bits(56, 17, 3)
private val stars = bits(18, 31, 1)
private val sparkles = bits(14, 29, 1)

// Every loop divides the 120 s clock, so nothing jumps when it restarts.
private fun cyc(sec: Float, period: Float, k: Int, ph: Float) = (sec / period * k + ph) % 1f
private fun wob(sec: Float, period: Float, k: Int, ph: Float) = sin(2f * PI.toFloat() * (sec / period * k + ph))

private fun DrawScope.cloud(x: Float, y: Float, s: Float, a: Float, color: Color = Color.White) {
    oval(x, y, 90f * s, 28f * s, color, a)
    oval(x - 60f * s, y + 8f * s, 60f * s, 22f * s, color, a)
    oval(x + 64f * s, y + 10f * s, 70f * s, 22f * s, color, a)
    oval(x + 10f * s, y - 18f * s, 56f * s, 24f * s, color, a)
}

// ---------- animated layers on top of the painted backgrounds ----------
// Each nation's still artwork (drawable-nodpi/scene_*.webp) is painted once; everything that moves
// (clouds, petals, snow, lanterns, sparkles, spinning sails, lightning ...) is drawn here on top.

private val starField = bits(120, 61, 1)
private val glints = bits(34, 41, 1)
private val skyMotes = bits(26, 77, 1)
private val liyueLanterns = bits(14, 9, 1)
private val electroMotes = bits(22, 23, 1)

private val bubbleSpots = listOf(
    Triple(560f, 560f, 34f), Triple(640f, 470f, 20f), Triple(1560f, 700f, 28f), Triple(1700f, 620f, 16f),
    Triple(260f, 300f, 22f), Triple(1840f, 860f, 24f), Triple(120f, 640f, 14f), Triple(900f, 300f, 18f),
)

/** Soft cloud: four overlapping ellipses drawn as ONE path so the overlaps don't get darker. */
private fun DrawScope.cloud2(x: Float, y: Float, k: Float, a: Float, color: Color = Color.White) {
    fun o(cx: Float, cy: Float, rx: Float, ry: Float) = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
    val p = Path().apply {
        addOval(o(x, y, 150f * k, 34f * k))
        addOval(o(x - 90f * k, y + 10f * k, 100f * k, 26f * k))
        addOval(o(x + 100f * k, y + 12f * k, 120f * k, 26f * k))
        addOval(o(x + 10f * k, y - 22f * k, 90f * k, 30f * k))
    }
    drawPath(p, color, alpha = a)
}

/** Slow sideways drift that loops cleanly with the 120 s clock. */
private fun drift(sec: Float, x: Float, range: Float, ph: Float) = x + range * wob(sec, 60f, 1, ph)

private fun DrawScope.twinkle(list: List<Bit>, x0: Float, w: Float, y0: Float, h: Float, color: Color, r0: Float, rs: Float, sec: Float, halo: Boolean) {
    for (b in list) {
        val x = x0 + b.x * w
        val y = y0 + b.y * h
        val a = 0.3f + 0.6f * (0.5f + 0.5f * wob(sec, 4f, 1, b.ph))
        if (halo) circ(x, y, (r0 + b.r * rs) * 3f, color, 0.14f)
        circ(x, y, r0 + b.r * rs, color, a)
    }
}

private fun DrawScope.risingMotes(list: List<Bit>, sec: Float, period: Float, x0: Float, w: Float, yBase: Float, rise: Float, color: Color, core: Color) {
    for (b in list) {
        val p = cyc(sec, period, b.k, b.ph)
        val x = x0 + b.x * w + 40f * wob(sec, 6f, 1, b.ph)
        val y = yBase - p * rise
        val a = sin(PI.toFloat() * p)
        glow(x, y, 20f + b.r * 14f, color, a * 0.7f)
        circ(x, y, 2.5f + b.r * 2f, core, a)
    }
}

private fun DrawScope.mondstadt(sec: Float) {
    cloud2(drift(sec, 300f, 70f, 0f), 200f, 1.3f, 0.75f)
    cloud2(drift(sec, 980f, 90f, 0.3f), 130f, 1.0f, 0.6f)
    cloud2(drift(sec, 1600f, 60f, 0.6f), 330f, 1.1f, 0.55f)
    cloud2(drift(sec, 700f, 80f, 0.8f), 400f, 0.8f, 0.5f)
    // windmill sails turning
    for ((x, y, k) in listOf(Triple(560f, 800f, 0.9f), Triple(1620f, 760f, 0.75f))) {
        translate(x, y - 250f * k) {
            scale(k, Offset.Zero) {
                rotate(360f * sec / 20f, Offset.Zero) {
                    for (q in 0 until 4) {
                        rotate(20f + q * 90f, Offset.Zero) {
                            rect(-4f, -150f, 8f, 150f, c(0x8A6B4A))
                            fillP("M4 -146 L50 -146 L50 -60 L4 -60Z", c(0xFFF8E6), 0.95f)
                            strokeP("M4 -103 H50 M27 -146 V-60", c(0xC9B793), 2f)
                        }
                    }
                }
                circ(0f, 0f, 9f, c(0x8A6B4A))
            }
        }
    }
    // dandelion seeds drifting up
    for (b in dandelions) {
        val p = cyc(sec, 24f, 1, b.ph)
        val x = 100f + b.x * 1700f + 150f * p + 20f * wob(sec, 6f, 1, b.ph)
        val y = 900f - p * 760f
        val a = sin(PI.toFloat() * p)
        val r = 7f + b.r * 4f
        circ(x, y, r * 2.4f, Color.White, a * 0.12f)
        for (i in 0 until 12) {
            val ang = i * PI.toFloat() / 6f
            val ex = x + cos(ang) * r * 1.6f
            val ey = y + sin(ang) * r * 1.6f
            drawLine(Color.White, Offset(x, y), Offset(ex, ey), 1.5f, alpha = a * 0.9f)
            circ(ex, ey, 1.8f, Color.White, a * 0.9f)
        }
        circ(x, y, 3.5f, Color.White, a)
    }
}

private fun DrawScope.liyue(sec: Float) {
    cloud2(drift(sec, 380f, 70f, 0f), 260f, 1.5f, 0.45f, c(0xFFB48A))
    cloud2(drift(sec, 1180f, 80f, 0.3f), 180f, 1.2f, 0.4f, c(0xFF9A7A))
    cloud2(drift(sec, 1650f, 60f, 0.6f), 380f, 1.1f, 0.4f, c(0xFFB48A))
    cloud2(drift(sec, 700f, 80f, 0.8f), 520f, 0.9f, 0.35f, c(0xFFC79A))
    // sky lanterns rising
    for (b in liyueLanterns) {
        val p = cyc(sec, 20f, 1, b.ph)
        val x = 60f + b.x * 1800f + 25f * wob(sec, 10f, 1, b.ph)
        val y = 900f - p * 860f
        val a = sin(PI.toFloat() * p)
        val r = 9f + b.r * 6f
        glow(x, y, r * 4f, c(0xFFB347), a * 0.55f)
        oval(x, y, r, r * 1.35f, c(0xFF9A3C), a * 0.95f)
    }
    // sunset glints on the water
    for (b in glints) circ(b.x * 1920f, 860f + b.y * 210f, 1.5f + b.r * 1.5f, c(0xFFF0C4), 0.65f * (0.5f + 0.5f * wob(sec, 4f, 1, b.ph)))
}

private fun DrawScope.inazuma(sec: Float) {
    twinkle(starField.take(40), 0f, 1920f, 20f, 500f, Color.White, 1f, 1.2f, sec, false)
    cloud2(drift(sec, 300f, 70f, 0f), 170f, 1.9f, 0.8f, c(0x1A0F3E))
    cloud2(drift(sec, 1000f, 90f, 0.3f), 120f, 1.6f, 0.75f, c(0x241450))
    cloud2(drift(sec, 1650f, 60f, 0.6f), 220f, 1.4f, 0.7f, c(0x1A0F3E))
    cloud2(drift(sec, 680f, 80f, 0.8f), 360f, 1.2f, 0.6f, c(0x2A1A5C))
    // lightning every 10 s
    val flash = keyframes(
        (sec % 10f) / 10f,
        floatArrayOf(0f, 0.8f, 0.82f, 0.85f, 0.87f, 0.9f, 1f),
        floatArrayOf(0f, 0f, 0.9f, 0f, 0.6f, 0f, 0f),
    )
    if (flash > 0.01f) {
        drawRect(c(0xE5D2FF), Offset.Zero, Size(1920f, 1080f), alpha = flash * 0.16f)
        glow(930f, 330f, 260f, c(0xC9A8FF), 0.5f * flash)
        val bolt = "M980 80 L920 250 L985 250 L880 470 L950 470 L860 640"
        strokeP(bolt, c(0xB58CFF), 22f, flash * 0.35f)
        strokeP(bolt, c(0xF1E6FF), 8f, flash)
        strokeP("M920 250 L820 330 L850 420", c(0xE5D2FF), 4f, flash)
        strokeP("M860 640 L880 800 L840 1000", c(0xE5D2FF), 10f, flash * 0.25f)
    }
    // sakura petals tumbling down
    val pink = c(0xF5B6D6)
    for (b in petals) {
        val p = cyc(sec, 20f, b.k, b.ph)
        val x = b.x * 2100f - 100f + 160f * p + 30f * wob(sec, 5f, 1, b.ph)
        val y = -30f + p * 1140f
        rotate(360f * cyc(sec, 6f, 1, b.ph), Offset(x, y)) {
            oval(x, y, 10f * (0.6f + b.r * 0.5f), 5f * (0.6f + b.r * 0.5f), pink, 0.85f)
        }
    }
    risingMotes(electroMotes, sec, 15f, 0f, 1920f, 960f, 800f, c(0xC9A8FF), c(0xEEDFFF))
}

private fun DrawScope.sumeru(sec: Float) {
    // glowing mushrooms breathing
    glow(1080f, 810f, 170f, c(0xFF9AC8), 0.20f + 0.18f * osc(sec, 6f))
    glow(430f, 835f, 120f, c(0x7FD6F5), 0.20f + 0.18f * osc(sec + 2f, 5f))
    glow(1640f, 835f, 140f, c(0xC9A0FF), 0.20f + 0.18f * osc(sec + 1f, 8f))
    glow(780f, 875f, 80f, c(0xFFD27A), 0.20f + 0.18f * osc(sec + 3f, 6f))
    risingMotes(fireflies, sec, 15f, 20f, 1880f, 1000f, 900f, c(0xD6FF8A), c(0xF3FFC8))
    twinkle(skyMotes, 0f, 1920f, 120f, 800f, c(0xD6FF8A), 1.5f, 1.5f, sec, true)
}

private fun DrawScope.fontaine(sec: Float) {
    cloud2(drift(sec, 380f, 70f, 0f), 220f, 1.4f, 0.55f)
    cloud2(drift(sec, 1020f, 90f, 0.3f), 150f, 1.0f, 0.45f)
    cloud2(drift(sec, 760f, 80f, 0.6f), 420f, 0.9f, 0.35f)
    twinkle(skyMotes, 40f, 1840f, 60f, 640f, c(0xBFEBFF), 1.5f, 1.9f, sec, true)
    // fountain: jets of drops arcing out of the top bowl into the basin
    val water = c(0xDFF3FF)
    for (i in 0 until 14) {
        val t = cyc(sec, 3f, 1, i / 14f)
        val side = if (i % 2 == 0) 1f else -1f
        val x = 330f + side * (40f + (i % 4) * 38f) * t
        val y = 600f - 140f * t + 370f * t * t
        circ(x, y, 3.4f, water, 0.9f * (1f - 0.3f * t))
    }
    for (i in 0 until 6) {
        val t = cyc(sec, 2f, 1, i / 6f)
        circ(330f + (i % 3 - 1) * 5f, 560f - 130f * 4f * t * (1f - t), 3.4f, water, 0.9f)
    }
    val rp = cyc(sec, 3f, 1, 0f)
    drawOval(water, Offset(330f - (60f + 130f * rp), 838f - (10f + 18f * rp)), Size(2f * (60f + 130f * rp), 2f * (10f + 18f * rp)), alpha = 0.5f * (1f - rp), style = Stroke(2f))
    // floating hydro bubbles, bobbing
    bubbleSpots.forEachIndexed { i, (bx, by, r) ->
        val x = bx + 24f * wob(sec, 15f, 1, i * 0.13f)
        val y = by + 40f * wob(sec, 20f, 1, i * 0.21f)
        glow(x, y, r * 2.6f, c(0x9BE3FF), 0.35f)
        circ(x, y, r, c(0xCFF0FF), 0.18f)
        drawCircle(Color.White, r, Offset(x, y), alpha = 0.75f, style = Stroke(2f))
        oval(x - r * 0.35f, y - r * 0.4f, r * 0.28f, r * 0.16f, Color.White, 0.85f)
    }
    for (b in glints) circ(b.x * 1900f + 10f, 840f + b.y * 230f, 1.5f + b.r * 1.5f, Color.White, 0.7f * (0.5f + 0.5f * wob(sec, 4f, 1, b.ph)))
}

private fun DrawScope.natlan(sec: Float) {
    cloud2(drift(sec, 380f, 70f, 0f), 230f, 1.9f, 0.7f, c(0x2A0F14))
    cloud2(drift(sec, 1100f, 90f, 0.3f), 130f, 1.6f, 0.7f, c(0x34121A))
    cloud2(drift(sec, 1650f, 60f, 0.6f), 300f, 1.5f, 0.65f, c(0x2A0F14))
    cloud2(drift(sec, 760f, 80f, 0.8f), 470f, 1.3f, 0.5f, c(0x6B2428))
    glow(1270f, 400f, 240f, c(0xFF7A3C), 0.25f + 0.22f * osc(sec, 5f))
    // embers flying up out of the crater
    for (b in embers) {
        val p = cyc(sec, 9f, b.k, b.ph)
        val x = 500f + b.x * 1500f + 50f * wob(sec, 4f, 1, b.ph)
        val y = 960f - p * 920f
        val a = sin(PI.toFloat() * p)
        glow(x, y, 14f + b.r * 8f, c(0xFFB070), a * 0.5f)
        circ(x, y, 2f + b.r * 2.2f, c(0xFFD9A0), a)
    }
    // lava heat shimmering along the ground
    translate(-600f * cyc(sec, 10f, 1, 0f), 0f) {
        strokeP("M0 1000 Q150 980 300 1000 T600 1000 T1200 1000 T1800 1000 T2400 1000 T3000 1000", c(0xFFB070), 6f, 0.25f)
    }
}

private fun DrawScope.snezhnaya(sec: Float) {
    twinkle(starField, 0f, 1920f, 10f, 630f, Color.White, 0.8f, 1.4f, sec, false)
    // aurora breathing
    translate(0f, 18f * osc(sec, 15f)) {
        strokeP("M-100 250 Q300 80 700 230 T1500 190 T2100 240", c(0x7CF5C8), 110f, 0.05f + 0.10f * osc(sec, 10f))
    }
    translate(0f, -18f * osc(sec, 10f)) {
        strokeP("M-100 430 Q500 330 1000 450 T2100 410", c(0xB58CFF), 90f, 0.04f + 0.08f * osc(sec, 15f))
    }
    // snowfall
    for (b in flakes) {
        val p = cyc(sec, 12f, b.k, b.ph)
        circ(b.x * 1960f + 30f * wob(sec, 6f, 1, b.ph), -20f + p * 1120f, 1.8f + b.r * 2.4f, Color.White, 0.55f + 0.3f * b.r.coerceAtMost(1f))
    }
}

private fun sceneRes(name: String) = when (name) {
    "Mondstadt" -> R.drawable.scene_mondstadt
    "Liyue" -> R.drawable.scene_liyue
    "Inazuma" -> R.drawable.scene_inazuma
    "Sumeru" -> R.drawable.scene_sumeru
    "Fontaine" -> R.drawable.scene_fontaine
    "Natlan" -> R.drawable.scene_natlan
    else -> R.drawable.scene_snezhnaya
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
