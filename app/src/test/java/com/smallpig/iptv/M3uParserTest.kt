package com.smallpig.iptv

import com.smallpig.iptv.parse.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test fun standardExtInf() {
        val text = "#EXTM3U\n" +
            "#EXTINF:-1 tvg-id=\"cctv1\" tvg-logo=\"http://l/1.png\" group-title=\"央视\",CCTV-1\n" +
            "http://s/1.m3u8\n" +
            "#EXTINF:-1 group-title=\"卫视\",湖南卫视\n" +
            "https://s/2.ts?token=ab,cd\n"
        val r = M3uParser.parse(text)
        assertEquals(2, r.size)
        assertEquals("CCTV-1", r[0].name)
        assertEquals("http://s/1.m3u8", r[0].url)
        assertEquals("http://l/1.png", r[0].logo)
        assertEquals("cctv1", r[0].tvgId)
        assertEquals("央视", r[0].group)
        assertEquals("湖南卫视", r[1].name)
        assertEquals("https://s/2.ts?token=ab,cd", r[1].url)
        assertEquals(0, r[0].order); assertEquals(1, r[1].order)
    }

    @Test fun simpleTxtNameCommaUrl() {
        val text = "CCTV-1,http://a/1.m3u8\nHunan TV,https://b/2.ts\n"
        val r = M3uParser.parse(text)
        assertEquals(2, r.size)
        assertEquals("CCTV-1", r[0].name); assertEquals("http://a/1.m3u8", r[0].url)
        assertEquals("未分组", r[0].group)
    }

    @Test fun bareUrlUsesFileName() {
        val r = M3uParser.parse("http://host/path/CCTV5.m3u8\n")
        assertEquals("CCTV5.m3u8", r[0].name)
    }

    @Test fun extGrpFallbackAndComments() {
        val text = "#EXTM3U\n#一些注释\n#EXTVLCOPT:http-user-agent=x\n#EXTGRP:卫视\nhttp://u/a.ts\n"
        val r = M3uParser.parse(text)
        assertEquals(1, r.size)
        assertEquals("卫视", r[0].group)
    }

    @Test fun extinfWithoutUrlDropped() {
        val r = M3uParser.parse("#EXTINF:-1 tvg-id=\"x\",孤儿频道\n")
        assertEquals(0, r.size)
    }

    @Test fun emptyExtInfNameFallsBack() {
        val r = M3uParser.parse("#EXTINF:-1,\nhttp://host/x.m3u8\n")
        assertEquals(1, r.size); assertEquals("x.m3u8", r[0].name)
    }

    @Test fun bomCrlfAndBlankLines() {
        val text = "﻿" + "#EXTM3U\r\n\r\n#EXTINF:-1 tvg-logo=\"\",A\r\nhttp://a\r\n\r\n"
        val r = M3uParser.parse(text)
        assertEquals(1, r.size); assertEquals("A", r[0].name)
        assertEquals(null, r[0].logo)
    }
}
