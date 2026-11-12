package com.smallpig.iptv

import com.smallpig.iptv.data.Subscription
import com.smallpig.iptv.ui.reselectSubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 终审 Important#3：reload 后按 id 重解析选中订阅（右栏详情不得停留在旧实体） */
class SubsReselectTest {

    private fun sub(id: Long, name: String, current: Boolean = false) =
        Subscription(id = id, name = name, m3uUrl = "http://a/$id", isCurrent = current)

    @Test
    fun sameId_resolvesToFreshEntity() {
        val fresh = sub(2, "改名后", current = true)
        val picked = reselectSubs(listOf(sub(1, "旧"), fresh), prev = sub(2, "旧名"))
        assertEquals("改名后", picked?.name) // 拿到的是新对象而非传入的旧 selected
        assertEquals(2L, picked!!.id)
    }

    @Test
    fun prevGone_fallsBackToCurrent() {
        val all = listOf(sub(1, "甲"), sub(2, "乙", current = true))
        assertEquals(2L, reselectSubs(all, prev = sub(9, "已删除"))!!.id)
    }

    @Test
    fun prevNull_fallsBackToCurrentThenFirst() {
        val withCurrent = listOf(sub(1, "甲"), sub(2, "乙", current = true))
        assertEquals(2L, reselectSubs(withCurrent, prev = null)!!.id)
        val noCurrent = listOf(sub(1, "甲"), sub(2, "乙"))
        assertEquals(1L, reselectSubs(noCurrent, prev = null)!!.id)
    }

    @Test
    fun emptyList_returnsNull() {
        assertNull(reselectSubs(emptyList(), prev = sub(1, "甲")))
    }
}
