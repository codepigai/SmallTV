package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.Channel
import com.smallpig.iptv.data.Favorite
import com.smallpig.iptv.data.Subscription
import com.smallpig.iptv.parse.FavoriteKey
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private val repo get() = (application as App).repo

    private lateinit var panelBrowse: View
    private lateinit var statusBar: TextView
    private lateinit var groupList: RecyclerView
    private lateinit var channelList: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var tabs: List<TextView>

    private var sub: Subscription? = null
    private var channels: List<Channel> = emptyList()
    private var favs: List<Favorite> = emptyList()
    private var favKeys: Set<String> = emptySet()
    private var currentGroup: String? = null
    private var showingFav = false
    private var focusedChannel: Channel? = null
    private var focusedFavorite: Favorite? = null
    private var epgRefreshing = false
    private var epgLastOk = 0L

    private val groupAdapter by lazy { GroupAdapter { group -> currentGroup = group; channelAdapter.submit(visibleChannels()) } }
    private val channelAdapter by lazy {
        ChannelAdapter(
            onClick = { play(it) },
            onFocus = { focusedChannel = it },
            isFav = { FavoriteKey.of(it.name, it.streamUrl) in favKeys },
            epgFor = { ch -> repo.channelNow(ch.name, ch.tvgId)?.title },
            scope = lifecycleScope,
        )
    }
    private val favAdapter by lazy { FavoriteAdapter(onClick = { playFavorite(it) }, onFocus = { focusedFavorite = it }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        panelBrowse = findViewById(R.id.panelBrowse)
        statusBar = findViewById(R.id.statusBar)
        groupList = findViewById(R.id.groupList); channelList = findViewById(R.id.channelList); favList = findViewById(R.id.favList)
        tabs = listOf(findViewById(R.id.tabAll), findViewById(R.id.tabFav), findViewById(R.id.tabSubs))
        tabs[0].setOnClickListener { showTab(0) }
        tabs[1].setOnClickListener { showTab(1) }
        tabs[2].setOnClickListener { startActivity(Intent(this, SubsActivity::class.java)) }
        groupList.layoutManager = LinearLayoutManager(this); groupList.adapter = groupAdapter
        channelList.layoutManager = LinearLayoutManager(this); channelList.adapter = channelAdapter
        favList.layoutManager = LinearLayoutManager(this); favList.adapter = favAdapter
        showTab(0)
        findViewById<View>(R.id.tabAll).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { reload() }
    }

    private fun showTab(idx: Int) {
        showingFav = idx == 1
        focusedChannel = null
        focusedFavorite = null
        panelBrowse.visibility = if (showingFav) View.GONE else View.VISIBLE
        favList.visibility = if (showingFav) View.VISIBLE else View.GONE
        tabs.forEachIndexed { i, t -> t.isSelected = i == idx }
        (if (showingFav) favList else channelList).requestFocus()
    }

    private suspend fun reload() {
        favs = repo.favorites()
        favKeys = favs.map { it.dedupeKey }.toSet()
        favAdapter.submit(favs)
        val s = repo.currentSubscription()
        sub = s
        if (s == null) {
            statusBar.text = "还没有订阅：请到『订阅管理』添加 m3u 订阅链接"
            channels = emptyList()
            groupAdapter.submit(listOf())
            channelAdapter.submit(emptyList())
            return
        }
        channels = repo.channels(s.id)
        statusBar.text = "当前订阅：${s.name}（${channels.size} 个频道）　菜单键=收藏/取消收藏"
        groupAdapter.submit(listOf<String?>(null) + repo.groups(s.id))
        if (currentGroup != null && visibleChannels().isEmpty()) currentGroup = null
        channelAdapter.submit(visibleChannels())
        if (!epgRefreshing && s.epgUrl != null && System.currentTimeMillis() - epgLastOk > EPG_REFRESH_MS) {
            epgRefreshing = true
            lifecycleScope.launch {
                try {
                    repo.refreshEpg(s)
                    epgLastOk = System.currentTimeMillis()
                    channelAdapter.notifyDataSetChanged()
                } catch (_: Exception) {}
                epgRefreshing = false
            }
        }
    }

    private fun visibleChannels(): List<Channel> =
        channels.filter { currentGroup == null || it.groupTitle == currentGroup }

    private fun play(ch: Channel) {
        startActivity(Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_NAME, ch.name)
            putExtra(PlaybackActivity.EXTRA_URL, ch.streamUrl)
            putExtra(PlaybackActivity.EXTRA_TVG_ID, ch.tvgId)
        })
    }

    private fun playFavorite(f: Favorite) {
        startActivity(Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_NAME, f.name)
            putExtra(PlaybackActivity.EXTRA_URL, f.streamUrl)
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event?.action == KeyEvent.ACTION_DOWN) {
            if (showingFav) {
                val f = focusedFavorite
                if (f != null) {
                    lifecycleScope.launch { repo.toggleFavorite(f.name, f.logoUrl, f.streamUrl); reload() }
                    return true
                }
            } else {
                val ch = focusedChannel
                if (ch != null) {
                    lifecycleScope.launch {
                        val nowFav = repo.toggleFavorite(ch.name, ch.logoUrl, ch.streamUrl)
                        Toast.makeText(this@HomeActivity, if (nowFav) "已收藏 ${ch.name}" else "已取消收藏", Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val EPG_REFRESH_MS = 15 * 60_000L
    }
}

private class GroupAdapter(val onClick: (String?) -> Unit) : RecyclerView.Adapter<GroupAdapter.VH>() {
    private var items: List<String?> = emptyList()
    fun submit(list: List<String?>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.groupName) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        h.name.text = items[pos] ?: "全部"
        h.itemView.setOnClickListener { onClick(items[pos]) }
    }
}

private class ChannelAdapter(
    val onClick: (Channel) -> Unit,
    val onFocus: (Channel?) -> Unit,
    val isFav: (Channel) -> Boolean,
    val epgFor: suspend (Channel) -> String?,
    val scope: kotlinx.coroutines.CoroutineScope,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var items: List<Channel> = emptyList()
    fun submit(list: List<Channel>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.chName)
        val now: TextView = v.findViewById(R.id.chNow)
        val fav: TextView = v.findViewById(R.id.chFav)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.name.text = c.name
        h.now.text = ""
        h.fav.text = if (isFav(c)) "★" else ""
        val ctx = h.itemView.context
        if (c.logoUrl != null) Glide.with(ctx).load(c.logoUrl).into(h.logo) else h.logo.setImageDrawable(null)
        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.setOnFocusChangeListener { _, has -> onFocus(if (has) c else null) }
        scope.launch {
            val t = epgFor(c)
            if (h.bindingAdapterPosition == pos) h.now.text = t ?: ""
        }
    }
}

private class FavoriteAdapter(
    val onClick: (Favorite) -> Unit,
    val onFocus: (Favorite?) -> Unit,
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {
    private var items: List<Favorite> = emptyList()
    fun submit(list: List<Favorite>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.chName)
        val now: TextView = v.findViewById(R.id.chNow)
        val fav: TextView = v.findViewById(R.id.chFav)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]
        h.name.text = f.name
        h.now.text = "已收藏"
        h.fav.text = "★"
        val ctx = h.itemView.context
        if (f.logoUrl != null) Glide.with(ctx).load(f.logoUrl).into(h.logo) else h.logo.setImageDrawable(null)
        h.itemView.setOnClickListener { onClick(f) }
        h.itemView.setOnFocusChangeListener { _, has -> onFocus(if (has) f else null) }
    }
}
