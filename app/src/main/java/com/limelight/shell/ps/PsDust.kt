package com.limelight.shell.ps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The light dust of the PlayStation background (the approved prototype, variant "Drift"): motes carried through the air along
 * two slow curved streams, in six depth layers plus three layers of fine dust that fill the clumps; sharp near-white small motes
 * with rare sparkles and a four-point glint, fewer and fainter soft blue ones; twelve large out-of-focus lights drifting from
 * left to right and five giant, very soft lights far behind them.
 *
 * The prototype computes all of it per pixel. Here it is a particle system, in the prototype's own 1920 × 1080 picture space
 * ("picture px"; the canvas is scaled by its caller):
 * - **Population:** made once for a picture size. Each layer is a grid of cells in the stream's frame (x along the flow, y
 *   across the stream), one possible mote per cell with fixed random properties, as in the prototype. The grid repeats beyond
 *   the picture ([periodX], [periodY]); a cell whose mote could never pass its keep test is left out.
 * - **Keep test:** whether a mote shows follows two slow cloud fields and its distance from the streams, as in the prototype.
 *   The fields change slowly, so each mote is tested again every [KEEP_ROUNDS] frames only.
 * - **Drawing:** small motes and the faint halo of every mote are round points, sorted into batches by colour, size and
 *   brightness (one draw call per batch); the larger soft motes and all lights are sprites from one-channel bitmaps tinted by
 *   the paint. Everything adds up ([PorterDuff.Mode.ADD]).
 * - **Tone:** the prototype maps the summed light through 1 − exp(−1.35 c). Adding clips instead, so every element's own
 *   brightness goes through that curve before it is drawn: single lights match, overlapping ones add a little more.
 *
 * Differences from the prototype: its streams are tied to the drifting frame and leave the picture after about two minutes;
 * here they stay on the stream curves (the look at the prototype's start). The cloud fields repeat with the grid, which moves
 * their two finest scales by under 5 %. Random values come from another generator, so single motes and lights sit elsewhere.
 *
 * One thread draws ([draw]); the constructor may run on another.
 */
internal class PsDust(private val viewWidth: Float, private val viewHeight: Float, pxPerPicture: Float) {
    private val layers = LAYER_COUNT
    private val layerZ = FloatArray(layers)
    private val layerIndex = FloatArray(layers)
    private val layerFine = BooleanArray(layers)
    private val cell = FloatArray(layers)
    private val periodX = FloatArray(layers)
    private val periodY = FloatArray(layers)
    private val streamWidth = FloatArray(layers)
    private val sway = FloatArray(layers)
    // The cloud fields' lattice per layer: cells per period and lattice cells per picture px
    private val cloudX = IntArray(layers)
    private val cloudY = IntArray(layers)
    private val clumpX = IntArray(layers)
    private val clumpY = IntArray(layers)
    private val start = IntArray(layers + 1)

    /** The motes that can ever show. */
    val candidates: Int

    // Per mote, fixed: its place in the layer's frame, its keep threshold and kind, and what it looks like
    private val frameX: FloatArray
    private val frameY: FloatArray
    private val threshold: FloatArray
    private val kind: ByteArray
    private val swayA: FloatArray
    private val swayB: FloatArray
    private val twinkleSpeed: FloatArray
    private val twinklePhase: FloatArray
    private val brightBase: FloatArray
    private val coreAmp: FloatArray
    private val coreClass: ByteArray
    private val coreSigma: FloatArray
    private val colour: ByteArray
    private val haloAmp: FloatArray
    private val haloSigma: FloatArray
    private val haloInner: ByteArray
    private val haloOuter: ByteArray
    private val glintAmp: FloatArray
    // Per mote, from its last keep test: whether it shows and how strongly the streams light it
    private val kept: BooleanArray
    private val stream: FloatArray

    // The frame's points before and after sorting into batches
    private var pointBatch = IntArray(INITIAL_POINTS)
    private var pointX = FloatArray(INITIAL_POINTS)
    private var pointY = FloatArray(INITIAL_POINTS)
    private var sorted = FloatArray(INITIAL_POINTS * 2)
    private var points = 0
    private val batchCount = IntArray(BATCHES)
    private val batchStart = IntArray(BATCHES)
    private val glints = FloatArray(GLINT_LEVELS * GLINT_CAPACITY)
    private val glintCount = IntArray(GLINT_LEVELS)

    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; xfermode = add }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = GLINT_WIDTH; xfermode = add }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = add }
    private val rect = RectF()
    private val soft: Bitmap = softSprite()
    private val giant: Bitmap

    // The twelve large lights and the five giant ones
    private val lightBitmap = arrayOfNulls<Bitmap>(LIGHTS)
    private val lightZ = FloatArray(LIGHTS)
    private val lightSpeed = FloatArray(LIGHTS)
    private val lightLane = FloatArray(LIGHTS)
    private val lightOffset = FloatArray(LIGHTS)
    private val lightExtent = FloatArray(LIGHTS)
    private val lightColour = IntArray(LIGHTS)
    private val lightGlow = IntArray(LIGHTS)
    private val lightGlowSigma = FloatArray(LIGHTS)
    private val giantRadius = FloatArray(GIANTS)
    private val giantSpeed = FloatArray(GIANTS)
    private val giantOffset = FloatArray(GIANTS)
    private val giantLane = FloatArray(GIANTS)
    private val giantColour = IntArray(GIANTS)

    private var frame = 0

    /** What the last [draw] drew: points, sprites and glint lines. */
    var drawnPoints = 0
        private set
    var drawnSprites = 0
        private set
    var drawnGlints = 0
        private set

    init {
        // The grid's period: beyond the picture and a multiple of the largest cloud field's cell, so that field repeats exactly
        val spanX = ceil((viewWidth + 2 * MARGIN) / CLOUD_CELL_X) * CLOUD_CELL_X
        val spanY = ceil((viewHeight + STREAM_SWING * 2 + 2 * MARGIN) / CLOUD_CELL_Y) * CLOUD_CELL_Y
        for (l in 0 until layers) {
            val fine = l >= COARSE_LAYERS
            val z = if (fine) mix(.8f, 2.2f, (l - COARSE_LAYERS) / 2f) else mix(.45f, 3.6f, (l / (COARSE_LAYERS - 1f)).pow(1.3f))
            layerZ[l] = z
            layerIndex[l] = if (fine) l - COARSE_LAYERS + 10f else l.toFloat()
            layerFine[l] = fine
            cell[l] = (if (fine) 15f else 46f) / z
            periodX[l] = (spanX / cell[l]).roundToInt() * cell[l]
            periodY[l] = (spanY / cell[l]).roundToInt() * cell[l]
            streamWidth[l] = 210f / sqrt(z)
            sway[l] = min(WAVE / z, cell[l] * .3f)
            cloudX[l] = max(1, (periodX[l] * .0012f).roundToInt())
            cloudY[l] = max(1, (periodY[l] * .0025f).roundToInt())
            clumpX[l] = max(1, (periodX[l] * .0042f).roundToInt())
            clumpY[l] = max(1, (periodY[l] * .0062f).roundToInt())
        }
        // Two passes over the same cells: count the motes that can ever show, then fill their arrays
        var count = 0
        for (l in 0 until layers) {
            start[l] = count
            count += eachCell(l) { _, _, _, _, _, _, _, _ -> }
        }
        start[layers] = count
        candidates = count
        frameX = FloatArray(count); frameY = FloatArray(count); threshold = FloatArray(count); kind = ByteArray(count)
        swayA = FloatArray(count); swayB = FloatArray(count); twinkleSpeed = FloatArray(count); twinklePhase = FloatArray(count)
        brightBase = FloatArray(count); coreAmp = FloatArray(count); coreClass = ByteArray(count); coreSigma = FloatArray(count)
        colour = ByteArray(count); haloAmp = FloatArray(count); haloSigma = FloatArray(count); haloInner = ByteArray(count)
        haloOuter = ByteArray(count); glintAmp = FloatArray(count); kept = BooleanArray(count); stream = FloatArray(count)
        for (l in 0 until layers) {
            var i = start[l]
            val z = layerZ[l]
            val weight = if (layerFine[l]) .75f else mix(1f, .6f, l / (COARSE_LAYERS - 1f))
            val blur = (abs(1f / z - 1f) * .8f).coerceIn(.05f, 1f)
            eachCell(l) { x, y, rx, ry, k, small, sparkle, moteKind ->
                frameX[i] = x; frameY[i] = y; threshold[i] = rx; kind[i] = moteKind
                swayA[i] = ry * 30f
                swayB[i] = x * .004f + rx * 50f
                twinkleSpeed[i] = 1f + 3f * ry
                twinklePhase[i] = rx * 60f
                val spark = sparkle.pow(8) * 5f
                brightBase[i] = .25f + .5f * k + .25f * ry + spark
                val size = mix(.3f, 6f, k.pow(4)) / z
                val sigma = min(max(size * (1f + blur * 1.6f * (1f - small)), .5f), cell[l] * .12f)
                val dim = mix(.65f, 1f, small) * weight
                val amp = mix(1f / (1f + blur * blur * 3f), 1.6f, small) * dim
                // A small mote is a point with the energy of its Gaussian (radius √2 σ); a bigger one a soft sprite
                if (sigma <= POINT_SIGMA) {
                    val c = sizeClass(sigma * 2.83f)
                    coreClass[i] = c.toByte()
                    coreAmp[i] = amp * square(sigma * 2.83f / SIZES[c])
                } else {
                    coreClass[i] = -1
                    coreAmp[i] = amp
                }
                coreSigma[i] = sigma
                // The sharp motes are near-white; the others take one of the three tints
                colour[i] = (if (small >= .5f) 0 else 1 + tintIndex(ry * .9f + .05f)).toByte()
                // The halo: two rings of points with the energy of its Gaussian (diameters 2.2 σ and 4.4 σ)
                val halo = min(size * 4f + 3f, cell[l] * .13f)
                val inner = sizeClass(halo * 2.2f)
                val outer = sizeClass(halo * 4.4f)
                haloSigma[i] = halo
                haloInner[i] = inner.toByte()
                haloOuter[i] = outer.toByte()
                haloAmp[i] = .14f * (1f - .7f * blur) * dim
                glintAmp[i] = small * spark * .5f * dim
                i++
            }
        }
        // The first keep test of every mote, so the picture is complete from its first frame
        for (l in 0 until layers) for (i in start[l] until start[l + 1]) test(l, i, 0.0)

        val random = Random(SEED)
        for (i in 0 until LIGHTS) {
            val z = mix(.35f, 3.2f, random.nextFloat())
            val r0 = 16f + 18f * random.nextFloat()
            val r = r0 / z
            val blur = (abs(1f / z - 1f) * .9f).coerceIn(.04f, 1f)
            val edge = r * (.12f + blur * .8f)
            val bright = .55f + .45f * random.nextFloat()
            val tint = tintIndex(random.nextFloat())
            lightZ[i] = z
            lightSpeed[i] = (26f + 40f * random.nextFloat()) / z
            lightLane[i] = random.nextFloat()
            lightOffset[i] = random.nextFloat()
            lightExtent[i] = r + edge
            lightBitmap[i] = lightSprite(r, edge, pxPerPicture)
            lightColour[i] = lit(1 + tint, bright * mix(1f, .45f, blur) * (r0 * r0 / (r * r)).coerceIn(.22f, 1f))
            lightGlow[i] = lit(1 + tint, bright * .35f * mix(1f, .5f, blur))
            lightGlowSigma[i] = r * 2.4f + 6f
        }
        for (i in 0 until GIANTS) {
            giantRadius[i] = 121f + 99f * random.nextFloat()
            giantSpeed[i] = 6f + 6f * random.nextFloat()
            giantOffset[i] = random.nextFloat()
            giantLane[i] = random.nextFloat()
            giantColour[i] = lit(1 + tintIndex(random.nextFloat()), GIANT_PEAK * (.6f + .4f * random.nextFloat()))
        }
        giant = giantSprite(pxPerPicture)
    }

    /**
     * Calls [mote] for every cell of layer [l] whose mote can ever show (its frame position, its two random values, its size
     * value k, how small it is (1 sharp dust … 0 soft), its sparkle value and its kind) and returns how many there are.
     */
    private inline fun eachCell(l: Int, mote: (Float, Float, Float, Float, Float, Float, Float, Byte) -> Unit): Int {
        val size = cell[l]
        val nx = (periodX[l] / size).roundToInt()
        val ny = (periodY[l] / size).roundToInt()
        val fine = layerFine[l]
        var count = 0
        for (cy in 0 until ny) for (cx in 0 until nx) {
            val rx = hash(cx, cy, l * 8)
            val hk = hash(cx, cy, l * 8 + 1)
            val k = if (fine) hk * .3f else hk
            val small = 1f - smoothstep(.15f, .55f, k)
            val moteKind = if (fine) FINE else if (small > .5f) SMALL else if (k > .55f) LARGE else MEDIUM
            // The most its keep test can ever give (streams and cloud fields at 1): a mote above it never shows
            val most = when (moteKind) { FINE -> .139f; SMALL -> .0026f + .593f; LARGE -> .52f * .7f; else -> .52f * .103f }
            if (rx > most) continue
            mote((cx + .2f + .6f * hash(cx, cy, l * 8 + 2)) * size, (cy + .2f + .6f * hash(cx, cy, l * 8 + 3)) * size,
                rx, hash(cx, cy, l * 8 + 4), k, small, hash(cx, cy, l * 8 + 5), moteKind)
            count++
        }
        return count
    }

    /** The keep test of mote [i] of layer [l] at time [t], as the prototype's: the streams, the cloud and the clump field. */
    private fun test(l: Int, i: Int, t: Double) {
        val z = layerZ[l]
        val li = layerIndex[l]
        val v = across(l, frameY[i], t)
        val width = streamWidth[l]
        val main = exp(-v * v / (2f * width * width))
        val second = exp(-square(v + 330f / z) / (2f * square(width * .55f))) * .8f
        val inStream = max(main, second)
        val x = frameX[i]
        val y = frameY[i]
        val clumpField = noise(x * clumpX[l] / periodX[l] + li * 7.1f, y * clumpY[l] / periodY[l] + (t * .03).toFloat(), clumpX[l], clumpY[l])
        val keep = when (kind[i]) {
            FINE -> .139f * (.2f + .8f * inStream) * square(smoothstep(.62f, .84f, clumpField))
            SMALL -> .0026f + .593f * (.3f + .7f * inStream) * smoothstep(.3f, .62f, clumpField).pow(1.2f) * (.6f + .4f * cloud(l, x, y, t))
            LARGE -> (.02f + .5f * inStream * cloud(l, x, y, t)) * .7f
            else -> (.02f + .5f * inStream * cloud(l, x, y, t)) * .103f
        }
        kept[i] = threshold[i] <= keep
        stream[i] = .2f + .8f * inStream
    }

    private fun cloud(l: Int, x: Float, y: Float, t: Double): Float {
        val n = noise(x * cloudX[l] / periodX[l] + (t * .015).toFloat(), y * cloudY[l] / periodY[l] + layerIndex[l] * 3f, cloudX[l], cloudY[l])
        return smoothstep(.3f, .78f, n).pow(1.6f)
    }

    /** A mote's offset across the stream at time [t]: its frame position carried down by the flow, repeating beyond the picture. */
    private fun across(l: Int, y: Float, t: Double): Float {
        val period = periodY[l]
        var v = y + ((FLOW_Y * t / layerZ[l]) % period).toFloat()
        if (v >= period) v -= period
        return v - STREAM_CENTRE * viewHeight - STREAM_SWING - MARGIN
    }

    /** Draws the lights and the dust at time [t] (seconds) into [canvas], whose units are picture px. */
    fun draw(canvas: Canvas, t: Double) {
        points = 0
        drawnSprites = 0
        glintCount.fill(0)
        drawLights(canvas, t)
        val round = frame++ and (KEEP_ROUNDS - 1)
        val centre = STREAM_CENTRE * viewHeight
        for (l in 0 until layers) {
            val z = layerZ[l]
            val li = layerIndex[l]
            val px = periodX[l]
            val py = periodY[l]
            val flowX = ((FLOW_X * t / z) % px).toFloat()
            val flowY = ((FLOW_Y * t / z) % py).toFloat()
            val top = -centre - STREAM_SWING - MARGIN
            val amp = sway[l]
            val swayX = t * .35
            val swayY = t * .5
            val wave1 = t * .09 + li * .4
            val wave2 = -t * .06 + li
            val bend = .0022f / z
            for (i in start[l] until start[l + 1]) {
                if ((i and (KEEP_ROUNDS - 1)) == round) test(l, i, t)
                if (!kept[i]) continue
                var x = frameX[i] + flowX
                if (x >= px) x -= px
                x += wave(swayX + swayA[i]) * amp - MARGIN
                if (x < -EDGE || x > viewWidth + EDGE) continue
                var v = frameY[i] + flowY
                if (v >= py) v -= py
                val y = v + top + wave(swayY + swayB[i] - QUARTER) * amp + centre + 170f * wave(x * bend + wave1 - QUARTER) + 70f * wave(x * .0057f + wave2 - QUARTER)
                if (y < -EDGE || y > viewHeight + EDGE) continue
                val twinkle = .5f + .5f * wave(t * twinkleSpeed[i] + twinklePhase[i] - QUARTER)
                val bright = stream[i] * brightBase[i] * twinkle
                val c = colour[i].toInt()
                val core = coreClass[i].toInt()
                if (core >= 0) point(c, core, coreAmp[i] * bright, x, y)
                else sprite(canvas, soft, x, y, coreSigma[i] * SOFT_REACH, c, tone(coreAmp[i] * bright))
                val halo = haloAmp[i] * bright
                if (halo > HALO_SPRITE) sprite(canvas, soft, x, y, haloSigma[i] * SOFT_REACH, c, tone(halo))
                else {
                    point(c, haloInner[i].toInt(), halo * .6f, x, y)
                    point(c, haloOuter[i].toInt(), halo * .3f, x, y)
                }
                val flare = glintAmp[i] * twinkle
                if (flare > GLINT_LEAST) glint(x, y, flare, GLINT_REACH / z)
            }
        }
        drawPoints(canvas)
        drawGlints(canvas)
    }

    /** The large out-of-focus lights (a disc with a brighter rim and a wide glow) and the giant soft ones behind them. */
    private fun drawLights(canvas: Canvas, t: Double) {
        val spanX = viewWidth + 680.0
        for (i in 0 until LIGHTS) {
            val z = lightZ[i]
            val x = ((lightOffset[i] * spanX + t * lightSpeed[i]) % spanX - 340.0).toFloat()
            val y = lightLane[i] * (viewHeight + 220f) - 110f + wave(x * .004f / z + i - QUARTER) * 70f / z + wave(t * .4 + i * 1.7 - QUARTER) * 18f / z
            spritePaint.color = lightGlow[i]
            val glow = lightGlowSigma[i] * SOFT_REACH
            rect.set(x - glow, y - glow, x + glow, y + glow)
            canvas.drawBitmap(soft, null, rect, spritePaint)
            spritePaint.color = lightColour[i]
            val extent = lightExtent[i]
            rect.set(x - extent, y - extent, x + extent, y + extent)
            canvas.drawBitmap(lightBitmap[i]!!, null, rect, spritePaint)
        }
        val spanGiant = viewWidth + 980.0
        for (i in 0 until GIANTS) {
            val r = giantRadius[i]
            val x = ((giantOffset[i] * spanGiant + t * giantSpeed[i]) % spanGiant - 480.0).toFloat()
            val y = (140f + 800f * giantLane[i]) / 1080f * viewHeight + wave(t * .07 + i + 40.0 - QUARTER) * 60f
            spritePaint.color = giantColour[i]
            rect.set(x - r, y - r, x + r, y + r)
            canvas.drawBitmap(giant, null, rect, spritePaint)
        }
        drawnSprites += LIGHTS * 2 + GIANTS
    }

    /** A point of colour class [c] and size class [size] with light [amp] (before the tone curve) for this frame's batches. */
    private fun point(c: Int, size: Int, amp: Float, x: Float, y: Float) {
        val level = level(tone(amp))
        if (level < 0) return
        if (points == pointBatch.size) {
            pointBatch = pointBatch.copyOf(points * 2)
            pointX = pointX.copyOf(points * 2)
            pointY = pointY.copyOf(points * 2)
            sorted = FloatArray(points * 4)
        }
        pointBatch[points] = (c * SIZES.size + size) * LEVELS + level
        pointX[points] = x
        pointY[points] = y
        points++
    }

    /** The frame's points sorted by batch, one draw call per batch that has any. */
    private fun drawPoints(canvas: Canvas) {
        batchCount.fill(0)
        for (i in 0 until points) batchCount[pointBatch[i]]++
        var at = 0
        for (b in 0 until BATCHES) { batchStart[b] = at; at += batchCount[b] }
        for (i in 0 until points) {
            val slot = batchStart[pointBatch[i]]++ * 2
            sorted[slot] = pointX[i]
            sorted[slot + 1] = pointY[i]
        }
        var from = 0
        for (b in 0 until BATCHES) {
            val count = batchCount[b]
            if (count == 0) continue
            pointPaint.strokeWidth = SIZES[b / LEVELS % SIZES.size]
            pointPaint.color = BATCH_COLOUR[b / (LEVELS * SIZES.size) * LEVELS + b % LEVELS]
            canvas.drawPoints(sorted, from * 2, count * 2, pointPaint)
            from += count
        }
        drawnPoints = points
    }

    /** A sparkle's four-point glint: a short horizontal and a short vertical stroke. */
    private fun glint(x: Float, y: Float, amp: Float, reach: Float) {
        val level = ((1f - tone(amp * GLINT_MEAN)) * GLINT_LEVELS).toInt().coerceIn(0, GLINT_LEVELS - 1)
        val count = glintCount[level]
        if (count + 8 > GLINT_CAPACITY) return
        val at = level * GLINT_CAPACITY + count
        val half = min(GLINT_HALF, reach)
        glints[at] = x - half; glints[at + 1] = y; glints[at + 2] = x + half; glints[at + 3] = y
        glints[at + 4] = x; glints[at + 5] = y - half; glints[at + 6] = x; glints[at + 7] = y + half
        glintCount[level] = count + 8
    }

    private fun drawGlints(canvas: Canvas) {
        var lines = 0
        for (level in 0 until GLINT_LEVELS) {
            val count = glintCount[level]
            if (count == 0) continue
            linePaint.color = grey(0, 1f - (level + .5f) / GLINT_LEVELS)
            canvas.drawLines(glints, level * GLINT_CAPACITY, count, linePaint)
            lines += count / 4
        }
        drawnGlints = lines
    }

    private fun sprite(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, half: Float, c: Int, light: Float) {
        if (light < LEAST) return
        spritePaint.color = grey(c, light)
        rect.set(x - half, y - half, x + half, y + half)
        canvas.drawBitmap(bitmap, null, rect, spritePaint)
        drawnSprites++
    }

    private companion object {
        const val COARSE_LAYERS = 6
        const val LAYER_COUNT = COARSE_LAYERS + 3
        const val LIGHTS = 12
        const val GIANTS = 5
        const val SEED = 20261123

        /** The prototype's flow (picture px per second at depth 1), its sway and where the main stream runs (of the height). */
        const val FLOW_X = 34.0
        const val FLOW_Y = 6.0
        const val WAVE = 40f
        const val STREAM_CENTRE = 600f / 1080f

        /** How far the stream curve swings from its centre (170 + 70), and the room kept around the picture for a mote's light. */
        const val STREAM_SWING = 240f
        const val MARGIN = 80f
        const val EDGE = 40f

        /** The largest cloud field's cell (1 / .0012 and 1 / .0025 picture px): the grid's period is a multiple of it. */
        const val CLOUD_CELL_X = 1f / .0012f
        const val CLOUD_CELL_Y = 1f / .0025f

        /** Each mote's keep test runs once in this many frames (a power of two): the fields it reads move slowly. */
        const val KEEP_ROUNDS = 32

        const val FINE: Byte = 0
        const val SMALL: Byte = 1
        const val MEDIUM: Byte = 2
        const val LARGE: Byte = 3

        /** A core up to this σ (picture px) is a point; a sprite reaches this many σ; a halo brighter than this is a sprite. */
        const val POINT_SIGMA = 1.2f
        const val SOFT_REACH = 3.3f
        const val HALO_SPRITE = .12f

        /** Light under this (after the tone curve, about 2 / 255) is not drawn. */
        const val LEAST = .008f

        /** The point sizes (diameters, picture px) and brightness levels of the batches: both ladders are geometric. */
        val SIZES = FloatArray(13) { 1.4f * 1.25f.pow(it) }
        const val LEVELS = 14
        const val LEVEL_STEP = .7f
        const val COLOURS = 4
        val BATCHES = COLOURS * SIZES.size * LEVELS
        const val INITIAL_POINTS = 1 shl 15

        /** The glint: strokes this wide and at most this long each way, brighter than this only, in this many levels. */
        const val GLINT_WIDTH = 1.4f
        const val GLINT_HALF = 2.5f
        const val GLINT_REACH = 14f
        const val GLINT_LEAST = .06f
        const val GLINT_MEAN = .6f
        const val GLINT_LEVELS = 8
        const val GLINT_CAPACITY = 2048

        /** The giant lights' peak: the prototype's disc (.065) plus rim (.05). */
        const val GIANT_PEAK = .115f

        /** The colour classes: the sharp motes' near-white, then the three tints (PlayStation blue, cyan, white-violet). */
        val RED = floatArrayOf(.92f, .24f, .259f, .582f)
        val GREEN = floatArrayOf(.96f, .569f, .909f, .608f)
        val BLUE = floatArrayOf(1f, 1f, .981f, .978f)
        val BATCH_COLOUR = IntArray(COLOURS * LEVELS) { grey(it / LEVELS, LEVEL_STEP.pow(it % LEVELS)) }

        // A sine table: the per-mote waves need no more
        const val TABLE = 4096
        val TURN = (TABLE / (2 * Math.PI)).toFloat()
        val SINE = FloatArray(TABLE) { sin(it * 2 * Math.PI / TABLE).toFloat() }
        /** A quarter turn (radians): [wave] is a cosine, so a sine is `wave(x - QUARTER)`. */
        val QUARTER = (Math.PI / 2).toFloat()

        /** cos([x]) from the table. */
        fun wave(x: Float): Float = SINE[((x * TURN).toInt() + TABLE / 4) and (TABLE - 1)]
        fun wave(x: Double): Float = SINE[((x * TURN).toLong().toInt() + TABLE / 4) and (TABLE - 1)]

        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun square(x: Float) = x * x
        fun smoothstep(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** The prototype's tone curve. */
        fun tone(light: Float): Float = 1f - exp(-1.35f * light)

        /** The brightness level of [light] (after the tone curve) on the batches' ladder, or −1 when it is too faint to draw. */
        fun level(light: Float): Int {
            if (light < LEAST) return -1
            return (ln(light) / ln(LEVEL_STEP)).roundToInt().coerceIn(0, LEVELS - 1)
        }

        /** The size class nearest to a diameter (picture px). */
        fun sizeClass(diameter: Float): Int = (ln(diameter / SIZES[0]) / ln(1.25f)).roundToInt().coerceIn(0, SIZES.size - 1)

        /** The prototype's tint choice: 0 PlayStation blue, 1 cyan, 2 white-violet. */
        fun tintIndex(k: Float): Int = if (k < .45f) 0 else if (k < .8f) 1 else 2

        /** Colour class [c] at [light] (already on the tone curve) as an opaque colour: adding it adds that much light. */
        fun grey(c: Int, light: Float): Int {
            val l = light.coerceIn(0f, 1f) * 255f
            return (0xFF shl 24) or ((RED[c] * l).toInt() shl 16) or ((GREEN[c] * l).toInt() shl 8) or (BLUE[c] * l).toInt()
        }

        /** Colour class [c] with light [amp] through the tone curve, per channel. */
        fun lit(c: Int, amp: Float): Int =
            (0xFF shl 24) or ((tone(RED[c] * amp) * 255f).toInt() shl 16) or ((tone(GREEN[c] * amp) * 255f).toInt() shl 8) or (tone(BLUE[c] * amp) * 255f).toInt()

        /** A value in 0 … 1 for a lattice point: an integer hash (the prototype's sine hash needs its exact float arithmetic). */
        fun hash(x: Int, y: Int, seed: Int): Float {
            var h = x * 374761393 + y * 668265263 + seed * 1274126177
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFFFF) / 16777216f
        }

        /** Value noise as the prototype's, on a lattice that repeats every [nx] × [ny] cells. */
        fun noise(x: Float, y: Float, nx: Int, ny: Int): Float {
            val fx = floor(x)
            val fy = floor(y)
            val ix = Math.floorMod(fx.toInt(), nx)
            val iy = Math.floorMod(fy.toInt(), ny)
            val jx = if (ix + 1 == nx) 0 else ix + 1
            val jy = if (iy + 1 == ny) 0 else iy + 1
            var u = x - fx
            var v = y - fy
            u = u * u * (3f - 2f * u)
            v = v * v * (3f - 2f * v)
            val a = hash(ix, iy, 977)
            val b = hash(jx, iy, 977)
            val c = hash(ix, jy, 977)
            val d = hash(jx, jy, 977)
            return mix(mix(a, b, u), mix(c, d, u), v)
        }

        /** A Gaussian that reaches zero at the bitmap's edge ([SOFT_REACH] σ), one channel: the paint's colour tints it. */
        fun softSprite(): Bitmap {
            val size = 128
            val bytes = ByteArray(size * size)
            val floor = exp(-SOFT_REACH * SOFT_REACH / 2f)
            for (y in 0 until size) for (x in 0 until size) {
                val dx = (x + .5f - size / 2f) / (size / 2f) * SOFT_REACH
                val dy = (y + .5f - size / 2f) / (size / 2f) * SOFT_REACH
                val value = (exp(-(dx * dx + dy * dy) / 2f) - floor) / (1f - floor)
                bytes[y * size + x] = (value.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
            return alphaBitmap(size, bytes)
        }

        /** One large light at the device's resolution: the prototype's disc with its soft edge and brighter rim. */
        fun lightSprite(r: Float, edge: Float, pxPerPicture: Float): Bitmap {
            val extent = r + edge
            val size = max(8, ceil(extent * 2f * pxPerPicture).toInt())
            val bytes = ByteArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val dx = (x + .5f - size / 2f) / (size / 2f) * extent
                val dy = (y + .5f - size / 2f) / (size / 2f) * extent
                val d = sqrt(dx * dx + dy * dy)
                val disc = 1f - smoothstep(r - edge, r + edge, d)
                val rim = smoothstep(r - edge * 1.6f, r, d) * disc
                bytes[y * size + x] = (disc * (.55f + .45f * rim) * 255f).toInt().toByte()
            }
            return alphaBitmap(size, bytes)
        }

        /** The giant lights' shared shape (it scales with their radius), at the largest one's resolution on the device. */
        fun giantSprite(pxPerPicture: Float): Bitmap {
            val size = max(8, ceil(440f * pxPerPicture).toInt())
            val bytes = ByteArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val dx = (x + .5f - size / 2f) / (size / 2f)
                val dy = (y + .5f - size / 2f) / (size / 2f)
                val d = sqrt(dx * dx + dy * dy)
                val disc = 1f - smoothstep(.78f, 1f, d)
                val rim = smoothstep(.72f, .94f, d) * disc
                bytes[y * size + x] = ((disc * .065f + rim * .05f) / GIANT_PEAK * 255f).toInt().toByte()
            }
            return alphaBitmap(size, bytes)
        }

        fun alphaBitmap(size: Int, bytes: ByteArray): Bitmap =
            Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).apply { copyPixelsFromBuffer(ByteBuffer.wrap(bytes)) }
    }
}
