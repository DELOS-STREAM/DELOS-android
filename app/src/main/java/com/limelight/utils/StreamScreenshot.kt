package com.limelight.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Copies only the video surface; the MediaStore entry stays hidden until PNG encoding succeeds. */
object StreamScreenshot {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private var busy = false

    /** [pq]: the stream runs in HDR (BT.2020 PQ): the copied values are PQ code values and are tone-mapped to SDR. */
    @JvmStatic
    fun capture(context: Context, view: SurfaceView?, pq: Boolean, onResult: java.util.function.Consumer<Boolean>) {
        if (busy) return
        val holder = view?.holder
        val surface = holder?.surface
        val frame = holder?.surfaceFrame
        if (surface == null || !surface.isValid || frame == null || frame.isEmpty) {
            onResult.accept(false)
            return
        }
        // An extended-range copy keeps an HDR picture's code values at full precision; the readback does not convert PQ,
        // so written as sRGB they looked washed out (owner, 2026-11-27): [toSdr] decodes and tone-maps them instead
        val bitmap = Bitmap.createBitmap(frame.width(), frame.height(), Bitmap.Config.RGBA_F16, false,
            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB))
        busy = true
        try {
            PixelCopy.request(surface, bitmap, { result ->
                scope.launch {
                    val saved = try {
                        result == PixelCopy.SUCCESS && withContext(Dispatchers.IO) { toSdr(bitmap, pq).let { sdr ->
                            try { save(context.applicationContext, sdr) } finally { sdr.recycle() }
                        } }
                    } finally {
                        bitmap.recycle()
                        busy = false
                    }
                    onResult.accept(saved)
                }
            }, main)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            busy = false
            onResult.accept(false)
        }
    }

    /**
     * The copy as 8-bit sRGB. SDR: as it is. HDR ([pq]): each PQ code value (SMPTE ST 2084) to light, SDR white at 203 nits
     * (ITU-R BT.2408) as 1.0, BT.2020 to BT.709 primaries, then an extended Reinhard curve on the luminance with white at 1000
     * nits, so highlights roll off instead of clipping and the contrast of an SDR picture stays; then the sRGB curve.
     */
    // The shorts are the bitmap's half floats, read through Half.toFloat as lint asks; its check misreads the ShortBuffer
    @android.annotation.SuppressLint("HalfFloat")
    internal fun toSdr(source: Bitmap, pq: Boolean): Bitmap {
        val w = source.width
        val h = source.height
        val halfs = java.nio.ByteBuffer.allocateDirect(w * h * 8).order(java.nio.ByteOrder.nativeOrder())
        source.copyPixelsToBuffer(halfs)
        halfs.rewind()
        val shorts = halfs.asShortBuffer()
        val out = IntArray(w * h)
        val toLinear = if (pq) PQ_LUT else null
        for (i in 0 until w * h) {
            var r = android.util.Half.toFloat(shorts.get(i * 4))
            var g = android.util.Half.toFloat(shorts.get(i * 4 + 1))
            var b = android.util.Half.toFloat(shorts.get(i * 4 + 2))
            if (toLinear != null) {
                val lr = toLinear[lutIndex(r)]; val lg = toLinear[lutIndex(g)]; val lb = toLinear[lutIndex(b)]
                // BT.2020 to BT.709 (linear)
                var r7 = 1.6605f * lr - .5876f * lg - .0728f * lb
                var g7 = -.1246f * lr + 1.1329f * lg - .0083f * lb
                var b7 = -.0182f * lr - .1006f * lg + 1.1187f * lb
                r7 = r7.coerceAtLeast(0f); g7 = g7.coerceAtLeast(0f); b7 = b7.coerceAtLeast(0f)
                val y = .2126f * r7 + .7152f * g7 + .0722f * b7
                val k = if (y <= 0f) 0f else (y * (1f + y / WHITE2) / (1f + y)) / y
                r = srgb(r7 * k); g = srgb(g7 * k); b = srgb(b7 * k)
            }
            out[i] = (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255f + .5f).toInt()
    private fun lutIndex(v: Float) = (v.coerceIn(0f, 1f) * (LUT_SIZE - 1) + .5f).toInt()

    private fun srgb(linear: Float): Float {
        val v = linear.coerceIn(0f, 1f)
        return if (v <= .0031308f) v * 12.92f else 1.055f * Math.pow(v.toDouble(), 1 / 2.4).toFloat() - .055f
    }

    private const val LUT_SIZE = 4096
    /** Reinhard's white: 1000 nits over SDR white at 203. */
    private const val WHITE2 = (1000f / 203f) * (1000f / 203f)

    /** PQ code value (0..1) to linear light relative to SDR white (203 nits). */
    private val PQ_LUT = FloatArray(LUT_SIZE) { i ->
        val e = Math.pow(i / (LUT_SIZE - 1.0), 1 / 78.84375)
        val nits = 10000 * Math.pow(maxOf(e - .8359375, 0.0) / (18.8515625 - 18.6875 * e), 1 / .1593017578125)
        (nits / 203).toFloat()
    }
    private fun save(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "DELOS_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DELOS")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        var published = false
        try {
            if (resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != true) return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            published = resolver.update(uri, values, null, null) == 1
            published
        } finally {
            if (!published) resolver.delete(uri, null, null)
        }
    }.getOrDefault(false)

}
