package com.smallpig.iptv.ui

import com.smallpig.iptv.data.Channel
import com.smallpig.iptv.data.Favorite
import com.smallpig.iptv.parse.FavoriteKey

/**
 * 多屏观看的纯逻辑层（零 Android 依赖，JVM 可测）——设计文档 2026-11-11 §6/§7。
 * MultiScreenActivity / ChannelDrawer 只消费这里的类型；行为语义以 MultiScreenLogicTest 为准。
 */

/** 抽屉「收藏」伪组 id；「全部」用 null（与 HomeActivity.currentGroup 语义一致） */
const val GROUP_FAV = "__fav__"

/** 抽屉频道行（Channel 与 Favorite 的统一展示模型） */
data class PickRow(
    val name: String,
    val url: String,
    val tvgId: String?,
    val fav: Boolean,
    val groupTitle: String? = null,
)

fun channelsToRows(channels: List<Channel>, favKeys: Set<String>): List<PickRow> =
    channels.map { PickRow(it.name, it.streamUrl, it.tvgId, FavoriteKey.of(it.name, it.streamUrl) in favKeys, it.groupTitle) }

/** 全局收藏 → 抽屉行：tvgId 恒 null（收藏表无此字段，抽屉无 EPG 故无影响） */
fun favoritesToRows(favs: List<Favorite>): List<PickRow> =
    favs.map { PickRow(it.name, it.streamUrl, null, true, GROUP_FAV) }

/** 抽屉分组列：[全部(null)] +（有收藏时 [收藏]）+ 真实分组 */
fun drawerGroups(realGroups: List<String>, favCount: Int): List<String?> =
    listOf<String?>(null) + (if (favCount > 0) listOf<String?>(GROUP_FAV) else emptyList()) + realGroups

/** 抽屉频道列：null=全部；GROUP_FAV=全局收藏行；其余按组名过滤 */
fun filterRows(rows: List<PickRow>, group: String?, favRows: List<PickRow>): List<PickRow> = when (group) {
    null -> rows
    GROUP_FAV -> favRows
    else -> rows.filter { it.groupTitle == group }
}

/** 致命错误重连退避：2s 起倍增、15s 封顶（复刻单屏 PlaybackActivity 第 3 层） */
fun backoffMs(retries: Long): Long = minOf(2000L * (1L shl retries.coerceAtMost(3).toInt()), 15_000L)

/**
 * 错峰起播门控：所有 prepare() 经 request() 排队，彼此至少间隔 minIntervalMs，
 * 防多路并发新会话把中继源打进限流（spec §6）。槽位在提交瞬间即预留，即使时钟不推进也串行。
 */
class StartGate(
    private val minIntervalMs: Long,
    private val now: () -> Long,
    private val post: (Runnable, Long) -> Unit,
) {
    private var lastStart = Long.MIN_VALUE / 2

    fun request(action: () -> Unit) {
        val due = maxOf(0L, lastStart + minIntervalMs - now())
        lastStart = now() + due
        post(Runnable(action), due)
    }
}

/**
 * 单路声音路由（spec §7）：恰有一个 owner 持 1f，其余 0f；不申请系统 AudioFocus（有意简化，§13③）。
 * owner=NONE（无 owner / owner 格被摘除）时全员静音。
 */
class AudioRouter {
    companion object { const val NONE = -1 }

    private val volumes = LinkedHashMap<Int, (Float) -> Unit>()
    var owner = NONE
        private set

    fun attach(index: Int, setVolume: (Float) -> Unit) {
        volumes[index] = setVolume
        setVolume(if (index == owner) 1f else 0f)
    }

    fun detach(index: Int) {
        volumes.remove(index) ?: return
        if (index == owner) {
            owner = NONE
            apply()
        }
    }

    /** 未 attach 的 index 与 NONE 同义：全员静音 */
    fun setOwner(index: Int) {
        owner = if (index != NONE && volumes.containsKey(index)) index else NONE
        apply()
    }

    private fun apply() {
        for ((i, set) in volumes) set(if (i == owner) 1f else 0f)
    }
}
