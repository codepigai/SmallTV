package com.smallpig.iptv

import com.smallpig.iptv.ui.UpDownIntent
import com.smallpig.iptv.ui.upDownIntent
import org.junit.Assert.assertEquals
import org.junit.Test

/** 播放页 UP/DOWN 键意图（真机复报：面板内吞咽=死键，须放行系统焦点导航） */
class PlaybackKeysTest {
    @Test
    fun `焦点在面板内时 UP 放行导航不吞咽不换台`() {
        assertEquals(UpDownIntent.PASS_TO_PANEL, upDownIntent(true, true))
    }

    @Test
    fun `焦点在面板内时 DOWN 同样放行`() {
        assertEquals(UpDownIntent.PASS_TO_PANEL, upDownIntent(true, false))
    }

    @Test
    fun `焦点不在面板时上下键换台`() {
        assertEquals(UpDownIntent.STEP_UP, upDownIntent(false, true))
        assertEquals(UpDownIntent.STEP_DOWN, upDownIntent(false, false))
    }
}
