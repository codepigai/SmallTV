package com.smallpig.iptv

import com.smallpig.iptv.ui.subMetaText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

/** 订阅卡 meta「N 频道 · 刷新时间」（spec 2026-11-11-ui-batch §④）：未刷新/当天/跨天三种形态 */
class SubMetaTest {

    private fun at(y: Int, moZeroBased: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply {
            set(y, moZeroBased, d, h, mi, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun neverRefreshed_showsPlaceholder() {
        assertEquals("128 频道 · 未刷新", subMetaText(128, 0, at(2026, 10, 12, 12, 0)))
    }

    @Test
    fun negativeTimestamp_treatedAsNever() {
        assertEquals("5 频道 · 未刷新", subMetaText(5, -1, at(2026, 10, 12, 12, 0)))
    }

    @Test
    fun sameDay_showsTimeOnlyWithoutDate() {
        val s = subMetaText(50, at(2026, 10, 12, 9, 12), at(2026, 10, 12, 12, 0))
        assertEquals("50 频道 · 09:12 刷新", s)
        assertFalse("同日不得带 MM-dd 日期前缀", s.contains("-"))
    }

    @Test
    fun crossDay_showsMonthDayPrefix() {
        assertEquals("50 频道 · 11-11 09:12 刷新", subMetaText(50, at(2026, 10, 11, 9, 12), at(2026, 10, 12, 12, 0)))
    }

    @Test
    fun zeroChannelCount_stillJoins() {
        assertEquals("0 频道 · 08:05 刷新", subMetaText(0, at(2026, 10, 12, 8, 5), at(2026, 10, 12, 12, 0)))
    }
}
