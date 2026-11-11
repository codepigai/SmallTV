package com.smallpig.iptv

import com.smallpig.iptv.ui.MODE_GRID
import com.smallpig.iptv.ui.MODE_LIST
import com.smallpig.iptv.ui.nextMode
import com.smallpig.iptv.ui.nextSpan
import com.smallpig.iptv.ui.normMode
import com.smallpig.iptv.ui.normSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/** 设置页存取纯逻辑（spec 2026-11-11-ui-batch §⑤）——归一化与循环切换 */
class UiSettingsTest {

    @Test
    fun normMode_onlyGridPassesOthersFallTo() {
        assertEquals(MODE_GRID, normMode("grid"))
        assertEquals(MODE_LIST, normMode("list"))
        assertEquals(MODE_LIST, normMode(null))
        assertEquals(MODE_LIST, normMode(""))
        assertEquals(MODE_LIST, normMode("Grid"))
        assertEquals(MODE_LIST, normMode("gibberish"))
    }

    @Test
    fun nextMode_togglesBetweenTwoValues() {
        assertEquals(MODE_GRID, nextMode(MODE_LIST))
        assertEquals(MODE_LIST, nextMode(MODE_GRID))
        assertEquals(MODE_GRID, nextMode("脏值按list切")) // 脏值视为 list → grid
    }

    @Test
    fun normSpan_outOfRangeFallsTo() {
        assertEquals(3, normSpan(3))
        assertEquals(2, normSpan(2))
        assertEquals(5, normSpan(5))
        assertEquals(3, normSpan(1))
        assertEquals(3, normSpan(6))
        assertEquals(3, normSpan(0))
        assertEquals(3, normSpan(-4))
    }

    @Test
    fun nextSpan_cyclesTwoThreeFourFive() {
        assertEquals(3, nextSpan(2))
        assertEquals(4, nextSpan(3))
        assertEquals(5, nextSpan(4))
        assertEquals(2, nextSpan(5))
        assertEquals(3, nextSpan(99)) // 脏值不进循环，直接回退默认 3
    }
}
