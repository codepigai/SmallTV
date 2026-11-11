package com.smallpig.iptv.ui

/**
 * 首页频道行「正在播节目名　·　网络~xxms」拼接（spec 2026-11-11-ui-batch §①）。
 * 纯函数零 Android 依赖；绿段=[greenFrom] 到结尾（-1=无绿段）。
 */
data class NowLabel(val text: String, val greenFrom: Int)

/** rtt 段文案「网络~xxms」；rttMs null/负数视为无；双段以全角空格+间隔号+全角空格（3 字符）连接 */
fun nowLabel(epgTitle: String?, rttMs: Long?): NowLabel {
    val rtt = rttMs?.takeIf { it >= 0 }?.let { "网络~${it}ms" }
    return when {
        epgTitle != null && rtt != null -> NowLabel("$epgTitle　·　$rtt", epgTitle.length + 3)
        rtt != null -> NowLabel(rtt, 0)
        epgTitle != null -> NowLabel(epgTitle, -1)
        else -> NowLabel("", -1)
    }
}
