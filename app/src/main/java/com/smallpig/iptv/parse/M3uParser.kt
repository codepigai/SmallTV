package com.smallpig.iptv.parse

data class ParsedChannel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
    val order: Int,
)

object M3uParser {
    private data class Pending(
        val name: String?, val logo: String?, val group: String?, val tvgId: String?,
    )

    fun parse(text: String): List<ParsedChannel> {
        val clean = text.removePrefix("﻿")
        val out = ArrayList<ParsedChannel>()
        var pending: Pending? = null
        var extGrp: String? = null
        var order = 0
        for (raw in clean.split("\r\n", "\n")) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF") -> pending = parseExtInf(line)
                line.startsWith("#EXTGRP:") -> extGrp = line.substring(8).trim().ifBlank { null }
                line.startsWith("#") -> {}
                else -> {
                    val pair = splitNameUrl(line, pending?.name)
                    if (pair != null) {
                        val (name, url) = pair
                        out += ParsedChannel(
                            name = name.ifBlank { fallbackName(url) },
                            url = url,
                            logo = pending?.logo,
                            group = pending?.group ?: extGrp ?: "未分组",
                            tvgId = pending?.tvgId,
                            order = order++,
                        )
                    }
                    pending = null
                }
            }
        }
        return out
    }

    private fun parseExtInf(line: String): Pending? {
        val comma = line.indexOf(',')
        val attrs: String; val name: String
        if (comma < 0) { attrs = line; name = "" } else { attrs = line.substring(0, comma); name = line.substring(comma + 1).trim() }
        return Pending(
            name = name.ifBlank { null },
            logo = attr(attrs, "tvg-logo")?.ifBlank { null },
            group = attr(attrs, "group-title")?.ifBlank { null },
            tvgId = attr(attrs, "tvg-id"),
        )
    }

    private fun attr(attrs: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)

    private fun splitNameUrl(line: String, pendingName: String?): Pair<String, String>? {
        val ci = line.indexOf(',')
        if (ci > 0) {
            val u = line.substring(ci + 1).trim()
            if (isUrl(u) && !u.contains(' ')) return line.substring(0, ci).trim() to u
        }
        if (isUrl(line)) return (pendingName ?: "") to line
        return null
    }

    private fun isUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true) || s.contains("://")
    private fun fallbackName(url: String) = url.substringAfterLast('/').substringBefore('?').ifBlank { url }
}
