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

class SubsActivity : AppCompatActivity() {
    private val repo get() = (application as App).repo
    private var selected: Subscription? = null
    private val adapter: SubAdapter by lazy { SubAdapter { s -> selected = s; adapter.notifyDataSetChanged() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subs)
        val list: RecyclerView = findViewById(R.id.subList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

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
                toast("刷新中…")
                try {
                    val n = repo.refreshChannels(s.id)
                    var e = 0
                    if (s.epgUrl != null) e = repo.refreshEpg(s)
                    toast("频道 $n 个，EPG 节目 $e 个")
                } catch (ex: Exception) { toast("刷新失败：${ex.message}") }
            }
        }
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch {
                repo.deleteSubscription(s); selected = null; reload()
            }
        }
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() = lifecycleScope.launch {
        val all = repo.subscriptions()
        val counts = all.associate { it.id to repo.channelCount(it.id) }
        adapter.submit(all, counts)
        if (selected?.let { s -> all.none { it.id == s.id } } == true) selected = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private class SubAdapter(val onClick: (Subscription) -> Unit) : RecyclerView.Adapter<SubAdapter.VH>() {
    private var items: List<Subscription> = emptyList()
    private var counts: Map<Long, Int> = emptyMap()
    fun submit(list: List<Subscription>, counts: Map<Long, Int>) { items = list; this.counts = counts; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.subName)
        val meta: TextView = v.findViewById(R.id.subMeta)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sub, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = items[pos]
        h.itemView.isSelected = s.isCurrent
        h.name.text = if (s.isCurrent) "★ ${s.name}" else s.name
        h.meta.text = "${counts[s.id] ?: 0} 个频道　" +
            (if (s.epgUrl != null) "EPG 已配置" else "无 EPG") + "　" + s.m3uUrl
        h.itemView.setOnClickListener { onClick(s) }
    }
}
