package com.limelight.shell.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsStillTest {
    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",URI="hls_264_4_audio.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=5800000,RESOLUTION=1920x1080,AUDIO="audio"
        hls_264_0_video.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720,AUDIO="audio"
        hls_264_1_video.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480,AUDIO="audio"
        hls_264_2_video.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=640x360,AUDIO="audio"
        hls_264_3_video.m3u8
    """.trimIndent()

    private val media = """
        #EXTM3U
        #EXT-X-TARGETDURATION:3
        #EXT-X-MAP:URI="dash_h264/init-stream1.m4s"
        #EXTINF:3
        dash_h264/chunk-1.m4s
        #EXTINF:3
        dash_h264/chunk-2.m4s
        #EXTINF:3,
        dash_h264/chunk-3.m4s
        #EXTINF:3
        dash_h264/chunk-4.m4s
        #EXTINF:3
        dash_h264/chunk-5.m4s
        #EXTINF:3
        dash_h264/chunk-6.m4s
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test fun `the 854 px rendition is read, not the audio or the largest`() {
        assertEquals("hls_264_2_video.m3u8", HlsStill.variant(master))
        assertNull(HlsStill.variant("#EXTM3U\n"))
    }

    @Test fun `the still comes from the segment playing at a sixth, then a third`() {
        val playlist = HlsStill.playlist(media)!!
        assertEquals("dash_h264/init-stream1.m4s", playlist.init)
        assertEquals(6, playlist.segments.size)
        // 18 s: a sixth is 3 s (the second segment starts there), a third 6 s (the third)
        assertEquals("dash_h264/chunk-2.m4s", playlist.segmentAt(1.0 / 6))
        assertEquals("dash_h264/chunk-3.m4s", playlist.segmentAt(1.0 / 3))
    }

    @Test fun `a playlist without an init section cannot give a still`() {
        assertNull(HlsStill.playlist("#EXTM3U\n#EXTINF:3\nchunk.ts\n"))
    }
}
