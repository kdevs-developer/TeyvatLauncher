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

// ---------- nations ----------
private fun DrawScope.windmill(x: Float, y: Float, k: Float, angle: Float, wall: Color, roof: Color, sail: Color) {
    translate(x, y) {
        scale(k, Offset.Zero) {
            val hub = Offset(0f, -270f)
            val wood = c(0x6B4A32)
            // stone tower, shaded on the right, with brick bands
            fillP("M-62 0 L-40 -240 L40 -240 L62 0Z", wall)
            fillP("M20 0 L22 -240 L40 -240 L62 0Z", Color.Black, 0.12f)
            strokeP("M-58 -64 H58 M-52 -128 H52 M-46 -190 H46", Color.Black, 3f, 0.10f)
            // door and round window
            fillP("M-17 0 V-46 Q0 -70 17 -46 V0Z", c(0x5A3A26))
            circ(0f, -150f, 12f, c(0x5A3A26))
            circ(0f, -150f, 7f, c(0xFFE9B0), 0.9f)
            // rounded roof with a cream rim
            fillP("M-56 -234 Q-48 -314 0 -344 Q48 -314 56 -234Z", roof)
            fillP("M0 -344 Q48 -314 56 -234 H20 Q24 -300 0 -344Z", Color.Black, 0.15f)
            rect(-62f, -246f, 124f, 14f, c(0xF3EBDD))
            // the four lattice sails
            rotate(angle, hub) {
                for (i in 0 until 4) {
                    rotate(i * 90f, hub) {
                        drawLine(wood, hub, Offset(0f, -466f), 7f)
                        rect(5f, -460f, 46f, 140f, sail, 0.9f)
                        for (j in 0..5) drawLine(wood, Offset(5f, -460f + j * 28f), Offset(51f, -460f + j * 28f), 2.2f)
                        drawLine(wood, Offset(28f, -460f), Offset(28f, -320f), 2.2f)
                        drawRect(wood, Offset(5f, -460f), Size(46f, 140f), style = Stroke(3f))
                    }
                }
            }
            circ(hub.x, hub.y, 16f, wood)
            circ(hub.x, hub.y, 8f, c(0xE3B04B))
        }
    }
}

private fun DrawScope.dandelion(x: Float, y: Float, s: Float, a: Float, rot: Float) {
    val col = Color.White.copy(alpha = a)
    rotate(rot, Offset(x, y)) {
        for (i in 0 until 6) {
            val ang = i * 60f * (PI.toFloat() / 180f)
            val ex = x + cos(ang) * 12f * s
            val ey = y + sin(ang) * 12f * s
            drawLine(col, Offset(x, y), Offset(ex, ey), 1.6f * s)
            drawCircle(col, 2.2f * s, Offset(ex, ey))
        }
        drawCircle(col, 2.6f * s, Offset(x, y))
    }
}

