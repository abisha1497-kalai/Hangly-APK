package com.hangly.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in pendants, all drawn in code so the app needs no bundled images.
 * Every design hangs from a small bail at the top centre of the bitmap.
 */
object DefaultPendant {
    /** Built-in ids, in gallery order. */
    val IDS = listOf("lotus", "nazar", "diya", "heart", "coin", "star", "om")

    fun label(id: String): String = when (id) {
        "lotus" -> "Lotus"
        "nazar" -> "Evil eye"
        "diya" -> "Diya"
        "heart" -> "Heart"
        "coin" -> "Lucky coin"
        "star" -> "Star"
        "om" -> "Om"
        else -> id
    }

    /** The original golden lotus. */
    fun create(widthPx: Int): Bitmap = create("lotus", widthPx)

    fun create(id: String, widthPx: Int): Bitmap {
        val w = max1(widthPx)
        val h = (w * 1.45f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.035f
            color = 0xFF9C6B00.toInt()
        }
        val fw = w.toFloat()
        val fh = h.toFloat()
        when (id) {
            "nazar" -> nazar(c, fw, fh, fill, line)
            "diya" -> diya(c, fw, fh, fill, line)
            "heart" -> heart(c, fw, fh, fill, line)
            "coin" -> coin(c, fw, fh, fill, line)
            "star" -> star(c, fw, fh, fill, line)
            "om" -> om(c, fw, fh, fill, line)
            else -> lotus(c, fw, fh, fill, line)
        }
        return bmp
    }

