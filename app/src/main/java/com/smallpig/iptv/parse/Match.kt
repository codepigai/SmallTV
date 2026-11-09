package com.smallpig.iptv.parse

object EpgMatcher {
    fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), "").uppercase()
    fun nameKey(displayName: String): String = "name:${normalize(displayName)}"

    fun keyForChannel(tvgId: String?, name: String): List<String> =
        listOfNotNull(tvgId?.trim()?.takeIf { it.isNotEmpty() }?.let { "id:$it" }, nameKey(name))

    /** items 中找 now 时刻正在播出项与紧随其后的下一项；items 可乱序 */
    fun <T> nowNext(items: List<T>, startOf: (T) -> Long, stopOf: (T) -> Long, now: Long): Pair<T?, T?> {
        val sorted = items.sortedBy(startOf)
        val cur = sorted.lastOrNull { startOf(it) <= now && stopOf(it) > now }
        val next = sorted.firstOrNull { startOf(it) > now }
        return cur to next
    }
}

object FavoriteKey {
    fun of(name: String, streamUrl: String): String =
        "${EpgMatcher.normalize(name)}|${streamUrl.trim()}"
}
