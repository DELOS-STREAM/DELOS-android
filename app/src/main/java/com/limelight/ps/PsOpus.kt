package com.limelight.ps

/**
 * An Opus decoder for the console's audio units (7.4), over the libopus in libmoonlight-core (jni/moonlight-core/ps_opus.c). A null
 * packet conceals a lost one (Opus PLC). Not thread-safe: one decoding thread per instance.
 */
class PsOpus(private val sampleRate: Int, val channels: Int) : AutoCloseable {
    private var handle = create(sampleRate, channels).also { require(it != 0L) { "Opus decoder for $sampleRate Hz, $channels channels" } }

    /** Decodes [packet] (null: conceal a loss) into [pcm], interleaved, [frameSize] samples per channel; returns the samples per channel. */
    fun decode(packet: ByteArray?, pcm: ShortArray, frameSize: Int): Int = decode(handle, packet, packet?.size ?: 0, pcm, frameSize)

    override fun close() {
        destroy(handle)
        handle = 0
    }

    private companion object {
        init { System.loadLibrary("moonlight-core") }

        @JvmStatic external fun create(sampleRate: Int, channels: Int): Long
        @JvmStatic external fun decode(handle: Long, packet: ByteArray?, length: Int, pcm: ShortArray, frameSize: Int): Int
        @JvmStatic external fun destroy(handle: Long)
    }
}
