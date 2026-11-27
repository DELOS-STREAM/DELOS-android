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

    @JvmStatic
    fun capture(context: Context, view: SurfaceView?, onResult: java.util.function.Consumer<Boolean>) {
        if (busy) return
        val holder = view?.holder
        val surface = holder?.surface
        val frame = holder?.surfaceFrame
        if (surface == null || !surface.isValid || frame == null || frame.isEmpty) {
            onResult.accept(false)
            return
        }
        // Copied into a wide, extended-range bitmap: the readback converts an HDR picture (PQ/HLG) into it instead of writing
        // its code values as sRGB, which made an HDR stream's screenshot look washed out (owner, 2026-11-27); [toSdr] then
        // rolls the highlights above SDR white off softly into an ordinary 8-bit sRGB PNG.
        val bitmap = Bitmap.createBitmap(frame.width(), frame.height(), Bitmap.Config.RGBA_F16, false,
            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB))
        busy = true
        try {
            PixelCopy.request(surface, bitmap, { result ->
                scope.launch {
                    val saved = try {
                        result == PixelCopy.SUCCESS && withContext(Dispatchers.IO) { toSdr(bitmap).let { sdr ->
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
     * The extended-range copy as 8-bit sRGB: values up to 0.8 stay as they are, brighter ones (HDR highlights above SDR white,
     * which the conversion puts above 1.0) bend smoothly towards 1.0 instead of clipping. SDR pictures pass unchanged.
     */
    // The shorts are the bitmap's half floats, read through Half.toFloat as lint asks; its check misreads the ShortBuffer
    @android.annotation.SuppressLint("HalfFloat")
    private fun toSdr(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val halfs = java.nio.ByteBuffer.allocateDirect(w * h * 8).order(java.nio.ByteOrder.nativeOrder())
        source.copyPixelsToBuffer(halfs)
        halfs.rewind()
        val shorts = halfs.asShortBuffer()
        val out = IntArray(w * h)
        fun channel(v: Float): Int {
            val k = if (v <= .8f) v.coerceAtLeast(0f) else .8f + .2f * (1f - kotlin.math.exp(-(v - .8f) / .2f))
            return (k * 255f + .5f).toInt().coerceIn(0, 255)
        }
        for (i in 0 until w * h) {
            val r = android.util.Half.toFloat(shorts.get(i * 4))
            val g = android.util.Half.toFloat(shorts.get(i * 4 + 1))
            val b = android.util.Half.toFloat(shorts.get(i * 4 + 2))
            out[i] = (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
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
