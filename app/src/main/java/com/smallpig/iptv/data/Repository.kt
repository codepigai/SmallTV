package com.smallpig.iptv.data

import com.smallpig.iptv.net.Fetcher
import com.smallpig.iptv.parse.EpgMatcher
import com.smallpig.iptv.parse.FavoriteKey
import com.smallpig.iptv.parse.M3uParser
import com.smallpig.iptv.parse.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.Calendar

class Repository(private val db: AppDatabase, private val fetcher: Fetcher) {
    private val subs get() = db.subscriptionDao()
    private val chans get() = db.channelDao()
    private val favs get() = db.favoriteDao()
    private val epgs get() = db.epgDao()
    private val state get() = db.appStateDao()

    // ---- 订阅 ----
    suspend fun subscriptions(): List<Subscription> = withContext(Dispatchers.IO) { subs.all() }
    suspend fun subscriptionById(id: Long): Subscription? = withContext(Dispatchers.IO) { subs.byId(id) }
    suspend fun currentSubscription(): Subscription? = withContext(Dispatchers.IO) { subs.current() }

    suspend fun addSubscription(name: String, m3uUrl: String, epgUrl: String?): Long = withContext(Dispatchers.IO) {
        val id = subs.insert(Subscription(name = name.trim(), m3uUrl = m3uUrl.trim(), epgUrl = epgUrl?.trim()?.ifBlank { null }))
        if (subs.current() == null) subs.setCurrentAtomically(id)
        id
    }

    suspend fun updateSubscription(sub: Subscription) = withContext(Dispatchers.IO) { subs.update(sub) }

    suspend fun setSubscriptionCurrent(id: Long) = withContext(Dispatchers.IO) {
        subs.setCurrentAtomically(id)
    }

    suspend fun deleteSubscription(sub: Subscription) = withContext(Dispatchers.IO) {
        chans.clearFor(sub.id)
        subs.delete(sub)
        if (sub.isCurrent) subs.all().firstOrNull()?.let { setSubscriptionCurrent(it.id) }
    }

    // ---- 频道 ----
    suspend fun refreshChannels(subId: Long): Int = withContext(Dispatchers.IO) {
        val sub = subs.byId(subId) ?: return@withContext 0
        val parsed = M3uParser.parse(fetcher.fetchText(sub.m3uUrl))
        chans.clearFor(subId)
        chans.insertAll(parsed.map {
            Channel(subscriptionId = subId, name = it.name, logoUrl = it.logo,
                groupTitle = it.group, streamUrl = it.url, tvgId = it.tvgId, sortIndex = it.order)
        })
        parsed.size
    }

    suspend fun channels(subId: Long): List<Channel> = withContext(Dispatchers.IO) { chans.forSub(subId) }
    suspend fun groups(subId: Long): List<String> = withContext(Dispatchers.IO) { chans.groupsFor(subId) }
    suspend fun channelCount(subId: Long): Int = withContext(Dispatchers.IO) { chans.countFor(subId) }

    // ---- 收藏（全局） ----
    suspend fun favorites(): List<Favorite> = withContext(Dispatchers.IO) { favs.all() }

    /** 返回 true = 现在是收藏状态 */
    suspend fun toggleFavorite(name: String, logoUrl: String?, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        val key = FavoriteKey.of(name, streamUrl)
        if (favs.byKey(key) != null) { favs.deleteByKey(key); false }
        else {
            favs.insert(Favorite(name = name.trim(), logoUrl = logoUrl, streamUrl = streamUrl.trim(),
                dedupeKey = key, addedAt = System.currentTimeMillis()))
            true
        }
    }

    suspend fun isFavorited(name: String, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        favs.byKey(FavoriteKey.of(name, streamUrl)) != null
    }

    // ---- EPG ----
    suspend fun epgCount(): Int = withContext(Dispatchers.IO) { epgs.count() }

    suspend fun refreshEpg(sub: Subscription): Int = withContext(Dispatchers.IO) {
        val url = sub.epgUrl ?: return@withContext 0
        val list = BufferedInputStream(fetcher.stream(url)).use { XmlTvParser.parse(it) }
        epgs.clear()
        list.asSequence().flatMap { p -> p.channelKeys.asSequence().map { k -> EpgProgramme(k, p.title, p.description, p.startMillis, p.stopMillis) } }
            .chunked(500).forEach { epgs.insertAll(it) }
        list.size
    }

    suspend fun nowNext(name: String, tvgId: String?): Pair<EpgProgramme?, EpgProgramme?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (k in EpgMatcher.keyForChannel(tvgId, name)) {
            val rows = epgs.upcoming(k, now)
            if (rows.isNotEmpty()) return@withContext EpgMatcher.nowNext(rows, { it.startTime }, { it.endTime }, now)
        }
        null to null
    }

    suspend fun channelNow(name: String, tvgId: String?): EpgProgramme? = nowNext(name, tvgId).first

    suspend fun todayProgrammes(name: String, tvgId: String?): List<EpgProgramme> = withContext(Dispatchers.IO) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayStart = c.timeInMillis
        val dayEnd = c.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        for (k in EpgMatcher.keyForChannel(tvgId, name)) {
            val rows = epgs.day(k, dayStart, dayEnd)
            if (rows.isNotEmpty()) return@withContext rows
        }
        emptyList()
    }

    // ---- 状态 ----
    suspend fun saveState(key: String, value: String) = withContext(Dispatchers.IO) { state.put(AppState(key, value)) }
    suspend fun readState(key: String): String? = withContext(Dispatchers.IO) { state.get(key) }
}