private fun DrawScope.mondstadt(sec: Float) {
    vgrad(0x2B6CA3, 0xA9DCE0)
    glow(1500f, 250f, 260f, c(0xFFF3C4), 0.5f + 0.12f * osc(sec, 8f))
    circ(1500f, 250f, 90f, c(0xFFF3C4), 0.9f)
    // clouds drifting across
    cloud(((120f + sec * 20f) % 2400f) - 240f, 170f, 1.2f, 0.55f)
    cloud(((1000f + sec * 20f) % 2400f) - 240f, 300f, 0.9f, 0.40f)
    cloud(((1700f + sec * 40f) % 2400f) - 240f, 110f, 0.7f, 0.35f)
    fillP("M0 700 L220 460 L360 580 L560 380 L760 620 L900 520 L1100 670 L1300 470 L1500 640 L1720 450 L1920 620 V1080 H0Z", c(0x6E97B8))
    fillP("M560 380 L610 450 L585 440 L560 470 L535 440 L510 450Z M1300 470 L1345 530 L1320 522 L1300 548 L1280 522 L1255 530Z", c(0xEAF4F8))
    fillP("M0 840 Q300 720 640 810 T1300 790 T1920 820 V1080 H0Z", c(0x4FA073))
    // far windmill, small and slow
    windmill(380f, 800f, 0.42f, 360f * cyc(sec, 40f, 1, 0.3f), c(0xCFC3A6), c(0x9A4A3C), c(0xE9DFC4))
    // hill and the main windmill
    fillP("M900 960 Q1480 700 1920 900 V1080 H900Z", c(0x3E8F66))
    windmill(1480f, 830f, 1f, 360f * cyc(sec, 24f, 1, 0f), c(0xE8D9B5), c(0xC9553F), c(0xF3EBDD))
    fillP("M0 950 Q400 870 900 940 T1920 910 V1080 H0Z", c(0x2F7D5B))
    // wind streaks sweeping through
    for (i in 0 until 4) {
        val p = cyc(sec, 10f, 1, i * 0.25f)
        translate(-200f + p * 2300f, 420f + i * 110f + 16f * wob(sec, 5f, 1, i * 0.3f)) {
            strokeP("M0 0 Q40 -18 90 0 T180 0", Color.White, 3f, 0.5f * sin(PI.toFloat() * p))
        }
    }
    // dandelion seeds floating on the breeze
    for (b in dandelions) {
        val p = cyc(sec, 30f, 1, b.ph)
        val x = -100f + p * 2120f
        val y = 260f + b.y * 480f + 40f * wob(sec, 6f, 1, b.ph)
        dandelion(x, y, 0.7f + b.r * 0.6f, 0.85f * sin(PI.toFloat() * p), 360f * cyc(sec, 20f, 1, b.ph))
    }
    // grass swaying in front
    for (i in 0..24) {
        translate(i * 80f + 20f, 1075f) {
            rotate(7f * wob(sec, 4f, 1, i * 0.137f), Offset.Zero) {
                strokeP("M0 0 Q-8 -45 -3 -88", c(0x3FA36E), 5f)
                strokeP("M10 0 Q14 -38 20 -70", c(0x5CBF87), 5f)
            }
        }
    }
}

private fun DrawScope.liyue(sec: Float) {
    vgrad(0x3A2A55, 0xE6A95E)
    glow(1400f, 430f, 340f, c(0xFFD27A), 0.35f + 0.1f * osc(sec, 10f))
    circ(1400f, 430f, 120f + 10f * osc(sec, 10f), c(0xFFD27A), 0.9f)
    fillP("M300 1080 L340 380 Q370 330 410 380 L470 1080Z", c(0x4C6E73))
    fillP("M900 1080 L950 300 Q990 240 1040 300 L1110 1080Z", c(0x3A5A62))
    fillP("M1600 1080 L1640 450 Q1670 410 1710 450 L1780 1080Z", c(0x4C6E73))
    // mist drifting between the pillars
    oval(((300f + sec * 20f) % 2400f) - 240f, 640f, 300f, 34f, c(0xFFE3B0), 0.20f)
    oval(((1300f + sec * 20f) % 2400f) - 240f, 560f, 380f, 40f, c(0xFFE3B0), 0.16f)
    rect(0f, 830f, 1920f, 250f, c(0x2C5C70))
    translate(120f * osc(sec, 15f), 0f) {
        strokeP("M-240 880 Q0 860 240 880 T720 880 T1200 880 T1680 880 T2160 880", c(0x8FC3C8), 4f)
    }
    translate(1180f, 760f) {
        glow(0f, 20f, 190f, c(0xFFB347), 0.25f + 0.15f * osc(sec, 4f))
        rect(-70f, 0f, 140f, 70f, c(0x8C3B2E))
        fillP("M-130 6 Q0 -80 130 6Z", c(0xC9553F))
        strokeP("M-130 6 Q0 -80 130 6", c(0xE3B04B), 6f)
    }
    // red lanterns swinging from above
    listOf(200f, 620f, 1060f, 1500f, 1840f).forEachIndexed { i, x ->
        val len = 120f + (i % 2) * 50f
        rotate(5f * wob(sec, 6f, 1, i * 0.21f), Offset(x, 0f)) {
            drawLine(c(0xE3B04B), Offset(x, 0f), Offset(x, len), 3f)
            glow(x, len + 34f, 80f, c(0xFFB347), 0.35f + 0.15f * osc(sec + i * 1.3f, 4f))
            oval(x, len + 34f, 24f, 34f, c(0xC9332B))
            rect(x - 14f, len, 28f, 6f, c(0xE3B04B))
            rect(x - 14f, len + 62f, 28f, 6f, c(0xE3B04B))
            drawLine(c(0xE3B04B), Offset(x, len + 68f), Offset(x, len + 98f), 3f)
        }
    }
    // sky lanterns rising
    for (b in skyLanterns) {
        val p = cyc(sec, 20f, 1, b.ph)
        val x = 120f + b.x * 1500f + 25f * wob(sec, 10f, 1, b.ph)
        val y = 860f - p * 980f
        val a = sin(PI.toFloat() * p)
        glow(x, y, 70f, c(0xFFC66B), a * 0.4f)
        oval(x, y, 11f + b.r * 5f, 15f + b.r * 7f, c(0xFF9A3C), a * 0.9f)
    }
}

