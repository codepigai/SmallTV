package com.smallpig.iptv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.EpgProgramme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_TVG_ID = "tvg_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val tvgId = intent.getStringExtra(EXTRA_TVG_ID)
        findViewById<TextView>(R.id.epgTitle).text = "$name · 今日节目单"
        val list: RecyclerView = findViewById(R.id.epgList)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = EpgAdapter()
        list.adapter = adapter
        (application as App).repo.let { repo ->
            lifecycleScope.launch {
                val rows = repo.todayProgrammes(name, tvgId)
                adapter.submit(rows)
                val now = System.currentTimeMillis()
                val idx = rows.indexOfLast { it.startTime <= now }
                if (idx >= 0) list.scrollToPosition(maxOf(0, idx - 2))
                if (rows.isEmpty()) findViewById<TextView>(R.id.epgTitle).text = "$name · 今日节目单（无节目信息）"
            }
        }
    }
}

private class EpgAdapter : RecyclerView.Adapter<EpgAdapter.VH>() {
    private var items: List<EpgProgramme> = emptyList()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val now = System.currentTimeMillis()
    fun submit(list: List<EpgProgramme>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.epgTime)
        val title: TextView = v.findViewById(R.id.epgName)
        val live: TextView = v.findViewById(R.id.epgLive)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]
        h.time.text = "${fmt.format(Date(p.startTime))} – ${fmt.format(Date(p.endTime))}"
        h.title.text = p.title
        h.live.text = if (p.startTime <= now && p.endTime > now) "正在播出" else ""
    }
}
