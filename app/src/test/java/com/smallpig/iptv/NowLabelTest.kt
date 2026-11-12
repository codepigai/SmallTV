package com.smallpig.iptv

import com.smallpig.iptv.ui.nowLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「节目名　·　网络~xxms」拼接与绿段起点（spec 2026-11-11-ui-batch §①） */
class NowLabelTest {

    @Test
    fun bothSegments_joinedWithSeparator_greenStartsAtRtt() {
        val l = nowLabel("新闻", 42L)
        assertEquals("新闻　·　网络~42ms", l.text)
        assertEquals(5, l.greenFrom) // 新闻+全角空格+·+全角空格 = 5 字符
    }

    @Test
    fun onlyRtt_greenFromZero() {
        val l = nowLabel(null, 42L)
        assertEquals("网络~42ms", l.text)
        assertEquals(0, l.greenFrom)
    }

    @Test
    fun onlyEpgTitle_noGreen() {
        val l = nowLabel("新闻", null)
        assertEquals("新闻", l.text)
        assertEquals(-1, l.greenFrom)
    }

    @Test
    fun neither_emptyTextNoGreen() {
        val l = nowLabel(null, null)
        assertEquals("", l.text)
        assertEquals(-1, l.greenFrom)
    }

    @Test
    fun negativeRtt_treatedAsAbsent() {
        val a = nowLabel(null, -1L)
        assertEquals("", a.text)
        assertEquals(-1, a.greenFrom)
        val b = nowLabel("新闻", -1L)
        assertEquals("新闻", b.text)
        assertEquals(-1, b.greenFrom)
    }
}