private fun DrawScope.inazuma(sec: Float) {
    vgrad(0x1A0F33, 0x6B3FA0)
    circ(1450f, 300f, 120f, c(0xF2E8FF), 0.9f)
    // storm clouds rolling by
    cloud(((300f + sec * 20f) % 2400f) - 240f, 120f, 1.7f, 0.55f, c(0x2A1A4A))
    cloud(((1500f + sec * 40f) % 2400f) - 240f, 220f, 1.2f, 0.45f, c(0x2A1A4A))
    val flash = keyframes(
        (sec % 10f) / 10f,
        floatArrayOf(0f, 0.8f, 0.82f, 0.85f, 0.87f, 0.9f, 1f),
        floatArrayOf(0f, 0f, 0.9f, 0f, 0.6f, 0f, 0f),
    )
    if (flash > 0.01f) {
        drawRect(c(0xE5D2FF), Offset.Zero, Size(1920f, 1080f), alpha = flash * 0.16f)
        strokeP("M1000 0 L940 220 L1010 220 L900 480", c(0xE5D2FF), 8f, flash)
    }
    rect(0f, 880f, 1920f, 200f, c(0x1E1440))
    fillP("M1000 900 Q1300 760 1600 900Z", c(0x2A1A4A))
    translate(520f, 560f) {
        glow(0f, 20f, 240f, c(0xFF5A5A), 0.16f + 0.10f * osc(sec, 6f))
        val red = c(0xC23B3B)
        rect(-120f, 0f, 30f, 320f, red); rect(90f, 0f, 30f, 320f, red)
        rect(-175f, -30f, 350f, 34f, red); rect(-135f, 70f, 270f, 20f, red)
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
}

private fun DrawScope.sumeru(sec: Float) {
    vgrad(0x0F3A2C, 0x7CC47A)
    translate(30f * wob(sec, 20f, 1, 0f), 0f) {
        fillP("M1100 0 L1300 0 L900 1080 L700 1080Z M1500 0 L1620 0 L1300 1080 L1180 1080Z", c(0xE8F5B0), 0.10f + 0.14f * osc(sec, 10f))
    }
    val bark = c(0x3B2A1E)
    rect(180f, 0f, 120f, 1080f, bark); rect(780f, 0f, 90f, 1080f, bark); rect(1560f, 0f, 130f, 1080f, bark)
    // canopies swaying in the wind
    translate(14f * wob(sec, 8f, 1, 0.1f), 0f) { oval(240f, 120f, 330f, 170f, c(0x2F8F4E)) }
    translate(14f * wob(sec, 8f, 1, 0.4f), 0f) { oval(830f, 80f, 280f, 150f, c(0x2A7F45)) }
    translate(14f * wob(sec, 8f, 1, 0.7f), 0f) { oval(1620f, 120f, 340f, 170f, c(0x2F8F4E)) }
    // hanging vines with glowing buds
    listOf(240f, 560f, 830f, 1250f, 1620f).forEachIndexed { i, x ->
        translate(x, 190f) {
            rotate(6f * wob(sec, 6f, 1, i * 0.2f), Offset.Zero) {
                strokeP("M0 0 Q-14 60 0 120 T0 240", c(0x2F8F4E), 7f)
                glow(0f, 240f, 40f, c(0xD6FF8A), 0.4f + 0.3f * osc(sec + i, 5f))
                circ(0f, 240f, 9f, c(0x9BE86A))
            }
        }
    }
    fillP("M0 900 Q500 820 1000 900 T1920 880 V1080 H0Z", c(0x1F5A3A))
    // mushrooms breathing light
    glow(1012f, 780f, 170f, c(0xF4A6C0), 0.22f + 0.2f * osc(sec, 6f))
    rect(1000f, 760f, 24f, 140f, c(0xE8D9B5))
    oval(1012f, 760f, 110f, 60f, c(0xF4A6C0), 1f - 0.35f * osc(sec, 6f))
    glow(429f, 820f, 130f, c(0x7FD6F5), 0.25f + 0.2f * osc(sec, 5f))
    rect(420f, 800f, 18f, 100f, c(0xE8D9B5))
    oval(429f, 800f, 80f, 44f, c(0x7FD6F5))
    // dendro seeds drifting up
    for (b in fireflies) {
        val p = cyc(sec, 15f, b.k, b.ph)
        val x = 20f + b.x * 1880f + 40f * wob(sec, 6f, 1, b.ph)
        val y = 980f - p * 900f
        val a = sin(PI.toFloat() * p)
        glow(x, y, 20f + b.r * 14f, c(0xD6FF8A), a * 0.7f)
        circ(x, y, 2.5f + b.r * 2f, c(0xF3FFC8), a)
    }
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
    // fountain: pedestal, basin and arcing jets
    rect(285f, 720f, 30f, 90f, c(0xD9D2C0))
    oval(300f, 720f, 62f, 14f, c(0xE9E2D0))
    val water = c(0xD6F0FF)
    for (i in 0 until 14) {
        val t = cyc(sec, 3f, 1, i / 14f)
        val side = if (i % 2 == 0) 1f else -1f
        val x = 300f + (30f + (i % 5) * 14f) * side * t * 1.6f
        val y = 715f - (200f + (i % 4) * 30f) * 4f * t * (1f - t)
        circ(x, y, 3.6f, water, 0.85f)
    }
    for (i in 0 until 6) {
        val t = cyc(sec, 2f, 1, i / 6f)
        circ(300f + (i % 3 - 1) * 6f, 715f - 260f * 4f * t * (1f - t), 3.6f, water, 0.9f)
    }
    val rp = cyc(sec, 3f, 1, 0f)
    drawOval(water, Offset(300f - (40f + 80f * rp), 806f - (8f + 14f * rp)), Size(2f * (40f + 80f * rp), 2f * (8f + 14f * rp)), alpha = 0.5f * (1f - rp), style = Stroke(2f))
    // a little boat bobbing on the water
    translate(0f, 7f * wob(sec, 6f, 1, 0f)) {
        rotate(3f * wob(sec, 6f, 1, 0.25f), Offset(700f, 850f)) {
            fillP("M600 850 L800 850 L770 895 L630 895Z", c(0x7A4A2C))
            rect(698f, 740f, 6f, 110f, c(0x5A3A26))
            fillP("M704 745 L704 840 L780 840Z", c(0xF3EBDD))
        }
    }
    // sparkles on the water
    for (b in sparkles) {
        circ(b.x * 1900f, 830f + b.y * 220f, 2f + b.r * 1.5f, Color.White, 0.6f * (0.5f + 0.5f * wob(sec, 4f, 1, b.ph)))
    }
}

private fun DrawScope.natlan(sec: Float) {
    vgrad(0x2A0E10, 0xFF7A3C)
    oval(960f, 200f, 420f, 90f, c(0x1A0A0C), 0.55f)
    fillP("M0 1080 L0 800 L300 640 L560 820 L760 560 L1160 560 L1360 820 L1620 640 L1920 800 V1080Z", c(0x3B1A16))
    fillP("M500 1080 L880 400 L1000 360 L1120 400 L1500 1080Z", c(0x2B1512))
    glow(1000f, 400f, 360f, c(0xFF7A3C), 0.30f + 0.22f * osc(sec, 5f))
    fillP("M880 400 L1000 360 L1120 400 L1060 420 L940 420Z", c(0xFF7A3C), 0.7f + 0.3f * osc(sec, 5f))
    strokeP("M960 420 L930 620 L970 800 L940 1000 M1040 420 L1070 640 L1030 820", c(0xFF7A3C), 10f, 0.65f + 0.35f * osc(sec, 4f))
    // smoke curling up from the crater
    for (i in 0 until 5) {
        val p = cyc(sec, 12f, 1, i / 5f)
        val x = 1000f + 60f * wob(sec, 12f, 1, i * 0.2f) + p * 80f
        oval(x, 380f - p * 320f, 40f + p * 90f, 26f + p * 50f, c(0x1A0A0C), 0.30f * sin(PI.toFloat() * p))
    }
    // embers flying up
    for (b in embers) {
        val p = cyc(sec, 8f, b.k, b.ph)
        val x = 700f + b.x * 600f + 50f * wob(sec, 4f, 1, b.ph)
        val y = 430f - p * 480f
        val a = sin(PI.toFloat() * p)
        glow(x, y, 14f + b.r * 8f, c(0xFFB070), a * 0.5f)
        circ(x, y, 2f + b.r * 2.2f, c(0xFFD9A0), a)
    }
    // lava heat rolling along the bottom
    translate(-600f * cyc(sec, 10f, 1, 0f), 0f) {
        strokeP("M0 1000 Q150 980 300 1000 T600 1000 T1200 1000 T1800 1000 T2400 1000 T3000 1000", c(0xFFB070), 6f, 0.35f)
    }
}

private fun DrawScope.snezhnaya(sec: Float) {
    vgrad(0x0B1D33, 0x5B9AC0)
    // twinkling stars
    for (b in stars) {
        circ(b.x * 1920f, b.y * 380f, 1.2f + b.r, Color.White, 0.3f + 0.6f * (0.5f + 0.5f * wob(sec, 4f, 1, b.ph)))
    }
    // two aurora ribbons
    translate(0f, 24f * osc(sec, 15f)) {
        strokeP("M0 300 Q400 180 800 320 T1600 260 T2000 300", c(0x7CF5C8), 90f, 0.25f + 0.25f * osc(sec, 10f))
    }
    translate(0f, -24f * osc(sec, 10f)) {
        strokeP("M0 380 Q500 260 1000 400 T2000 340", c(0xB58CFF), 70f, 0.12f + 0.15f * osc(sec, 15f))
    }
    fillP("M0 760 L260 460 L480 700 L760 400 L1040 720 L1300 480 L1560 700 L1760 520 L1920 680 V1080 H0Z", c(0xB8D4E6))
    fillP("M0 900 Q500 820 1000 900 T1920 880 V1080 H0Z", c(0xEAF4F8))
    val tower = c(0x8FB0C8)
    rect(1380f, 620f, 50f, 160f, tower); fillP("M1370 620 L1405 520 L1440 620Z", tower)
    rect(1480f, 660f, 40f, 120f, tower); fillP("M1472 660 L1500 584 L1528 660Z", tower)
    glow(1405f, 680f, 90f, c(0xFFE9B0), 0.25f + 0.2f * osc(sec, 4f))
    // snowfall
    for (b in flakes) {
        val p = cyc(sec, 12f, b.k, b.ph)
        circ(b.x * 1960f + 30f * wob(sec, 6f, 1, b.ph), -20f + p * 1120f, 1.8f + b.r * 2.4f, Color.White, 0.55f + 0.3f * b.r.coerceAtMost(1f))
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