    /** An emoji (or a few) hanging from a gold bail. */
    fun emoji(text: String, widthPx: Int): Bitmap {
        val w = max1(widthPx)
        val h = (w * 1.3f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val bail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.05f
            color = 0xFFE0A526.toInt()
        }
        c.drawLine(cx, 0f, cx, h * 0.14f, bail)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val t = text.ifBlank { "✨" }
        p.textSize = w * 0.8f
        val tw = p.measureText(t)
        if (tw > w * 0.96f) p.textSize = p.textSize * (w * 0.96f / tw)
        val fm = p.fontMetrics
        val cy = h * 0.56f
        c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2f, p)
        return bmp
    }

    // ---------- designs ----------

    private fun bail(c: Canvas, cx: Float, w: Float, to: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.05f
            color = 0xFFE0A526.toInt()
        }
        c.drawLine(cx, 0f, cx, to, p)
    }

    private fun lotus(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        // Lotus petals fanning down from under the medallion.
        val pivotY = h * 0.55f
        val petalLen = h * 0.40f
        val petalW = w * 0.30f
        val angles = floatArrayOf(-62f, 62f, -34f, 34f, 0f)
        for (a in angles) {
            c.save()
            c.rotate(a, cx, pivotY)
            val r = RectF(cx - petalW / 2, pivotY, cx + petalW / 2, pivotY + petalLen)
            fill.shader = RadialGradient(
                cx, pivotY + petalLen * 0.7f, petalLen,
                0xFFFFB6C8.toInt(), 0xFFD6336C.toInt(), Shader.TileMode.CLAMP
            )
            c.drawOval(r, fill)
            c.drawOval(r, line)
            c.restore()
        }
        fill.shader = null

        bail(c, cx, w, h * 0.12f)

        // Gold medallion.
        val mr = w * 0.32f
        val my = h * 0.33f
        fill.shader = RadialGradient(
            cx - mr * 0.3f, my - mr * 0.3f, mr * 1.4f,
            0xFFFFE9A3.toInt(), 0xFFC9901A.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, my, mr, fill)
        c.drawCircle(cx, my, mr, line)

        // Red gem.
        val gr = mr * 0.58f
        fill.shader = RadialGradient(
            cx - gr * 0.3f, my - gr * 0.3f, gr * 1.3f,
            0xFFFF8A80.toInt(), 0xFFB71C1C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, my, gr, fill)
        fill.shader = null
        fill.color = 0xAAFFFFFF.toInt()
        c.drawCircle(cx - gr * 0.35f, my - gr * 0.35f, gr * 0.16f, fill)

        // Tiny bead at the bottom.
        fill.color = 0xFFE0A526.toInt()
        c.drawCircle(cx, h - w * 0.05f, w * 0.05f, fill)
    }

    /** Nazar: a blue evil-eye bead in a gold rim. */
    private fun nazar(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val my = h * 0.42f
        val r = w * 0.40f
        bail(c, cx, w, my - r)
        fill.shader = null
        fill.color = 0xFF1A3A8F.toInt()
        c.drawCircle(cx, my, r, fill)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, my, r * 0.76f, fill)
        fill.color = 0xFF39A0ED.toInt()
        c.drawCircle(cx, my, r * 0.55f, fill)
        fill.color = 0xFF0B0F1A.toInt()
        c.drawCircle(cx, my, r * 0.28f, fill)
        fill.color = 0xCCFFFFFF.toInt()
        c.drawCircle(cx - r * 0.1f, my - r * 0.1f, r * 0.08f, fill)
        line.color = 0xFFE0A526.toInt()
        line.strokeWidth = w * 0.05f
        c.drawCircle(cx, my, r, line)
        // Three little beads underneath.
        fill.color = 0xFF39A0ED.toInt()
        c.drawCircle(cx, my + r + w * 0.07f, w * 0.05f, fill)
        c.drawCircle(cx - w * 0.14f, my + r + w * 0.05f, w * 0.04f, fill)
        c.drawCircle(cx + w * 0.14f, my + r + w * 0.05f, w * 0.04f, fill)
    }

    /** Diya: a clay lamp with a flame, hung from three little chains. */
    private fun diya(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val ry = h * 0.58f          // rim height
        val bw = w * 0.80f          // bowl width
        val depth = h * 0.20f
        val top = h * 0.10f
        bail(c, cx, w, top)

        val chain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.025f
            color = 0xFFE0A526.toInt()
        }
        c.drawLine(cx, top, cx - bw / 2f, ry, chain)
        c.drawLine(cx, top, cx + bw / 2f, ry, chain)

        // Flame (outer glow, flame, bright core).
        val fh = h * 0.30f
        val fw = w * 0.13f
        fill.shader = RadialGradient(
            cx, ry - fh * 0.4f, fh, 0x55FFD54F, 0x00FFD54F, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, ry - fh * 0.4f, fh, fill)
        fill.shader = null
        fill.color = 0xFFFF9800.toInt()
        c.drawPath(flame(cx, ry, fw, fh), fill)
        fill.color = 0xFFFFF176.toInt()
        c.drawPath(flame(cx, ry, fw * 0.55f, fh * 0.62f), fill)

        // Clay bowl.
        val bowl = Path().apply {
            moveTo(cx - bw / 2f, ry)
            cubicTo(cx - bw / 2f, ry + depth * 1.3f, cx + bw / 2f, ry + depth * 1.3f, cx + bw / 2f, ry)
            close()
        }
        fill.shader = RadialGradient(
            cx - bw * 0.15f, ry + depth * 0.2f, bw * 0.8f,
            0xFFE98A4B.toInt(), 0xFFB5481B.toInt(), Shader.TileMode.CLAMP
        )
        c.drawPath(bowl, fill)
        fill.shader = null
        line.color = 0xFF7A2E0C.toInt()
        c.drawPath(bowl, line)
        // Oil surface at the rim.
        fill.color = 0xFF5B2208.toInt()
        c.drawOval(RectF(cx - bw / 2f, ry - depth * 0.14f, cx + bw / 2f, ry + depth * 0.14f), fill)
        // Gold dots on the bowl.
        fill.color = 0xFFFFD54F.toInt()
        for (i in -1..1) c.drawCircle(cx + i * bw * 0.22f, ry + depth * 0.5f, w * 0.022f, fill)
    }

    private fun flame(cx: Float, base: Float, fw: Float, fh: Float): Path = Path().apply {
        moveTo(cx, base - fh)
        quadTo(cx + fw * 1.5f, base - fh * 0.4f, cx, base)
        quadTo(cx - fw * 1.5f, base - fh * 0.4f, cx, base - fh)
        close()
    }

    private fun heart(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val cy = h * 0.42f
        val s = w * 0.46f
        bail(c, cx, w, cy - s * 0.2f)
        val path = Path().apply {
            moveTo(cx, cy + s * 0.55f)
            cubicTo(cx - s * 0.95f, cy + s * 0.05f, cx - s * 0.55f, cy - s * 0.65f, cx, cy - s * 0.2f)
            cubicTo(cx + s * 0.55f, cy - s * 0.65f, cx + s * 0.95f, cy + s * 0.05f, cx, cy + s * 0.55f)
            close()
        }
        fill.shader = RadialGradient(
            cx - s * 0.3f, cy - s * 0.3f, s * 1.4f,
            0xFFFF8FA3.toInt(), 0xFFC2185B.toInt(), Shader.TileMode.CLAMP
        )
        c.drawPath(path, fill)
        fill.shader = null
        line.color = 0xFF8E0F3F.toInt()
        c.drawPath(path, line)
        fill.color = 0x99FFFFFF.toInt()
        c.drawOval(RectF(cx - s * 0.62f, cy - s * 0.38f, cx - s * 0.3f, cy - s * 0.18f), fill)
        // Bead.
        fill.color = 0xFFE0A526.toInt()
        c.drawCircle(cx, cy + s * 0.55f + w * 0.07f, w * 0.05f, fill)
    }

    /** A lucky coin with a square hole and a red tassel. */
    private fun coin(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val my = h * 0.36f
        val r = w * 0.36f
        bail(c, cx, w, my - r)
        fill.shader = RadialGradient(
            cx - r * 0.3f, my - r * 0.3f, r * 1.5f,
            0xFFFFE9A3.toInt(), 0xFFC9901A.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, my, r, fill)
        fill.shader = null
        line.color = 0xFF9C6B00.toInt()
        c.drawCircle(cx, my, r, line)
        c.drawCircle(cx, my, r * 0.82f, line)
        // Square hole, cut out so the background shows through.
        val hole = r * 0.27f
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        c.drawRect(cx - hole, my - hole, cx + hole, my + hole, clear)
        c.drawRect(cx - hole, my - hole, cx + hole, my + hole, line)
        // Tassel.
        val ty = my + r
        val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.03f
            color = 0xFFC62828.toInt()
        }
        c.drawLine(cx, ty - r * 0.05f, cx, ty + h * 0.05f, thread)
        fill.color = 0xFFC62828.toInt()
        c.drawCircle(cx, ty + h * 0.05f, w * 0.045f, fill)
        val tassel = Path().apply {
            moveTo(cx - w * 0.06f, ty + h * 0.06f)
            lineTo(cx + w * 0.06f, ty + h * 0.06f)
            lineTo(cx + w * 0.1f, h - w * 0.02f)
            lineTo(cx - w * 0.1f, h - w * 0.02f)
            close()
        }
        c.drawPath(tassel, fill)
        thread.strokeWidth = w * 0.012f
        thread.color = 0xFF8E1B1B.toInt()
        for (i in -1..1) {
            c.drawLine(cx + i * w * 0.04f, ty + h * 0.07f, cx + i * w * 0.06f, h - w * 0.03f, thread)
        }
    }

    private fun star(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val cy = h * 0.42f
        val outer = w * 0.44f
        val inner = outer * 0.45f
        bail(c, cx, w, cy - outer)
        val path = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val px = cx + (rad * cos(a)).toFloat()
            val py = cy + (rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.shader = RadialGradient(
            cx - outer * 0.25f, cy - outer * 0.25f, outer * 1.4f,
            0xFFFFF3B0.toInt(), 0xFFE0A526.toInt(), Shader.TileMode.CLAMP
        )
        line.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, fill)
        fill.shader = null
        line.color = 0xFF9C6B00.toInt()
        c.drawPath(path, line)
        fill.color = 0xFFE0A526.toInt()
        c.drawCircle(cx, cy + outer + w * 0.06f, w * 0.045f, fill)
    }

    /** A gold medallion with the Om sign (uses the phone's own Devanagari font). */
    private fun om(c: Canvas, w: Float, h: Float, fill: Paint, line: Paint) {
        val cx = w / 2f
        val my = h * 0.40f
        val r = w * 0.40f
        bail(c, cx, w, my - r)
        fill.shader = RadialGradient(
            cx - r * 0.3f, my - r * 0.3f, r * 1.5f,
            0xFFFFE9A3.toInt(), 0xFFC9901A.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, my, r, fill)
        fill.shader = null
        line.color = 0xFF9C6B00.toInt()
        c.drawCircle(cx, my, r, line)
        c.drawCircle(cx, my, r * 0.84f, line)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7A1C1C.toInt()
            textAlign = Paint.Align.CENTER
            textSize = r * 1.15f
        }
        val fm = t.fontMetrics
        c.drawText("ॐ", cx, my - (fm.ascent + fm.descent) / 2f, t)
        fill.color = 0xFFC62828.toInt()
        c.drawCircle(cx, my + r + w * 0.06f, w * 0.045f, fill)
    }

    private fun max1(v: Int) = if (v < 8) 8 else v
}
