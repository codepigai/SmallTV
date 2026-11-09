package com.smallpig.iptv

import com.smallpig.iptv.parse.bitrateLabel
import com.smallpig.iptv.parse.codecDisplayName
import com.smallpig.iptv.parse.rttLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatInfoTest {

    @Test
    fun codecFromRfc6381Codecs() {
        assertEquals("H.264", codecDisplayName("avc1.640028", null))
        assertEquals("H.264", codecDisplayName("avc3.4d001f", null))
        assertEquals("H.265", codecDisplayName("hvc1.1.6.L93.B0", null))
        assertEquals("H.265", codecDisplayName("hev1.1.2.L93.B0", null))
        assertEquals("AV1", codecDisplayName("av01.0.08M.08", null))
        assertEquals("VP9", codecDisplayName("vp09.00.10.08", null))
        assertEquals("AAC", codecDisplayName("mp4a.40.2", null))
        assertEquals("E-AC-3", codecDisplayName("ec-3", null))
        assertEquals("Opus", codecDisplayName("opus", null))
    }

    @Test
    fun codecFallsBackToMime() {
        assertEquals("H.264", codecDisplayName(null, "video/avc"))
        assertEquals("MP3", codecDisplayName(null, "audio/mpeg"))
        assertEquals("TS", codecDisplayName(null, "video/mp2t"))
        assertEquals("MPEG-4", codecDisplayName(null, "video/mp4v-es"))
        // codecs 优先于 mime
        assertEquals("H.265", codecDisplayName("hvc1.1.6", "video/hevc"))
    }

    @Test
    fun codecUnknownAndRawFallback() {
        assertEquals("未知", codecDisplayName(null, null))
        assertEquals("未知", codecDisplayName("", "  "))
        assertEquals("XAVC", codecDisplayName("xavc.123", null))
    }

    @Test
    fun bitratePrefersRealValue() {
        assertEquals("4.5 Mbps", bitrateLabel(4500000, 99999999))
        assertEquals("800 kbps", bitrateLabel(800000, 0))
    }

    @Test
    fun bitrateEstimatedFallbackAndUnknown() {
        assertEquals("3.2 Mbps（估）", bitrateLabel(-1, 3200000))
        assertEquals("128 kbps（估）", bitrateLabel(-1, 128000))
        assertEquals("码率未知", bitrateLabel(-1, -1))
        assertEquals("码率未知", bitrateLabel(0, 0))
    }

    @Test
    fun rttLabels() {
        assertEquals("~38 ms", rttLabel(38))
        assertEquals("~999 ms", rttLabel(999))
        assertEquals(">999 ms", rttLabel(1500))
        assertEquals("未知", rttLabel(-1))
    }
}
