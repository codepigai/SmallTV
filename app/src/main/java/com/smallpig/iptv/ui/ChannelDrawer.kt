package com.smallpig.iptv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.R
import com.smallpig.iptv.data.Repository
import com.smallpig.iptv.data.Subscription
import kotlinx.coroutines.launch

/**
 * 多屏右侧滑出频道抽屉（spec §6）：两列=分组|频道，行样式复用首页布局。
 * 类结构与滑入/收起语义仿 EpgPanel（hide 返回 Boolean 供 BACK 消费判定）。
 */
class ChannelDrawer(root: View, private val repo: Repository, private val scope: LifecycleCoroutineScope) {
    private val panel: View = root.findViewById(R.id.drawerPanel)
    private val title: TextView = root.findViewById(R.id.drawerTitle)
    private val subRow: LinearLayout = root.findViewById(R.id.drawerSubRow)
    private val groupList: RecyclerView = root.findViewById(R.id.drawerGroupList)
    private val channelList: RecyclerView = root.findViewById(R.id.drawerChannelList)
    private val density = root.context.resources.displayMetrics.density

    private var allRows: List<PickRow> = emptyList()
    private var favRows: List<PickRow> = emptyList()
    private var currentGroup: String? = null
    private var pickCallback: ((PickRow) -> Unit)? = null
    private var subId: Long? = null   // 抽屉切换行选中的订阅（null=当前订阅；跨订阅同屏方案 A）
    private var showSubChips = true   // 播放页传 false：换台恒在当前订阅（裁决6）；每次 show 记录参数，多屏默认 true 不受残留影响

    var isOpen = false
        private set

    private val groupAdapter = DrawerGroupAdapter { g ->
        currentGroup = g
        channelAdapter.submit(filterRows(allRows, g, favRows))
        channelList.post { channelList.getChildAt(0)?.requestFocus() }   // 组点击后焦点进频道列（同首页语义）
    }
    private val channelAdapter = DrawerChannelAdapter { row -> pickCallback?.invoke(row) }

    init {
        groupList.layoutManager = LinearLayoutManager(root.context)
        groupList.adapter = groupAdapter
        channelList.layoutManager = LinearLayoutManager(root.context)
        channelList.adapter = channelAdapter
    }

    /** 打开并定位到 currentUrl（该格当前频道）行；首次打开从右缘滑入（仿 EpgPanel.show）。 */
    fun show(currentUrl: String?, showSubChips: Boolean = true, onPick: (PickRow) -> Unit) {
        pickCallback = onPick
        this.showSubChips = showSubChips
        val first = !isOpen
        isOpen = true
        if (first) {
            panel.visibility = View.VISIBLE
            panel.translationX = panel.width.toFloat()
            panel.post {
                panel.translationX = panel.width.toFloat()
                panel.animate().translationX(0f).setDuration(220).start()
            }
        }
        load(currentUrl)
    }

    /** 收起抽屉；未打开返回 false（供 BACK 键判断是否消费）。afterClose 在滑出动画正常结束时执行（换格重开用，终审 #4）。 */
    fun hide(afterClose: (() -> Unit)? = null): Boolean {
        if (!isOpen) return false
        isOpen = false
        panel.animate().translationX(panel.width.toFloat())
            .setDuration(200)
            .withEndAction { panel.visibility = View.GONE; panel.translationX = 0f; afterClose?.invoke() }
            .start()
        return true
    }

    /** 订阅切换 chips（仅 ≥2 订阅时显示）；点击即在抽屉内换订阅重载列表（跨订阅同屏方案 A） */
    private fun buildSubChips(subs: List<Subscription>, currentUrl: String?) {
        val chosen = pickSubscription(subs, subId)?.id
        subRow.removeAllViews()
        for (s in subs) {
            val sel = s.id == chosen
            val chip = TextView(subRow.context).apply {
                text = s.name
                textSize = 13f
                setTextColor(if (sel) Color.WHITE else 0xFF8899BB.toInt())
                setBackgroundColor(if (sel) 0x33FFFFFF else Color.TRANSPARENT)
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                isFocusable = true
                setOnClickListener { subId = s.id; load(currentUrl) }
            }
            subRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            })
        }
    }

    private fun load(currentUrl: String?) {
        scope.launch {
            val subs = repo.subscriptions()
            if (showSubChips && subs.size > 1) { subRow.visibility = View.VISIBLE; buildSubChips(subs, currentUrl) } else subRow.visibility = View.GONE
            val sub = pickSubscription(subs, subId)
            if (sub == null) {   // 无订阅空态（Review Focus #6）
                title.text = "暂无订阅：请到『订阅管理』添加"
                allRows = emptyList(); favRows = emptyList()
                groupAdapter.submit(emptyList()); channelAdapter.submit(emptyList())
                return@launch
            }
            val favs = repo.favorites()
            allRows = channelsToRows(repo.channels(sub.id), favs.map { it.dedupeKey }.toSet())
            favRows = favoritesToRows(favs)
            title.text = "选择频道"
            currentGroup = null
            groupAdapter.submit(drawerGroups(repo.groups(sub.id), favs.size))
            val rows = filterRows(allRows, null, favRows)
            channelAdapter.submit(rows)
            val idx = currentUrl?.let { u -> rows.indexOfFirst { it.url == u } } ?: -1
            val target = if (idx >= 0) maxOf(0, idx - 2) else 0
            if (idx >= 0) channelList.scrollToPosition(target)
            channelList.post {
                (channelList.layoutManager?.findViewByPosition(target) ?: channelList.getChildAt(0))?.requestFocus()
            }
        }
    }
}

private class DrawerGroupAdapter(val onClick: (String?) -> Unit) : RecyclerView.Adapter<DrawerGroupAdapter.VH>() {
    private var items: List<String?> = emptyList()
    fun submit(list: List<String?>) { if (items == list) return; items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.groupName) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val g = items[pos]
        h.name.text = when (g) { null -> "全部"; GROUP_FAV -> "收藏"; else -> g }
        h.itemView.setOnClickListener { onClick(g) }
    }
}

private class DrawerChannelAdapter(val onClick: (PickRow) -> Unit) : RecyclerView.Adapter<DrawerChannelAdapter.VH>() {
    private var items: List<PickRow> = emptyList()
    fun submit(list: List<PickRow>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        // PickRow 无台标字段：item_channel 的 logo 位留空（spec 非目标：抽屉不做重样式）
        val name: TextView = v.findViewById(R.id.chName)
        val now: TextView = v.findViewById(R.id.chNow)
        val fav: TextView = v.findViewById(R.id.chFav)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = items[pos]
        h.name.text = r.name
        h.fav.text = if (r.fav) "★" else ""
        h.now.text = r.groupTitle?.takeIf { it != GROUP_FAV } ?: ""
        h.itemView.setOnClickListener { onClick(r) }
    }
}
