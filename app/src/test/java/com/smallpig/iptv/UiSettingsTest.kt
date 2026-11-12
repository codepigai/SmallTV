package com.smallpig.iptv

import com.smallpig.iptv.ui.MODE_GRID
import com.smallpig.iptv.ui.MODE_LIST
import com.smallpig.iptv.ui.channelItemViewType
import com.smallpig.iptv.ui.needsLayoutRebuild
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

    // ---- 终审 Important#2：仅模式/路数真变化才重建 LayoutManager（防 onResume 无条件重建致滚动回顶） ----

    @Test
    fun needsLayoutRebuild_firstApplyAlwaysRebuilds() {
        assertEquals(true, needsLayoutRebuild(null, false, 3))
    }

    @Test
    fun needsLayoutRebuild_sameSpecNoRebuild() {
        assertEquals(false, needsLayoutRebuild(Pair(false, 3), false, 3))
        assertEquals(false, needsLayoutRebuild(Pair(true, 4), true, 4))
    }

    @Test
    fun needsLayoutRebuild_modeToggleAlwaysRebuilds() {
        assertEquals(true, needsLayoutRebuild(Pair(false, 3), true, 3))
        assertEquals(true, needsLayoutRebuild(Pair(true, 3), false, 3))
        assertEquals(true, needsLayoutRebuild(Pair(true, 3), false, 5)) // 列表态忽略旧 span，但模式变了必须重建
    }

    @Test
    fun needsLayoutRebuild_spanOnlyMattersInGridMode() {
        assertEquals(true, needsLayoutRebuild(Pair(true, 3), true, 4))   // 宫格换路数→重建
        assertEquals(false, needsLayoutRebuild(Pair(false, 3), false, 4)) // 列表态调路数→不重建
    }

    // ---- 真机反馈①：画廊切回列表混用卡片——RecycledViewPool 按 viewType 回收，两模式若同 viewType，
    // 另一模式的 holder 会被原样复用（item 视图不换布局）。两模式必须分属不同 viewType ----
    @Test
    fun channelItemViewType_gridAndListDistinct() {
        assertEquals(0, channelItemViewType(false)) // 列表=默认 0：同 RecyclerView 的其他 adapter（收藏列表恒列表）不受牵连
        assertEquals(1, channelItemViewType(true))  // 画廊=1：池隔离，永不把宫格卡给列表
    }
}
