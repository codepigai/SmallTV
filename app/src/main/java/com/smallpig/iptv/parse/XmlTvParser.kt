package com.smallpig.iptv.parse

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

data class ParsedProgramme(
    val channelKeys: List<String>,
    val title: String,
    val description: String?,
    val startMillis: Long,
    val stopMillis: Long,
)

object XmlTvParser {
    private val TIME_RE = Pattern.compile("^(\\d{14})(?:\\.(\\d{1,3}))?([+-]\\d{4})?$")

    /** 流式解析 XMLTV；只保留 [今天00:00, 后天00:00) 的节目
     * 假定 <channel> 定义位于 <programme> 之前（XMLTV 惯例）；乱序时 programme 仅获得 id: 键，name: 键缺失，降级为不显示节目信息。
     */
    fun parse(input: InputStream, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): List<ParsedProgramme> {
        val windowStart = dayStart(now, zone, 0)
        val windowEnd = dayStart(now, zone, 2)

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        val displayNames = HashMap<String, ArrayList<String>>()
        val raw = ArrayList<Triple<String, Long, Long>>() // channel, start, stop（title 阶段再补）
        val titles = ArrayList<Pair<String?, String?>>()  // title, desc 与 raw 对齐
        val namesAt = ArrayList<List<String>>()           // programme 结束时已知的 display-name，与 raw 对齐
        var curChannelId: String? = null
        var pChannel: String? = null
        var pStart = 0L
        var pStop = 0L
        var text: StringBuilder? = null
        var pTitle: String? = null
        var pDesc: String? = null

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> curChannelId = parser.getAttributeValue(null, "id")
                    "display-name" -> { text = StringBuilder() }
                    "programme" -> {
                        pChannel = parser.getAttributeValue(null, "channel")
                        pStart = parseXmlTvTime(parser.getAttributeValue(null, "start"), zone) ?: -1
                        pStop = parseXmlTvTime(parser.getAttributeValue(null, "stop"), zone) ?: -1
                        pTitle = null; pDesc = null
                    }
                    "title" -> { text = StringBuilder() }
                    "desc" -> { text = StringBuilder() }
                }
                XmlPullParser.TEXT -> text?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "display-name" -> {
                        val v = text?.toString()?.trim()
                        val cid = curChannelId
                        if (!v.isNullOrEmpty() && cid != null)
                            displayNames.getOrPut(cid) { arrayListOf() }.add(v)
                        text = null
                    }
                    "channel" -> curChannelId = null
                    "title" -> { pTitle = text?.toString()?.trim(); text = null }
                    "desc" -> { pDesc = text?.toString()?.trim(); text = null }
                    "programme" -> {
                        val ch = pChannel
                        if (ch != null && pStart > 0 && pStop > 0 && pStop > windowStart && pStart < windowEnd) {
                            raw.add(Triple(ch, pStart, pStop)); titles.add(pTitle to pDesc)
                            namesAt.add(displayNames[ch]?.toList() ?: emptyList())
                        }
                        pChannel = null
                    }
                }
            }
            ev = parser.next()
        }

        val out = ArrayList<ParsedProgramme>(raw.size)
        for (i in raw.indices) {
            val (ch, s, e) = raw[i]
            val (title, desc) = titles[i]
            if (title.isNullOrEmpty()) continue
            val keys = LinkedHashSet<String>().apply {
                add("id:${ch.trim()}")
                namesAt[i].forEach { add(EpgMatcher.nameKey(it)) }
            }
            out.add(ParsedProgramme(keys.toList(), title, desc, s, e))
        }
        return out
    }

    fun parseXmlTvTime(raw: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (raw == null) return null
        val t = raw.trim().replace(" ", "")
        val m = TIME_RE.matcher(t)
        if (!m.matches()) return null
        val base = m.group(1)!!
        val fraction = m.group(2)
        val off = m.group(3) ?: ""
        val tz = if (off.isEmpty()) zone else TimeZone.getTimeZone("GMT$off")
        val c = Calendar.getInstance(tz)
        c.clear()
        c.set(
            base.substring(0, 4).toInt(), base.substring(4, 6).toInt() - 1, base.substring(6, 8).toInt(),
            base.substring(8, 10).toInt(), base.substring(10, 12).toInt(), base.substring(12, 14).toInt(),
        )
        if (fraction != null) c.set(Calendar.MILLISECOND, fraction.padEnd(3, '0').toInt())
        return c.timeInMillis
    }

    private fun dayStart(millis: Long, zone: TimeZone, plusDays: Int): Long {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
