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
        val bitmap = Bitmap.createBitmap(frame.width(), frame.height(), Bitmap.Config.ARGB_8888)
        busy = true
        try {
            PixelCopy.request(surface, bitmap, { result ->
                scope.launch {
                    val saved = try {
                        result == PixelCopy.SUCCESS && withContext(Dispatchers.IO) { save(context.applicationContext, bitmap) }
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
