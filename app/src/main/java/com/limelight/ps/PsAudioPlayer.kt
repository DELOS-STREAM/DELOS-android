package com.limelight.ps

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays the console's Opus units (7.4) with the platform decoder and an AudioTrack in low-latency mode. One instance per stream;
 * [header] is the 14-byte audio header of STREAMINFO (channels, bits, rate BE, frame size BE).
 */
class PsAudioPlayer(header: ByteArray) : AutoCloseable {
    val channels = u(header[0])
    val rate = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
    private val codec: MediaCodec
    private val track: AudioTrack
    private var lastFrame = -1

    init {
        require(channels in 1..2) { "audio with $channels channels needs the Opus multistream mapping (not done yet)" }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, rate, channels)
        val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
            .put("OpusHead".toByteArray(Charsets.US_ASCII)).put(1).put(channels.toByte()).putShort(0).putInt(rate).putShort(0).put(0)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(head.array()))
        format.setByteBuffer("csd-1", ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0).flip() as ByteBuffer)
        format.setByteBuffer("csd-2", ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(80_000_000L).flip() as ByteBuffer)
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply { configure(format, null, null, 0); start() }
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, rate / 100 * channels * 2 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
    }

    /** One Opus packet for frame [frameIndex]; repeats of frames already played are dropped. */
    @Synchronized
    fun play(opus: ByteArray, frameIndex: Int) {
        if (lastFrame >= 0 && ((frameIndex - lastFrame) and 0xFFFF) > 0x8000) return
        if (frameIndex == lastFrame) return
        lastFrame = frameIndex
        try {
            val i = codec.dequeueInputBuffer(5_000)
            if (i >= 0) { codec.getInputBuffer(i)!!.apply { clear(); put(opus) }; codec.queueInputBuffer(i, 0, opus.size, 0, 0) }
            val info = MediaCodec.BufferInfo()
            var o = codec.dequeueOutputBuffer(info, 0)
            while (o >= 0) {
                val out = codec.getOutputBuffer(o)!!
                val pcm = ByteArray(info.size)
                out.position(info.offset); out.get(pcm)
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
                codec.releaseOutputBuffer(o, false)
                o = codec.dequeueOutputBuffer(info, 0)
            }
        } catch (e: Exception) { Log.w("PsAudio", "decode: ${e.message}") }
    }

    override fun close() { runCatching { track.stop(); track.release() }; runCatching { codec.stop(); codec.release() } }
}
