package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.Subscription
import kotlinx.coroutines.launch

/**
 * 订阅管理双栏（spec 2026-11-11-ui-batch §④）：左=卡片列表（焦点即选中），右=详情+操作列。
 * 操作恒作用于焦点订阅（原「先选中再点底部」依赖被本布局天然取代，裁决⑥覆盖）。
 */
class SubsActivity : AppCompatActivity() {
    private val repo get() = (application as App).repo
    private var selected: Subscription? = null
    private var counts: Map<Long, Int> = emptyMap()
    private var refreshing = false
    private lateinit var subDetail: TextView
    private val adapter: SubAdapter by lazy {
        // 焦点即选中：不 notifyDataSetChanged——焦点高亮（focus_bg 焦点态）已表达选中态，
        // 重绑反而会销毁刚获焦的行把焦点甩出列表（Review Focus 6）
        SubAdapter { s -> selected = s; updateDetail() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subs)
        val list: RecyclerView = findViewById(R.id.subList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        subDetail = findViewById(R.id.subDetail)

        findViewById<View>(R.id.btnAdd).setOnClickListener { startActivity(Intent(this, SubEditActivity::class.java)) }
        findViewById<View>(R.id.btnEdit).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            startActivity(Intent(this, SubEditActivity::class.java).putExtra(SubEditActivity.EXTRA_SUB_ID, s.id))
        }
        findViewById<View>(R.id.btnCurrent).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch { repo.setSubscriptionCurrent(s.id); reload() }
        }
        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch {
                if (refreshing) { toast("刷新进行中…"); return@launch }
                refreshing = true
                toast("刷新中…")
                try {
                    val n = repo.refreshChannels(s.id)
                    var e = 0
                    if (s.epgUrl != null) e = repo.refreshEpg(s)
                    toast("频道 $n 个，EPG 节目 $e 个")
                    reload()  // 刷新时间上屏（lastUpdatedAt 已随 refreshChannels 写入）
                } catch (ex: Exception) { toast("刷新失败：${ex.message}") }
                finally { refreshing = false }
            }
        }
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除订阅")
                .setMessage("确定删除『${s.name}』？其频道缓存将一并清除。")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch { repo.deleteSubscription(s); selected = null; reload() }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() = lifecycleScope.launch {
        val all = repo.subscriptions()
        counts = all.associate { it.id to repo.channelCount(it.id) }
        adapter.submit(all, counts)
        // 按 id 重解析为新实体：改名/刷新返回后右栏详情不得停留旧值（终审 I3）；
        // 焦点行的 onSelect 会在重绑后再次触发并覆盖 selected，「焦点即选中」语义不受影响
        selected = reselectSubs(all, selected)
        updateDetail()
    }

    /** 右栏详情：名称/URL/台数与刷新时间（subMetaText）/EPG 行 */
    private fun updateDetail() {
        val s = selected
        if (s == null) {
            subDetail.text = "还没有订阅：点右上『＋ 添加订阅』录入 m3u 链接"
            return
        }
        subDetail.text = buildString {
            appendLine("名称：${s.name}")
            appendLine("地址：${s.m3uUrl}")
            appendLine(subMetaText(counts[s.id] ?: 0, s.lastUpdatedAt, System.currentTimeMillis()))
            append(if (s.epgUrl != null) "EPG：已配置" else "EPG：未配置")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** reload 后按 id 重解析选中订阅：旧 id 消失退「当前」再退首个，空表 null（终审 I3，JVM 可测） */
fun reselectSubs(all: List<Subscription>, prev: Subscription?): Subscription? =
    all.firstOrNull { it.id == prev?.id } ?: all.firstOrNull { it.isCurrent } ?: all.firstOrNull()

private class SubAdapter(val onSelect: (Subscription) -> Unit) : RecyclerView.Adapter<SubAdapter.VH>() {
    private var items: List<Subscription> = emptyList()
    private var counts: Map<Long, Int> = emptyMap()
    fun submit(list: List<Subscription>, counts: Map<Long, Int>) { items = list; this.counts = counts; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.subName)
        val meta: TextView = v.findViewById(R.id.subMeta)
        val badge: TextView = v.findViewById(R.id.subBadge)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sub, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = items[pos]
        h.name.text = s.name
        h.meta.text = subMetaText(counts[s.id] ?: 0, s.lastUpdatedAt, System.currentTimeMillis())
        h.badge.text = if (s.isCurrent) "当前" else "未启用"
        h.badge.setTextColor(if (s.isCurrent) 0xFF6FE28A.toInt() else 0xFF8899BB.toInt())
        h.itemView.setOnClickListener { onSelect(s) }
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onSelect(s) }
    }
}
