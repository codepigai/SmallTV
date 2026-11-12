package com.smallpig.iptv

import com.smallpig.iptv.ui.subDetailRows
import com.smallpig.iptv.ui.subMetaText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Task 4 纯函数：订阅详情四行（[名称, 地址, meta, EPG]），视图层只逐行 setText */
class SubDetailRowsTest {
    // 固定 now=2026-11-12 12:00（本机时区），lastUpdate 相对 now 构造防跨日抖动
    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 12, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val lastUpdate = now - 3600_000L // 当天 11:00 刷新

    @Test
    fun `普通订阅返回4行且第3行等于同参subMetaText`() {
        val rows = subDetailRows("央视", "http://a.m3u", 42, lastUpdate, "http://epg", now)
        assertEquals(4, rows.size)
        assertTrue(rows[0], rows[0].startsWith("名称："))
        assertTrue(rows[1], rows[1].startsWith("地址："))
        assertEquals(subMetaText(42, lastUpdate, now), rows[2])
        assertTrue(rows[3], rows[3].startsWith("EPG："))
        assertEquals("名称：央视", rows[0])
        assertEquals("地址：http://a.m3u", rows[1])
    }

    @Test
    fun `epg未配置时末行为EPG未配置`() {
        val rows = subDetailRows("x", "http://a.m3u", 1, lastUpdate, null, now)
        assertEquals("EPG：未配置", rows[3])
    }

    @Test
    fun `epg非空时末行为EPG已配置`() {
        val rows = subDetailRows("x", "http://a.m3u", 1, lastUpdate, "http://epg", now)
        assertEquals("EPG：已配置", rows[3])
    }

    @Test
    fun `长URL原样出现在第2行`() {
        val longUrl = "http://example.com/" + "p".repeat(200)
        val rows = subDetailRows("x", longUrl, 0, 0L, null, now)
        assertEquals("地址：$longUrl", rows[1])
    }
}
