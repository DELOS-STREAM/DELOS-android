package com.limelight.ps

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap

/**
 * Plays the console's Opus units (7.4) with the platform decoder and an AudioTrack in low-latency mode. One instance per stream;
 * [header] is the 14-byte audio header of STREAMINFO (channels, bits, rate BE, frame size BE).
 *
 * The network thread only queues packets by frame index; a playback thread waits until [START_FRAMES] are buffered, then plays one
 * frame per packet in order, filling a missing frame with the decoder's loss concealment. The buffer keeps at most [MAX_FRAMES].
 */
class PsAudioPlayer(header: ByteArray) : AutoCloseable {
    val channels = u(header[0])
    val rate = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
    private val frameSamples = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.BIG_ENDIAN).int.takeIf { it > 0 } ?: 480
    private val codec: MediaCodec
    private val track: AudioTrack
    private val queue = TreeMap<Int, ByteArray>()
    private var next = -1
    @Volatile private var running = true
    private val thread: Thread

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
        val frameBytes = frameSamples * channels * 2
        val min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, frameBytes * 6))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        thread = Thread(::playLoop, "ps-audio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    /** Queues one Opus packet for [frameIndex]; repeats and frames already played are dropped. */
    fun play(opus: ByteArray, frameIndex: Int) {
        synchronized(queue) {
            if (next >= 0 && ((frameIndex - next) and 0xFFFF) > 0x8000) return
            queue.putIfAbsent(frameIndex, opus)
            while (queue.size > MAX_FRAMES) queue.pollFirstEntry()?.let { if (next >= 0) next = (it.key + 1) and 0xFFFF }
            (queue as Object).notifyAll()
        }
    }

    private fun playLoop() {
        val silence = ByteArray(frameSamples * channels * 2)
        while (running) {
            val packet: ByteArray?
            synchronized(queue) {
                if (next < 0) {
                    while (running && queue.size < START_FRAMES) (queue as Object).wait(50)
                    if (!running) return
                    next = queue.firstKey()
                }
                // Wait one frame for the next packet; a missing one is concealed
                if (!queue.containsKey(next) && queue.isNotEmpty()) (queue as Object).wait(frameSamples * 1000L / rate)
                if (queue.isEmpty()) { next = -1; continue }
                packet = queue.remove(next)
                next = (next + 1) and 0xFFFF
            }
            if (packet == null) { track.write(silence, 0, silence.size); continue }
            decode(packet)
        }
    }

    private fun decode(opus: ByteArray) {
        try {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) { codec.getInputBuffer(i)!!.apply { clear(); put(opus) }; codec.queueInputBuffer(i, 0, opus.size, 0, 0) }
            val info = MediaCodec.BufferInfo()
            var o = codec.dequeueOutputBuffer(info, 10_000)
            while (o >= 0) {
                val out = codec.getOutputBuffer(o)!!
                val pcm = ByteArray(info.size)
                out.position(info.offset); out.get(pcm)
                track.write(pcm, 0, pcm.size)
                codec.releaseOutputBuffer(o, false)
                o = codec.dequeueOutputBuffer(info, 0)
            }
        } catch (e: Exception) { Log.w("PsAudio", "decode: ${e.message}") }
    }

    override fun close() {
        running = false
        synchronized(queue) { (queue as Object).notifyAll() }
        runCatching { thread.join(200) }
        runCatching { track.stop(); track.release() }
        runCatching { codec.stop(); codec.release() }
    }

    companion object {
        const val START_FRAMES = 3
        const val MAX_FRAMES = 8
    }
}
