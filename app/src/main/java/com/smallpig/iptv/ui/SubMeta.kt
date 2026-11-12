package com.smallpig.iptv.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 订阅卡 meta 行（spec 2026-11-11-ui-batch §④）：
 * lastUpdatedAt ≤0 →「未刷新」；与 now 同一天 →「HH:mm 刷新」；跨天 →「MM-dd HH:mm 刷新」。
 * 纯 JVM：同一天判定用两个日期的 yyyy-MM-dd 格式化串比较（时区取默认，与真机一致）。
 */
fun subMetaText(count: Int, lastUpdatedAt: Long, now: Long): String {
    val suffix = if (lastUpdatedAt <= 0L) {
        "未刷新"
    } else {
        val d = Date(lastUpdatedAt)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val time = SimpleDateFormat("HH:mm", Locale.CHINA)
        if (day.format(d) == day.format(Date(now))) "${time.format(d)} 刷新"
        else "${SimpleDateFormat("MM-dd", Locale.CHINA).format(d)} ${time.format(d)} 刷新"
    }
    return "$count 频道 · $suffix"
}
