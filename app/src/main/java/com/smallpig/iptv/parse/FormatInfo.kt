package com.smallpig.iptv.parse

/**
 * 纯函数显示逻辑（真机反馈 #4）：编码名/码率/网络延迟的可测展示格式化。
 * 输入均为原始值（String/Long），不依赖 media3 类型，便于 JVM 单测。
 */

/** Media3 Format.codecs（RFC 6381，如 "avc1.640028"）优先，其次 sampleMimeType（如 "video/avc"）→ 显示名。 */
fun codecDisplayName(codecs: String?, sampleMimeType: String?): String {
    val key = (codecs ?: "").trim().substringBefore('.').lowercase().takeIf { it.isNotEmpty() }
        ?: (sampleMimeType ?: "").trim().substringAfter('/').lowercase().takeIf { it.isNotEmpty() }
    return when (key) {
        null -> "未知"
        "avc1", "avc3", "avc" -> "H.264"
        "hvc1", "hev1", "hevc", "h265" -> "H.265"
        "av01" -> "AV1"
        "vp08", "vp8" -> "VP8"
        "vp09", "vp9" -> "VP9"
        "mp4a" -> "AAC"
        "ac-3", "ac3" -> "AC-3"
        "ec-3", "eac3" -> "E-AC-3"
        "opus" -> "Opus"
        "mpeg", "mpeglayer3", "mp3" -> "MP3"
        "mp2t" -> "TS"
        "mp4v-es" -> "MPEG-4"
        else -> key.uppercase()
    }
}

/** 优先真实码率，其次带宽估计值（带「估」标注）；均无则「码率未知」。 */
fun bitrateLabel(bitrateBps: Long, estimatedBps: Long): String {
    val (bps, estimated) = if (bitrateBps > 0) bitrateBps to false else estimatedBps to true
    if (bps <= 0) return "码率未知"
    val suffix = if (estimated) "（估）" else ""
    return if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1e6) + suffix
    else "%.0f kbps".format(bps / 1e3) + suffix
}

/** RttProbe 结果（ms，-1=失败）→ 显示文本。 */
fun rttLabel(ms: Long): String = when {
    ms < 0 -> "未知"
    ms > 999 -> ">999 ms"
    else -> "~${ms} ms"
}
