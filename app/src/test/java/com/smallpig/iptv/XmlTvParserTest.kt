package com.smallpig.iptv

import com.smallpig.iptv.parse.XmlTvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.TimeZone

class XmlTvParserTest {
    private fun xml(body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><tv><channel id="cctv1"><display-name>CCTV-1</display-name></channel>$body</tv>"""

    private fun String.toStream() = ByteArrayInputStream(toByteArray())

    private fun stamp(dayOffset: Int, hour: Int, min: Int, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
        }
        fun p(n: Int, w: Int = 2) = n.toString().padStart(w, '0')
        return "${p(c.get(Calendar.YEAR), 4)}${p(c.get(Calendar.MONTH) + 1)}${p(c.get(Calendar.DAY_OF_MONTH))}" +
            "${p(c.get(Calendar.HOUR_OF_DAY))}${p(c.get(Calendar.MINUTE))}00"
    }

    @Test fun keepsTodayFiltersYesterdayAndFarFuture() {
        val now = System.currentTimeMillis()
        val body =
            "<programme channel=\"cctv1\" start=\"${stamp(-1, 8, 0)}\" stop=\"${stamp(-1, 9, 0)}\"><title>昨天</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>今天</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(2, 3, 0)}\" stop=\"${stamp(2, 4, 0)}\"><title>大后天</title></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size)
        assertEquals("今天", r[0].title)
    }

    @Test fun programmeKeysIncludeIdAndDisplayName() {
        val now = System.currentTimeMillis()
        val body = "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>新闻</title><desc>描述</desc></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size)
        assertTrue(r[0].channelKeys.contains("id:cctv1"))
        assertTrue(r[0].channelKeys.contains("name:CCTV-1"))
        assertEquals("描述", r[0].description)
    }

    @Test fun parsesTimezoneOffset() {
        val ms = XmlTvParser.parseXmlTvTime("20261108120000+0800")!!
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply { timeInMillis = ms }
        assertEquals(2026, c.get(Calendar.YEAR)); assertEquals(10, c.get(Calendar.MONTH))
        assertEquals(8, c.get(Calendar.DAY_OF_MONTH)); assertEquals(12, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun skipsBrokenTimeAndBlankTitle() {
        val now = System.currentTimeMillis()
        val body =
            "<programme channel=\"cctv1\" start=\"garbage\" stop=\"x\"><title>坏时间</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>  </title></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(0, r.size)
    }

    @Test fun keepsProgrammeOverlappingWindowStart() {
        val now = System.currentTimeMillis()
        val body = "<programme channel=\"cctv1\" start=\"${stamp(-1, 23, 0)}\" stop=\"${stamp(0, 1, 0)}\"><title>跨天</title></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size); assertEquals("跨天", r[0].title)
    }

    @Test fun programmeBeforeChannelDefGetsOnlyIdKey() {
        val now = System.currentTimeMillis()
        val body = "<programme channel=\"cctv2\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>先序节目</title></programme>" +
            "<channel id=\"cctv2\"><display-name>CCTV-2</display-name></channel>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size)
        assertEquals(listOf("id:cctv2"), r[0].channelKeys)
    }

    @Test fun fractionalSecondsAndOffsetParse() {
        val ms = XmlTvParser.parseXmlTvTime("20261108120000.500+0800")!!
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply { timeInMillis = ms }
        assertEquals(12, c.get(Calendar.HOUR_OF_DAY)); assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(500, c.get(Calendar.MILLISECOND))
    }
}
