package com.smallpig.iptv

import com.smallpig.iptv.data.Channel
import com.smallpig.iptv.data.Favorite
import com.smallpig.iptv.data.Subscription
import com.smallpig.iptv.parse.FavoriteKey
import com.smallpig.iptv.ui.AudioRouter
import com.smallpig.iptv.ui.GROUP_FAV
import com.smallpig.iptv.ui.pickSubscription
import com.smallpig.iptv.ui.PickRow
import com.smallpig.iptv.ui.StartGate
import com.smallpig.iptv.ui.backoffMs
import com.smallpig.iptv.ui.channelsToRows
import com.smallpig.iptv.ui.drawerGroups
import com.smallpig.iptv.ui.favoritesToRows
import com.smallpig.iptv.ui.filterRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 多屏纯逻辑（错峰门控/音量路由/重连退避/抽屉数据）——spec 2026-11-11 §6/§7/§3 */
class MultiScreenLogicTest {

    // ---- StartGate：prepare() 串行限流，槽位即时预留 ----

    @Test
    fun startGate_serializesByMinInterval() {
        val delays = mutableListOf<Long>()
        val queued = mutableListOf<Runnable>()
        var ran = 0
        val gate = StartGate(800L, now = { 0L }, post = { r, d -> delays += d; queued += r })
        gate.request { ran++ }; gate.request { ran++ }; gate.request { ran++ }
        // 时钟恒 0：仍须按 800ms 间隔串行排队
        assertEquals(listOf(0L, 800L, 1600L), delays)
        queued.forEach { it.run() }
        assertEquals(3, ran)
    }

    @Test
    fun startGate_respectsClockAdvance() {
        var t = 0L
        val delays = mutableListOf<Long>()
        val gate = StartGate(800L, now = { t }, post = { _, d -> delays += d })
        gate.request {}
        t = 500L
        gate.request {}
        assertEquals(listOf(0L, 300L), delays)
    }

    // ---- AudioRouter：恰一路持有 1f，其余 0f ----

    private fun routerWith(vararg idx: Int): Pair<AudioRouter, MutableMap<Int, MutableList<Float>>> {
        val r = AudioRouter()
        val log = mutableMapOf<Int, MutableList<Float>>()
        for (i in idx) { log[i] = mutableListOf(); r.attach(i) { log[i]!!.add(it) } }
        return r to log
    }

    @Test
    fun audioRouter_onlyOwnerHasVolume() {
        val (r, log) = routerWith(0, 1, 2)
        r.setOwner(1)
        assertEquals(0f, log[0]!!.last())
        assertEquals(1f, log[1]!!.last())
        assertEquals(0f, log[2]!!.last())
        assertEquals(1, r.owner)
    }

    @Test
    fun audioRouter_attachAfterOwnerIsMuted() {
        val r = AudioRouter()
        val v0 = mutableListOf<Float>(); r.attach(0) { v0.add(it) }
        r.setOwner(0)
        val v2 = mutableListOf<Float>(); r.attach(2) { v2.add(it) }
        assertEquals(1f, v0.last())
        assertEquals(0f, v2.last()) // 后挂上来的格不得抢声音
    }

    @Test
    fun audioRouter_detachOwnerGoesSilent() {
        val (r, log) = routerWith(0, 1)
        r.setOwner(0)
        r.detach(0)
        assertEquals(AudioRouter.NONE, r.owner)
        assertEquals(0f, log[1]!!.last()) // 无 owner 时全员静音
    }

    @Test
    fun audioRouter_setNoneMutesAll() {
        val (r, log) = routerWith(0, 1)
        r.setOwner(0)
        r.setOwner(AudioRouter.NONE)
        assertEquals(0f, log[0]!!.last())
        assertEquals(0f, log[1]!!.last())
        assertEquals(AudioRouter.NONE, r.owner)
    }

    // ---- 重连退避：2s 起倍增，15s 封顶（同单屏第 3 层） ----

    @Test
    fun backoffMs_growthAndCap() {
        assertEquals(2000L, backoffMs(0L))
        assertEquals(4000L, backoffMs(1L))
        assertEquals(8000L, backoffMs(2L))
        assertEquals(15000L, backoffMs(3L))
        assertEquals(15000L, backoffMs(9L))
    }

    // ---- 抽屉数据：分组表 / 行模型 / 组过滤 ----

    @Test
    fun drawerGroups_shape() {
        assertEquals(listOf<String?>(null, GROUP_FAV, "CCTV", "体育"), drawerGroups(listOf("CCTV", "体育"), 3))
        assertFalse(drawerGroups(listOf("CCTV"), 0).contains(GROUP_FAV))
    }

    private fun ch(name: String, url: String, group: String, tvg: String?) =
        Channel(id = 0, subscriptionId = 1, name = name, logoUrl = null, groupTitle = group, streamUrl = url, tvgId = tvg, sortIndex = 0)

    @Test
    fun channelsToRows_marksFav() {
        val favKeys = setOf(FavoriteKey.of("CCTV5", "http://x/5"))
        val rows = channelsToRows(listOf(ch("CCTV5", "http://x/5", "体育", "c5"), ch("CCTV1", "http://x/1", "综合", null)), favKeys)
        assertEquals(true, rows[0].fav)
        assertEquals(false, rows[1].fav)
        assertEquals("c5", rows[0].tvgId)   // tvgId 透传
        assertEquals("体育", rows[0].groupTitle)
    }

    @Test
    fun favoritesToRows_nullTvg() {
        val rows = favoritesToRows(listOf(Favorite(id = 0, name = "HBO", logoUrl = null, streamUrl = "http://h", dedupeKey = "k", addedAt = 0L)))
        assertEquals("HBO", rows[0].name)
        assertEquals("http://h", rows[0].url)
        assertEquals(null, rows[0].tvgId)   // 全局收藏无 tvgId（抽屉无 EPG，无影响）
        assertEquals(true, rows[0].fav)
    }

    @Test
    fun filterRows_byGroup() {
        val rows = channelsToRows(listOf(ch("A", "http://a", "体育", null), ch("B", "http://b", "新闻", null)), emptySet())
        val favRows = listOf(PickRow("F", "http://f", null, true, GROUP_FAV))
        assertEquals(2, filterRows(rows, null, favRows).size)          // 全部
        assertEquals(listOf("A"), filterRows(rows, "体育", favRows).map { it.name })
        assertEquals(listOf("F"), filterRows(rows, GROUP_FAV, favRows).map { it.name })
    }

    // ---- pickSubscription：抽屉订阅切换的选取顺序（方案 A：指定→当前→第一个） ----

    @Test
    fun pickSubscription_selectedThenCurrentThenFirst() {
        val a = Subscription(1, "a", "http://a.m3u")
        val b = Subscription(2, "b", "http://b.m3u", isCurrent = true)
        val c = Subscription(3, "c", "http://c.m3u")
        val subs = listOf(a, b, c)
        assertEquals(3L, pickSubscription(subs, 3L)?.id)      // 指定订阅优先（跨订阅同屏入口）
        assertEquals(2L, pickSubscription(subs, null)?.id)    // 未指定 → 当前订阅
        assertEquals(2L, pickSubscription(subs, 99L)?.id)     // 指定已删 → 回退当前
        assertEquals(1L, pickSubscription(listOf(a, c), null)?.id)   // 无当前 → 第一个
        assertNull(pickSubscription(emptyList(), null))       // 空 → null（无订阅空态）
    }
}
