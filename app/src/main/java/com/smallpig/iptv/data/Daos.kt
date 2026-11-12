package com.smallpig.iptv.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SubscriptionDao {
    @Insert suspend fun insert(s: Subscription): Long
    @Update suspend fun update(s: Subscription)
    @Delete suspend fun delete(s: Subscription)
    @Query("SELECT * FROM subscriptions ORDER BY id") suspend fun all(): List<Subscription>
    @Query("SELECT * FROM subscriptions WHERE id = :id") suspend fun byId(id: Long): Subscription?
    @Query("SELECT * FROM subscriptions WHERE isCurrent = 1 LIMIT 1") suspend fun current(): Subscription?
    @Query("UPDATE subscriptions SET isCurrent = 0") suspend fun clearCurrent()
    @Query("UPDATE subscriptions SET lastUpdatedAt = :ms WHERE id = :id") suspend fun touch(id: Long, ms: Long)
    @Query("UPDATE subscriptions SET isCurrent = 1 WHERE id = :id") suspend fun setCurrent(id: Long)

    /** 保证"仅一条 current"不变量的原子入口，调用方勿再手工 clear+set */
    @Transaction
    suspend fun setCurrentAtomically(id: Long) {
        clearCurrent()
        setCurrent(id)
    }
}

@Dao
interface ChannelDao {
    @Insert suspend fun insertAll(list: List<Channel>)
    @Query("DELETE FROM channels WHERE subscriptionId = :subId") suspend fun clearFor(subId: Long)
    @Query("SELECT * FROM channels WHERE subscriptionId = :subId ORDER BY sortIndex") suspend fun forSub(subId: Long): List<Channel>
    @Query("SELECT groupTitle FROM channels WHERE subscriptionId = :subId GROUP BY groupTitle ORDER BY MIN(sortIndex)") suspend fun groupsFor(subId: Long): List<String>
    @Query("SELECT COUNT(*) FROM channels WHERE subscriptionId = :subId") suspend fun countFor(subId: Long): Int

    /** 原子替换该订阅频道：取消/失败不会留下清完没写入的空表 */
    @Transaction
    suspend fun replaceFor(subId: Long, list: List<Channel>) {
        clearFor(subId)
        insertAll(list)
    }
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(f: Favorite)
    @Delete suspend fun delete(f: Favorite)
    @Query("DELETE FROM favorites WHERE dedupeKey = :key") suspend fun deleteByKey(key: String)
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") suspend fun all(): List<Favorite>
    @Query("SELECT * FROM favorites WHERE dedupeKey = :key") suspend fun byKey(key: String): Favorite?
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<EpgProgramme>)
    @Query("DELETE FROM epg_programmes") suspend fun clear()
    @Query("SELECT COUNT(*) FROM epg_programmes") suspend fun count(): Int
    @Query("SELECT * FROM epg_programmes WHERE channelKey = :key AND endTime > :from ORDER BY startTime")
    suspend fun upcoming(key: String, from: Long): List<EpgProgramme>
    @Query("SELECT * FROM epg_programmes WHERE channelKey = :key AND endTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime")
    suspend fun day(key: String, dayStart: Long, dayEnd: Long): List<EpgProgramme>

    @Transaction
    suspend fun replaceAll(list: List<EpgProgramme>) {
        clear()
        list.chunked(500).forEach { insertAll(it) }
    }
}

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_state WHERE key = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(v: AppState)
}
