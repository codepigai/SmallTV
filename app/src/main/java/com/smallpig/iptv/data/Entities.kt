package com.smallpig.iptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val m3uUrl: String,
    val epgUrl: String? = null,
    val isCurrent: Boolean = false,
    val lastUpdatedAt: Long = 0, // 最近一次全量拉频道成功时刻（0=从未，历史行迁移默认 0）
)

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val tvgId: String?,
    val sortIndex: Int,
)

@Entity(tableName = "favorites", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val dedupeKey: String,
    val addedAt: Long,
)

@Entity(tableName = "epg_programmes", primaryKeys = ["channelKey", "startTime", "title"])
data class EpgProgramme(
    val channelKey: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
)

@Entity(tableName = "app_state")
data class AppState(@PrimaryKey val key: String, val value: String)
