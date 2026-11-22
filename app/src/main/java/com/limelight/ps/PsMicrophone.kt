package com.limelight.ps

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log

/** The Opus encoder of the microphone (ps_opus.c): 48 kHz mono, restricted low delay, constant bitrate. */
internal class PsOpusEncoder(sampleRate: Int, bitrate: Int) : AutoCloseable {
    private var handle = create(sampleRate, bitrate).also { if (it == 0L) throw IllegalStateException("opus encoder") }

    /** One frame of [frameSize] samples into [out]; the packet length, or a negative Opus error. */
    fun encode(pcm: ShortArray, frameSize: Int, out: ByteArray): Int = encode(handle, pcm, frameSize, out)

    override fun close() {
        destroy(handle)
        handle = 0
    }

    private companion object {
        init { System.loadLibrary("moonlight-core") }

        @JvmStatic external fun create(sampleRate: Int, bitrate: Int): Long
        @JvmStatic external fun encode(handle: Long, pcm: ShortArray, frameSize: Int, out: ByteArray): Int
        @JvmStatic external fun destroy(handle: Long)
    }
}

/**
 * The device microphone for the console's voice chat (spec 8.5, issue #124): 10 ms frames of 48 kHz mono from the voice
 * source (with the platform's echo cancelling), each encoded as a 40-byte Opus packet and handed to [send]. Only packets of
 * exactly that size go out (the console drops others). The caller holds the record-audio permission.
 */
internal class PsMicrophone(private val send: (ByteArray) -> Unit) : AutoCloseable {
    @Volatile private var active = true
    @Volatile var muted = false
    private val worker = Thread(::run, "ps-microphone")

    fun start() = worker.start()

    @SuppressLint("MissingPermission")
    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, FRAME * 2 * 4))
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) { Log.w(TAG, "microphone unavailable"); record?.release(); return }
        val encoder = runCatching { PsOpusEncoder(RATE, BITRATE) }.getOrElse { Log.w(TAG, "encoder: ${it.message}"); record.release(); return }
        val pcm = ShortArray(FRAME)
        val packet = ByteArray(256)
        try {
            record.startRecording()
            Log.i(TAG, "microphone on")
            while (active) {
                var read = 0
                while (read < FRAME && active) {
                    val n = record.read(pcm, read, FRAME - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < FRAME || !active) continue
                if (muted) continue
                val length = encoder.encode(pcm, FRAME, packet)
                if (length == PACKET) send(packet.copyOf(PACKET))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            encoder.close()
        }
    }

    override fun close() {
        active = false
        runCatching { worker.join(300) }
    }

    companion object {
        private const val TAG = "PsMicrophone"
        private const val RATE = 48_000
        /** 10 ms at 48 kHz. */
        const val FRAME = 480
        private const val BITRATE = 32_000
        /** The only packet size the console takes (8.5). */
        const val PACKET = 40
    }
}
