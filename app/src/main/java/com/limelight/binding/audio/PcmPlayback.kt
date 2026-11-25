package com.limelight.binding.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/** Original float PCM, streamed without changing sample rate, channels, length or the intro's audio clock. */
class PcmPlayback(
    private val pcm: FloatArray,
    private val format: AudioFormat,
    attributes: AudioAttributes,
    private val volume: VolumeController,
    private val executor: ExecutorService,
) {
    private val channels = format.channelCount
    private val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(format.sampleRate, format.channelMask, AudioFormat.ENCODING_PCM_FLOAT), 2048 * channels * 4))
        .build()
    private val serial = AtomicLong()
    private val finishedSerial = AtomicLong()
    private val timestamp = AudioTimestamp()
    @Volatile private var generationStartedNs = Long.MAX_VALUE
    private val ioLock = Any()
    private val block = FloatArray(512 * channels)
    @Volatile private var released = false
    private var lastGain = Float.NaN

    fun route(device: AudioDeviceInfo?) { track.preferredDevice = device }

    fun play() {
        val id = serial.incrementAndGet()
        generationStartedNs = System.nanoTime()
        track.pause()
        track.flush()
        executor.execute {
            synchronized(ioLock) {
                if (released || serial.get() != id) return@execute
                try {
                    var offset = 0
                    track.play()
                    while (!released && serial.get() == id && offset < pcm.size) {
                        val gain = volume.gain
                        if (gain != lastGain) {
                            track.setVolume(min(1f, gain))
                            lastGain = gain
                        }
                        val count = min(block.size, pcm.size - offset)
                        val samples: FloatArray
                        val sourceOffset: Int
                        if (gain > 1f) {
                            for (i in 0 until count) block[i] = (pcm[offset + i] * gain).coerceIn(-1f, 1f)
                            samples = block
                            sourceOffset = 0
                        } else {
                            samples = pcm
                            sourceOffset = offset
                        }
                        var written = 0
                        while (!released && serial.get() == id && written < count) {
                            val n = track.write(samples, sourceOffset + written, count - written, AudioTrack.WRITE_BLOCKING)
                            if (n <= 0) {
                                if (released || serial.get() != id) break
                                error("PCM output write failed: $n")
                            }
                            written += n
                        }
                        offset += written
                    }
                    // Completion follows the audible head, not the submission of the last block.
                    while (!released && serial.get() == id && track.playbackHeadPosition.toLong() < pcm.size / channels) {
                        Thread.sleep(10)
                    }
                } finally {
                    if (!released && serial.get() == id) track.stop()
                    finishedSerial.accumulateAndGet(id) { a, b -> maxOf(a, b) }
                }
            }
        }
    }

    /** Same contract as the intro's former MediaPlayer timestamp: null after completion, zero before sound reaches the output. */
    fun positionMs(): Float? {
        if (released || serial.get() <= finishedSerial.get()) return null
        if (!track.getTimestamp(timestamp) || timestamp.nanoTime < generationStartedNs) return 0f
        val frames = timestamp.framePosition + (System.nanoTime() - timestamp.nanoTime) * format.sampleRate / 1_000_000_000.0
        return (frames.coerceIn(0.0, (pcm.size / channels).toDouble()) * 1000.0 / format.sampleRate).toFloat()
    }

    fun stop() {
        finishedSerial.set(serial.incrementAndGet())
        if (!released) { track.pause(); track.flush() }
    }

    fun release() {
        stop()
        released = true
        // Queue release after a writer leaves its blocking call; no track is released under a write.
        executor.execute { synchronized(ioLock) { track.release() } }
    }
}
