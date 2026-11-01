package com.teyvat.launcher

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws one app's art so it fills the WHOLE 16:9 tile (no little icon inside a box):
 *  - TV apps: their banner, cropped to fill.
 *  - Adaptive icons: the background layer is stretched over the tile, the logo layer sits on top, full height.
 *  - Old square icons: a gradient built from the icon's own colour behind a big centred icon.
 */
object TileArt {
    const val W = 400
    const val H = 225

    fun render(pm: PackageManager, ri: ResolveInfo): ImageBitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val banner = runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull()
        if (banner != null) {
            cover(canvas, banner)
        } else {
            val icon = runCatching { ri.loadIcon(pm) }.getOrNull() ?: pm.defaultActivityIcon
            fillWithIcon(canvas, icon)
        }
        return bmp.asImageBitmap()
    }

    /** Scale [d] so it covers the whole tile (centre-crop). */
    private fun cover(canvas: Canvas, d: Drawable) {
        val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else W
        val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else H
        val scale = max(W / iw.toFloat(), H / ih.toFloat())
        val w = (iw * scale).roundToInt()
        val h = (ih * scale).roundToInt()
        val l = (W - w) / 2
        val t = (H - h) / 2
        d.setBounds(l, t, l + w, t + h)
        d.draw(canvas)
    }

    private fun fillWithIcon(canvas: Canvas, icon: Drawable) {
        if (icon is AdaptiveIconDrawable) {
            val bg = icon.background
            val fg = icon.foreground
            if (bg != null && fg != null) {
                cover(canvas, bg)
                // An adaptive layer is 108dp but only the middle 72dp is guaranteed visible,
                // so a layer 1.5x the tile height shows exactly that safe zone at full height.
                val s = (H * 1.5f).roundToInt()
                val l = (W - s) / 2
                val t = (H - s) / 2
                fg.setBounds(l, t, l + s, t + s)
                fg.draw(canvas)
                return
            }
        }
        // Legacy icon: its own colour as a backdrop, icon big in the middle.
        val base = averageColor(icon)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                scaleColor(base, 1.18f), scaleColor(base, 0.70f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        val side = (H * 0.80f).roundToInt()
        val l = (W - side) / 2
        val t = (H - side) / 2
        icon.setBounds(l, t, l + side, t + side)
        icon.draw(canvas)
    }

    private fun averageColor(d: Drawable): Int {
        val s = 32
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(b))
        var r = 0L
        var g = 0L
        var bl = 0L
        var n = 0L
        for (y in 0 until s) for (x in 0 until s) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) > 200) {
                r += Color.red(p)
                g += Color.green(p)
                bl += Color.blue(p)
                n++
            }
        }
        b.recycle()
        if (n == 0L) return Color.rgb(0x1B, 0x23, 0x42)
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun scaleColor(c: Int, f: Float): Int {
        fun ch(v: Int) = (v * f).roundToInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(c)), ch(Color.green(c)), ch(Color.blue(c)))
    }
}
