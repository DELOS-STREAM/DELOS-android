package com.limelight.shell.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalArtworkCacheTest {
    private lateinit var context: Context
    private lateinit var store: ArtworkStore
    private lateinit var pc: Pc
    private val game = App(27, "Ordinary game", false, false, NvApp())

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ArtworkStore(context)
        pc = Pc("cache-${UUID.randomUUID()}", "PC", "PC", true, true, 0, false, ComputerDetails())
    }

    private fun file(kind: ArtKind) = File(context.filesDir, "artwork/${pc.uuid}/${game.id}/${kind.file}")
        .apply { parentFile!!.mkdirs() }

    private fun webp(width: Int, height: Int, animated: Boolean): ByteArray = ByteArray(30).also { b ->
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        "VP8X".toByteArray().copyInto(b, 12)
        b[16] = 10
        b[20] = if (animated) 2 else 0
        for ((offset, value) in listOf(24 to width - 1, 27 to height - 1)) {
            repeat(3) { b[offset + it] = (value shr (8 * it)).toByte() }
        }
    }

    private fun png(target: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    @Test fun animationCacheTracksTheChosenFileAndRejectsStillsAndHugeCanvases() = runBlocking {
        val kind = ArtKind.HERO
        val disk = file(kind)
        assertNull(store.cachedAnimatedFile(pc, game, kind))
        assertNull(store.animatedFile(pc, game, kind))
        disk.writeBytes(byteArrayOf())
        assertNull(store.animatedFile(pc, game, kind))
        disk.writeBytes(webp(640, 360, true))
        assertEquals(disk, store.animatedFile(pc, game, kind))
        assertEquals(disk, store.cachedAnimatedFile(pc, game, kind))
        assertEquals(disk, store.animatedFile(pc, game, kind))
        // A different file length invalidates even a same-timestamp replacement.
        disk.writeBytes(webp(640, 360, false) + byteArrayOf(0))
        assertNull(store.animatedFile(pc, game, kind))
        assertNull(store.cachedAnimatedFile(pc, game, kind))
        assertNull(store.animatedFile(pc, game, kind))
        disk.writeBytes(webp(10_001, 10_000, true) + byteArrayOf(0, 0))
        assertNull(store.animatedFile(pc, game, kind))
        val builtin = game.copy(name = "Desktop")
        assertNull(store.animatedFile(pc, builtin, kind))
        assertNull(store.cachedAnimatedFile(pc, builtin, kind))
    }

    @Test fun logosAndCoversDecodeFromDiskAndRemainAvailableFromMemory() = runBlocking {
        for (kind in listOf(ArtKind.LOGO, ArtKind.COVER, ArtKind.WIDE)) {
            png(file(kind), if (kind == ArtKind.LOGO) 24 else 2400, 40)
            assertNull(store.cached(pc, game, kind))
            val loaded = store.load(pc, game, kind)!!
            assertTrue(loaded.width > 0)
            assertSame(loaded, store.cached(pc, game, kind))
            assertTrue(file(kind).delete())
            assertSame(loaded, store.load(pc, game, kind))
        }
        file(ArtKind.HERO).writeText("broken image")
        assertNull(store.load(pc, game, ArtKind.HERO))
    }

    @Test fun hostBoxArtLoadsOnlyLocalFilesWithoutAClientIdentityAndCachesByPcAndApp() = runBlocking {
        val cache = BoxArtCache(context) { null }
        val disk = File(context.cacheDir, "boxart/${pc.uuid}/${game.id}.png").apply { parentFile!!.mkdirs() }
        assertNull(cache.cached(pc, game))
        assertNull(cache.load(pc, game))
        disk.writeBytes(byteArrayOf())
        assertNull(cache.load(pc, game))
        disk.writeText("not a PNG")
        assertNull(cache.load(pc, game))
        png(disk, 60, 90)
        val loaded = cache.load(pc, game)!!
        assertEquals(60, loaded.width)
        assertEquals(90, loaded.height)
        assertSame(loaded, cache.cached(pc, game))
        assertTrue(disk.delete())
        assertSame(loaded, cache.load(pc, game))
        assertNull(cache.cached(pc, game.copy(id = 28)))
        assertNull(cache.cached(pc.copy(uuid = "other-pc"), game))
    }
}
