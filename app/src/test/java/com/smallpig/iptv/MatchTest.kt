package com.smallpig.iptv

import com.smallpig.iptv.parse.EpgMatcher
import com.smallpig.iptv.parse.FavoriteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchTest {
    private data class Row(val s: Long, val e: Long, val t: String)

    @Test fun normalizeStripsSpacesAndUpperCases() {
        assertEquals("CCTV1", EpgMatcher.normalize("  CCTV 1 "))
        assertEquals("name:CCTV-1高清", EpgMatcher.nameKey("cctv-1高清"))
    }

    @Test fun keyForChannelPrefersTvgId() {
        assertEquals(listOf("id:x", "name:CCTV1"), EpgMatcher.keyForChannel("x", "cctv 1"))
        assertEquals(listOf("name:CCTV1"), EpgMatcher.keyForChannel(null, "CCTV1"))
        assertEquals(listOf("name:CCTV1"), EpgMatcher.keyForChannel("  ", "CCTV1"))
    }

    @Test fun favoriteKeyDedupesByNormalizedNameAndUrl() {
        assertEquals(FavoriteKey.of(" CCTV 1 ", "http://a"), FavoriteKey.of("cctv1", "http://a"))
        assertEquals(false, FavoriteKey.of("a", "http://1") == FavoriteKey.of("a", "http://2"))
    }

    @Test fun nowNextPicksCurrentAndFollowing() {
        val rows = listOf(Row(10, 20, "a"), Row(15, 25, "b"), Row(30, 40, "c"))
        val r = EpgMatcher.nowNext(rows, { it.s }, { it.e }, 16)
        assertEquals("b", r.first?.t); assertEquals("c", r.second?.t)
    }

    @Test fun nowNextBetweenProgrammes() {
        val rows = listOf(Row(10, 20, "a"), Row(30, 40, "c"))
        val r = EpgMatcher.nowNext(rows, { it.s }, { it.e }, 25)
        assertEquals(null, r.first); assertEquals("c", r.second?.t)
    }

    @Test fun nowNextEmpty() {
        val r = EpgMatcher.nowNext(emptyList<Row>(), { it.s }, { it.e }, 1)
        assertEquals(null, r.first); assertEquals(null, r.second)
    }
}
