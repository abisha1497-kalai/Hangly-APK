package com.hangly.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val N = 14             // points in each chain
private const val ITERATIONS = 22    // constraint passes per step
private const val DAMPING = 0.996f   // velocity kept per 1/120 s step
private const val SPRING_K = 260f    // elastic pull-back strength (higher = snappier)
private const val SPRING_C = 9f      // elastic damping (lower = more bounce)
private const val MIN_STRETCH = 0.9f // slight squash on the rebound
private const val MAX_SPARKS = 56

/** One charm on its own chain: the pendant, where its hook is, and how far its chain may stretch. */
class StrandSpec(
    val bob: Bitmap,
    val pivotX: Float,
    val chainLen: Float,
    /** How far the rope may stretch while dragging, as a multiple of its rest length. */
    val maxStretch: Float
)

/**
 * One or more chains hanging from fixed hooks at the top of the screen, each with a pendant on the
 * end. Every chain is a Verlet rope; the pendant is a heavy last link that rotates with the chain.
 * Pendants bump into each other. Coordinates are screen pixels (this view sits at the top-left
 * corner of the screen).
 */
class PendulumView(
    context: Context,
    specs: List<StrandSpec>,
    private val density: Float,
    private val style: Int
) : View(context) {

    companion object {
        val STYLE_NAMES = arrayOf("Gold", "Silver", "Red thread", "Pearls", "Marigold", "Neon", "Mala")
        private const val S_PEARLS = 3
        private const val S_MARIGOLD = 4
        private const val S_NEON = 5
        private const val S_MALA = 6

        private val SPARK_COLORS = intArrayOf(
            0xFFFFE082.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFB6C8.toInt(), 0xFFFFD54F.toInt()
        )
    }

    /** Acceleration in px/s^2: gx to the right, gy downwards. */
    var gx = 0f
    var gy = 0f

    private inner class Strand(
        var bob: Bitmap,
        val pivotX: Float,
        val chainLen: Float,
        val maxStretch: Float,
        pushDir: Float
    ) {
        val x = FloatArray(N)
        val y = FloatArray(N)
        val ox = FloatArray(N)
        val oy = FloatArray(N)
        val invMass = FloatArray(N) { 1f }
        val seg = chainLen / (N - 1)
        var rest = seg

        var dragging = false
        var dragTx = 0f
        var dragTy = 0f
        var grabDx = 0f
        var grabDy = 0f
        var angle = 0f

        /** Current rope length as a multiple of its rest length (1 = relaxed). */
        var stretch = 1f
        var stretchVel = 0f

        init {
            invMass[0] = 0f
            invMass[N - 1] = 0.15f // heavy pendant
            for (i in 0 until N) {
                x[i] = pivotX
                y[i] = seg * i
                ox[i] = x[i]
                oy[i] = y[i]
            }
            // A small initial push so it swings gently when it appears.
            for (i in 1 until N) ox[i] = x[i] - pushDir * 1.2f * density * i / (N - 1)
        }

        /** Verlet step: elastic stretch, gravity, and following the finger while dragged. */
        fun integrate(dt: Float, gx: Float, gy: Float) {
            val dt2 = dt * dt
            // Elastic rope: follows the finger while dragging, springs back (with a bounce) on release.
            if (dragging) {
                val d = hypot(x[N - 1] - pivotX, y[N - 1])
                val target = (d / chainLen).coerceIn(1f, maxStretch)
                stretchVel = (target - stretch) / dt * 0.2f
                stretch += (target - stretch) * 0.5f
            } else if (stretch != 1f || stretchVel != 0f) {
                stretchVel += (-SPRING_K * (stretch - 1f) - SPRING_C * stretchVel) * dt
                stretch = (stretch + stretchVel * dt).coerceIn(MIN_STRETCH, maxStretch)
                if (abs(stretch - 1f) < 0.0005f && abs(stretchVel) < 0.01f) {
                    stretch = 1f
                    stretchVel = 0f
                }
            }
            rest = seg * stretch
            for (i in 1 until N) {
                if (dragging && i == N - 1) continue
                val vx = (x[i] - ox[i]) * DAMPING
                val vy = (y[i] - oy[i]) * DAMPING
                ox[i] = x[i]
                oy[i] = y[i]
                x[i] += vx + gx * dt2
                y[i] += vy + gy * dt2
            }
            x[0] = pivotX
            y[0] = 0f
            if (dragging) {
                ox[N - 1] = x[N - 1]
                oy[N - 1] = y[N - 1]
                x[N - 1] += (dragTx - x[N - 1]) * 0.4f
                y[N - 1] += (dragTy - y[N - 1]) * 0.4f
            }
        }

        /** One pass of the chain's length constraints. */
        fun solve() {
            for (i in 0 until N - 1) {
                val j = i + 1
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 1e-4f) continue
                val wi = invMass[i]
                val wj = if (dragging && j == N - 1) 0f else invMass[j]
                val ws = wi + wj
                if (ws == 0f) continue
                val diff = (dist - rest) / dist
                x[i] += dx * diff * wi / ws
                y[i] += dy * diff * wi / ws
                x[j] -= dx * diff * wj / ws
                y[j] -= dy * diff * wj / ws
            }
            x[0] = pivotX
            y[0] = 0f
        }

        fun motion(): Float {
            var s = 0f
            for (i in 1 until N) s += abs(x[i] - ox[i]) + abs(y[i] - oy[i])
            s += abs(stretch - 1f) * 50f + abs(stretchVel)
            return s
        }

        fun center(out: FloatArray) {
            val dx = x[N - 1] - x[N - 2]
            val dy = y[N - 1] - y[N - 2]
            val len = hypot(dx, dy).let { if (it < 1e-4f) 1f else it }
            val half = bob.height / 2f - 4f * density
            out[0] = x[N - 1] + dx / len * half
            out[1] = y[N - 1] + dy / len * half
        }

        /** Size of the circle used when charms bump into each other. */
        fun radius(): Float = min(bob.width, bob.height) * 0.5f
    }

    private val strands: List<Strand> = specs.mapIndexed { i, s ->
        Strand(s.bob, s.pivotX, s.chainLen, s.maxStretch, if (i % 2 == 0) 1f else -1f)
    }

    // Sparkles (burst when a charm is tapped).
    private val sx = FloatArray(MAX_SPARKS)
    private val sy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS)
    private val svy = FloatArray(MAX_SPARKS)
    private val slife = FloatArray(MAX_SPARKS)
    private val smax = FloatArray(MAX_SPARKS) { 1f }
    private val ssize = FloatArray(MAX_SPARKS)
    private val scolor = IntArray(MAX_SPARKS)
    private var sparkHead = 0

    private val ca = FloatArray(2)
    private val cb = FloatArray(2)
    private val path = Path()
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3.4f * density
    }
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.1f * density
        pathEffect = DashPathEffect(floatArrayOf(3f * density, 4.5f * density), 0f)
    }
    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val beadFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beadEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }
    private val hookPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.2f * density
    }

    init {
        when (style) {
            1 -> setColors(0xFF7D8A97.toInt(), 0xFFEEF3F7.toInt())
            2 -> setColors(0xFF7A1C1C.toInt(), 0xFFE0524D.toInt())
            S_PEARLS -> setColors(0xFFB9A9A9.toInt(), 0xFFF3E6E6.toInt())
            S_MARIGOLD -> setColors(0xFF4F7D3A.toInt(), 0xFFFFB300.toInt())
            S_NEON -> setColors(0xFF00E5FF.toInt(), 0xFFE6FDFF.toInt())
            S_MALA -> setColors(0xFF5D4037.toInt(), 0xFFD4A017.toInt())
            else -> setColors(0xFFB07A0A.toInt(), 0xFFFFD966.toInt())
        }
        gy = 9.81f * 300f * density
    }

    private fun setColors(base: Int, light: Int) {
        basePaint.color = base
        linkPaint.color = light
        hookPaint.color = base
        ringPaint.color = base
    }

    // ---------- simulation ----------

    /** Advances the simulation by [dt] seconds (call with a small fixed step, e.g. 1/120). */
    fun step(dt: Float) {
        for (s in strands) s.integrate(dt, gx, gy)
        for (iter in 0 until ITERATIONS) {
            for (s in strands) s.solve()
            if (strands.size > 1) collide()
        }
        stepSparks(dt)
    }

    /** Charms are circles; when two overlap, push their pendants apart (a dragged one wins). */
    private fun collide() {
        for (i in 0 until strands.size - 1) {
            for (j in i + 1 until strands.size) {
                val a = strands[i]
                val b = strands[j]
                a.center(ca)
                b.center(cb)
                val dx = cb[0] - ca[0]
                val dy = cb[1] - ca[1]
                val dist = hypot(dx, dy)
                val minDist = a.radius() + b.radius()
                if (dist >= minDist || dist < 1e-3f) continue
                val wa = if (a.dragging) 0f else 1f
                val wb = if (b.dragging) 0f else 1f
                val ws = wa + wb
                if (ws == 0f) continue
                val overlap = minDist - dist
                val nx = dx / dist
                val ny = dy / dist
                val pa = overlap * wa / ws
                val pb = overlap * wb / ws
                a.x[N - 1] -= nx * pa
                a.y[N - 1] -= ny * pa
                a.x[N - 2] -= nx * pa * 0.5f
                a.y[N - 2] -= ny * pa * 0.5f
                b.x[N - 1] += nx * pb
                b.y[N - 1] += ny * pb
                b.x[N - 2] += nx * pb * 0.5f
                b.y[N - 2] += ny * pb * 0.5f
            }
        }
    }

    private fun stepSparks(dt: Float) {
        for (k in 0 until MAX_SPARKS) {
            if (slife[k] <= 0f) continue
            slife[k] -= dt
            sx[k] += svx[k] * dt
            sy[k] += svy[k] * dt
            svx[k] *= 0.985f
            svy[k] = svy[k] * 0.985f + 260f * density * dt
        }
    }

    /** Sum of recent movement; near zero when everything has settled. */
    fun motion(): Float {
        var s = 0f
        for (st in strands) s += st.motion()
        for (k in 0 until MAX_SPARKS) if (slife[k] > 0f) s += 5f
        return s
    }

    // ---------- charm access (used by the overlay service) ----------

    val count: Int get() = strands.size

    val anyDragging: Boolean get() = strands.any { it.dragging }

    fun bobWidth(i: Int): Int = strands[i].bob.width

    fun bobHeight(i: Int): Int = strands[i].bob.height

    /** Swaps the pendant picture on charm [i] without disturbing its swing. */
    fun setBob(i: Int, bob: Bitmap) {
        strands[i].bob = bob
        invalidate()
    }

    /** Writes the centre of charm [i]'s pendant (screen px) into [out]. */
    fun bobCenter(i: Int, out: FloatArray) {
        strands[i].center(out)
    }

    /** Gives charm [i] a push sideways: [v] is extra speed in px per simulation step. */
    fun nudge(i: Int, v: Float) {
        val s = strands[i]
        if (s.dragging) return
        for (j in 1 until N) s.ox[j] -= v * j / (N - 1)
    }

    /** A little burst of sparkles around charm [i]. */
    fun sparkle(i: Int) {
        val s = strands[i]
        s.center(ca)
        val spread = s.radius() * 0.6f
        for (k in 0 until 18) {
            val idx = sparkHead
            sparkHead = (sparkHead + 1) % MAX_SPARKS
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val speed = (60f + Random.nextFloat() * 220f) * density
            sx[idx] = ca[0] + cos(a) * spread
            sy[idx] = ca[1] + sin(a) * spread
            svx[idx] = cos(a) * speed
            svy[idx] = sin(a) * speed - 40f * density
            slife[idx] = 0.5f + Random.nextFloat() * 0.5f
            smax[idx] = slife[idx]
            ssize[idx] = (1.6f + Random.nextFloat() * 2.4f) * density
            scolor[idx] = SPARK_COLORS[Random.nextInt(SPARK_COLORS.size)]
        }
        invalidate()
    }

    fun isDragging(i: Int): Boolean = strands[i].dragging

    fun beginDrag(i: Int, rawX: Float, rawY: Float) {
        val s = strands[i]
        s.dragging = true
        s.grabDx = s.x[N - 1] - rawX
        s.grabDy = s.y[N - 1] - rawY
        s.dragTx = s.x[N - 1]
        s.dragTy = s.y[N - 1]
    }

    fun dragTo(i: Int, rawX: Float, rawY: Float) {
        val s = strands[i]
        var tx = rawX + s.grabDx
        var ty = (rawY + s.grabDy).coerceAtLeast(0f)
        val dx = tx - s.pivotX
        val dist = hypot(dx, ty)
        val maxReach = s.chainLen * s.maxStretch * 0.995f
        if (dist > maxReach) {
            val k = maxReach / dist
            tx = s.pivotX + dx * k
            ty *= k
        }
        s.dragTx = tx
        s.dragTy = ty
    }

    fun endDrag(i: Int) {
        strands[i].dragging = false
    }

    // ---------- drawing ----------

    override fun onDraw(canvas: Canvas) {
        for (s in strands) {
            drawChain(canvas, s)

            // Hook at the top edge.
            canvas.drawCircle(s.x[0], s.y[0] + 1f * density, 4.5f * density, hookPaint)

            // Pendant, rotated to follow the last chain segment.
            val dx = s.x[N - 1] - s.x[N - 2]
            val dy = s.y[N - 1] - s.y[N - 2]
            val target = -Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
            val diff = ((target - s.angle + 540f) % 360f) - 180f
            s.angle += diff * 0.4f

            canvas.save()
            canvas.translate(s.x[N - 1], s.y[N - 1])
            canvas.rotate(s.angle)
            canvas.drawBitmap(s.bob, -s.bob.width / 2f, -4f * density, bmpPaint)
            canvas.drawCircle(0f, 0f, 3.2f * density, ringPaint) // jump ring
            canvas.restore()
        }
        drawSparks(canvas)
    }

    private fun drawSparks(canvas: Canvas) {
        for (k in 0 until MAX_SPARKS) {
            if (slife[k] <= 0f) continue
            val alpha = (slife[k] / smax[k]).coerceIn(0f, 1f)
            sparkPaint.color = (scolor[k] and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24)
            val r = ssize[k] * (0.5f + 0.5f * alpha)
            canvas.drawLine(sx[k] - r * 1.6f, sy[k], sx[k] + r * 1.6f, sy[k], sparkPaint)
            canvas.drawLine(sx[k], sy[k] - r * 1.6f, sx[k], sy[k] + r * 1.6f, sparkPaint)
            canvas.drawCircle(sx[k], sy[k], r * 0.6f, sparkPaint)
        }
    }

    /** Calls [block] for [count] evenly spaced points along [p] (index, x, y, tangent angle). */
    private inline fun along(p: Path, count: Int, block: (Int, Float, Float, Float) -> Unit) {
        measure.setPath(p, false)
        val len = measure.length
        if (len <= 0f) return
        for (k in 0 until count) {
            measure.getPosTan((k + 0.5f) * len / count, pos, tan)
            block(k, pos[0], pos[1], atan2(tan[1], tan[0]))
        }
    }

    private fun beadCount(s: Strand, spacingDp: Float): Int =
        maxOf(3, (s.chainLen / (spacingDp * density)).toInt())

    private fun drawChain(canvas: Canvas, s: Strand) {
        // Smooth curve through the points.
        path.reset()
        path.moveTo(s.x[0], s.y[0])
        for (i in 1 until N - 1) {
            val mx = (s.x[i] + s.x[i + 1]) / 2f
            val my = (s.y[i] + s.y[i + 1]) / 2f
            path.quadTo(s.x[i], s.y[i], mx, my)
        }
        path.lineTo(s.x[N - 1], s.y[N - 1])

        val d = density
        when (style) {
            S_PEARLS -> {
                threadPaint.color = 0xFFC9C0B6.toInt()
                threadPaint.strokeWidth = 1.2f * d
                canvas.drawPath(path, threadPaint)
                beadEdge.color = 0xFFB9A9A9.toInt()
                along(path, beadCount(s, 8f)) { _, px, py, _ ->
                    beadFill.color = 0xFFF8F0EE.toInt()
                    canvas.drawCircle(px, py, 3.4f * d, beadFill)
                    canvas.drawCircle(px, py, 3.4f * d, beadEdge)
                    beadFill.color = 0xDDFFFFFF.toInt()
                    canvas.drawCircle(px - 1.1f * d, py - 1.1f * d, 1.1f * d, beadFill)
                }
            }
            S_MARIGOLD -> {
                threadPaint.color = 0xFF4F7D3A.toInt()
                threadPaint.strokeWidth = 1.6f * d
                canvas.drawPath(path, threadPaint)
                along(path, beadCount(s, 9.5f)) { k, px, py, ang ->
                    beadFill.color = if (k % 2 == 0) 0xFFFF9F1C.toInt() else 0xFFFFC93C.toInt()
                    for (j in 0 until 6) {
                        val a = ang + j * 1.0472f + k * 0.4f
                        canvas.drawCircle(px + cos(a) * 3f * d, py + sin(a) * 3f * d, 2.6f * d, beadFill)
                    }
                    beadFill.color = 0xFFB35C00.toInt()
                    canvas.drawCircle(px, py, 1.9f * d, beadFill)
                }
            }
            S_NEON -> {
                glowPaint.color = 0x2200E5FF
                glowPaint.strokeWidth = 13f * d
                canvas.drawPath(path, glowPaint)
                glowPaint.color = 0x5500E5FF
                glowPaint.strokeWidth = 7f * d
                canvas.drawPath(path, glowPaint)
                glowPaint.color = 0xFFE6FDFF.toInt()
                glowPaint.strokeWidth = 2.4f * d
                canvas.drawPath(path, glowPaint)
            }
            S_MALA -> {
                threadPaint.color = 0xFF5D4037.toInt()
                threadPaint.strokeWidth = 1.2f * d
                canvas.drawPath(path, threadPaint)
                beadEdge.color = 0xFF3E2723.toInt()
                along(path, beadCount(s, 8f)) { k, px, py, _ ->
                    if (k % 7 == 3) {
                        beadFill.color = 0xFFD4A017.toInt()
                        canvas.drawCircle(px, py, 4.4f * d, beadFill)
                        canvas.drawCircle(px, py, 4.4f * d, beadEdge)
                    } else {
                        beadFill.color = 0xFF6D4C2F.toInt()
                        canvas.drawCircle(px, py, 3.5f * d, beadFill)
                        canvas.drawCircle(px, py, 3.5f * d, beadEdge)
                        beadFill.color = 0x66FFFFFF
                        canvas.drawCircle(px - 1f * d, py - 1f * d, 1f * d, beadFill)
                    }
                }
            }
            else -> {
                canvas.drawPath(path, basePaint)
                canvas.drawPath(path, linkPaint)
            }
        }
    }
}
