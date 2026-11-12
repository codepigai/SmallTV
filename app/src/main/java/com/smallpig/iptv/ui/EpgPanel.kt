package com.smallpig.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.R
import com.smallpig.iptv.data.EpgProgramme
import com.smallpig.iptv.data.Repository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 右侧滑出节目单面板（真机反馈 #5/#8）：在宿主页面内展示，不新建 Activity，播放不中断。
 * 宿主需在根 FrameLayout 中 include view_epg_panel。
 */
class EpgPanel(root: View, private val repo: Repository, private val scope: LifecycleCoroutineScope) {
    private val panel: View = root.findViewById(R.id.epgPanel)
    private val title: TextView = root.findViewById(R.id.epgPanelTitle)
    private val list: RecyclerView = root.findViewById(R.id.epgPanelList)
    private val adapter = EpgAdapter()
    var isOpen = false
        private set
    /** 焦点是否已在面板行内：播放页 UP/DOWN 拦截据此放行（真机反馈②，空/未落焦面板不得做死换台键） */
    val hasFocusInside: Boolean get() = list.hasFocus()
    private var name = ""
    private var tvgId: String? = null

    init {
        list.layoutManager = LinearLayoutManager(root.context)
        list.adapter = adapter
    }

    fun show(name: String, tvgId: String?) {
        this.name = name
        this.tvgId = tvgId
        val first = !isOpen
        isOpen = true
        if (first) {
            panel.visibility = View.VISIBLE
            // GONE 状态未测量：先设一次（width 可能为 0），post 布局就绪后再定位并从右缘滑入
            panel.translationX = panel.width.toFloat()
            panel.post {
                panel.translationX = panel.width.toFloat()
                panel.animate().translationX(0f).setDuration(220).start()
            }
        }
        load()
    }

    /** 数据外部刷新（如首页 EPG 后台刷新成功）时重拉；未打开则忽略。 */
    fun reload() {
        if (isOpen) load()
    }

    private fun load() {
        scope.launch {
            val rows = repo.todayProgrammes(name, tvgId)
            adapter.submit(rows)
            title.text = if (rows.isEmpty()) "$name · 今日节目单（无节目信息）" else "$name · 今日节目单"
            val now = System.currentTimeMillis()
            val idx = rows.indexOfLast { it.startTime <= now }
            val target = if (idx >= 0) maxOf(0, idx - 2) else 0
            if (idx >= 0) list.scrollToPosition(target)
            focusRowAfterLayout(list, target)  // post{} 早于布局会漏焦（真机反馈②），见 ListFocus.kt
        }
    }

    /** 收起面板；未打开返回 false（供 BACK 键判断是否消费）。 */
    fun hide(): Boolean {
        if (!isOpen) return false
        isOpen = false
        panel.animate().translationX(panel.width.toFloat())
            .setDuration(200)
            .withEndAction { panel.visibility = View.GONE; panel.translationX = 0f }
            .start()
        return true
    }
}

private class EpgAdapter : RecyclerView.Adapter<EpgAdapter.VH>() {
    private var items: List<EpgProgramme> = emptyList()
    private var now = System.currentTimeMillis()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    fun submit(list: List<EpgProgramme>) {
        if (items == list) return
        items = list
        now = System.currentTimeMillis()
        notifyDataSetChanged()
    }
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
