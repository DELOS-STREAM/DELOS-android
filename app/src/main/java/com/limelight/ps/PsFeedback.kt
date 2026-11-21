package com.limelight.ps

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** The controller state the console sees (8.1). Sticks are signed 16-bit with Y positive = down; gyro in rad/s, accel in g. */
data class PsControllerState(
    val buttons: Int = 0,
    val l2: Int = 0,
    val r2: Int = 0,
    val lx: Short = 0, val ly: Short = 0, val rx: Short = 0, val ry: Short = 0,
    val gyroX: Float = 0f, val gyroY: Float = 0f, val gyroZ: Float = 0f,
    val accelX: Float = 0f, val accelY: Float = 1f, val accelZ: Float = 0f,
    val orientX: Float = 0f, val orientY: Float = 0f, val orientZ: Float = 0f, val orientW: Float = 1f,
    val touches: List<PsTouch> = emptyList(),
)

data class PsTouch(val id: Int, val x: Int, val y: Int)

/** Digital buttons, one bit each, and their FEEDBACK_HISTORY codes (8.3): pressed = 80 code FF / 80 code+0x20, released = 00 / code. */
enum class PsButton(val bit: Int, val code: Int, val toggle: Boolean) {
    CROSS(0, 0x88, false), CIRCLE(1, 0x89, false), SQUARE(2, 0x8A, false), TRIANGLE(3, 0x8B, false),
    DPAD_UP(4, 0x80, false), DPAD_DOWN(5, 0x81, false), DPAD_LEFT(6, 0x82, false), DPAD_RIGHT(7, 0x83, false),
    L1(8, 0x84, false), R1(9, 0x85, false),
    L3(10, 0x8F, true), R3(11, 0x90, true), OPTIONS(12, 0x8C, true), SHARE(13, 0x8D, true), TOUCHPAD(14, 0x91, true), PS(15, 0x8E, true);

    val mask: Int get() = 1 shl bit

    /** The event: value buttons send 80 code SS, toggle buttons send 80 (code + 0x20) on press and 80 code on release. */
    fun event(pressed: Boolean): ByteArray =
        if (toggle) byteArrayOf(0x80.toByte(), (if (pressed) code + 0x20 else code).toByte())
        else byteArrayOf(0x80.toByte(), code.toByte(), if (pressed) 0xFF.toByte() else 0)
}

object PsFeedback {
    /** The state bytes of a FEEDBACK_STATE packet before encryption (8.2): 25 bytes for Takion v9, 28 for v12. */
    fun state(s: PsControllerState, v12: Boolean): ByteArray {
        val b = ByteBuffer.allocate(if (v12) 28 else 25)
        b.put(0xA0.toByte())
        b.order(ByteOrder.LITTLE_ENDIAN)
        for (v in floatArrayOf(s.gyroX, s.gyroY, s.gyroZ)) b.putShort(scale(v, 30f))
        for (v in floatArrayOf(s.accelX, s.accelY, s.accelZ)) b.putShort(scale(v, 5f))
        b.putInt(orientation(s.orientX, s.orientY, s.orientZ, s.orientW))
        b.order(ByteOrder.BIG_ENDIAN)
        b.putShort(s.lx); b.putShort(s.ly); b.putShort(s.rx); b.putShort(s.ry)
        if (v12) b.put(byteArrayOf(0, 0, 1))
        return b.array()
    }

    private fun scale(v: Float, range: Float): Short = (65535.0 * (v + range) / (2 * range)).toLong().coerceIn(0, 65535).toInt().toShort()

    /** "Smallest three" compression of a unit quaternion (8.2). */
    fun orientation(x: Float, y: Float, z: Float, w: Float): Int {
        val q = floatArrayOf(x, y, z, w)
        var largest = 0
        for (i in 1 until 4) if (abs(q[i]) > abs(q[largest])) largest = i
        var out = (if (q[largest] < 0) 1 else 0) or (largest shl 1)
        val half = (1.0 / sqrt(2.0))
        var k = 0
        for (i in 0 until 4) {
            if (i == largest) continue
            val c = q[i].toDouble().coerceIn(-half, half)
            val v = ((c + half) * (511.0 / (2.0 * half))).toInt() and 0x1FF
            out = out or (v shl (3 + 9 * k))
            k++
        }
        return out
    }

    /** A touch event (8.3): D0 down/moved or C0 up, id & 0x7F, then X and Y as 12 bits each. */
    fun touchEvent(down: Boolean, id: Int, x: Int, y: Int): ByteArray {
        val xx = x.coerceIn(0, 0xFFF)
        val yy = y.coerceIn(0, 0xFFF)
        return byteArrayOf(if (down) 0xD0.toByte() else 0xC0.toByte(), (id and 0x7F).toByte(), (xx shr 4).toByte(), (((xx and 0xF) shl 4) or (yy shr 8)).toByte(), yy.toByte())
    }

    /**
     * The events between two states, in the order the console expects (8.3): releases, touch changes, presses, L2, R2.
     */
    fun events(old: PsControllerState, new: PsControllerState): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        for (b in PsButton.entries) if (old.buttons and b.mask != 0 && new.buttons and b.mask == 0) out += b.event(false)
        val oldTouches = old.touches.associateBy { it.id }
        val newTouches = new.touches.associateBy { it.id }
        for ((id, t) in oldTouches) if (id !in newTouches) out += touchEvent(false, id, t.x, t.y)
        for ((id, t) in newTouches) if (oldTouches[id] != t) out += touchEvent(true, id, t.x, t.y)
        for (b in PsButton.entries) if (old.buttons and b.mask == 0 && new.buttons and b.mask != 0) out += b.event(true)
        if (old.l2 != new.l2) out += byteArrayOf(0x80.toByte(), 0x86.toByte(), new.l2.coerceIn(0, 255).toByte())
        if (old.r2 != new.r2) out += byteArrayOf(0x80.toByte(), 0x87.toByte(), new.r2.coerceIn(0, 255).toByte())
        return out
    }
}
