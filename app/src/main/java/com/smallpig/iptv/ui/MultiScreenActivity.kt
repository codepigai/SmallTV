package com.smallpig.iptv.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.net.IptvLoadPolicy
import kotlinx.coroutines.launch

/**
 * 多屏观看（spec 2026-11-11）：2/4 格并发直播、逐格换台、单路声音、长按 BACK 退出。
 * 每格一个独立 ExoPlayer，复刻单屏三层断流防护（PlaybackActivity 注释）；
 * 所有 prepare() 经 [StartGate] 错峰 800ms，防中继源并发限流（spec §6）；
 * 声音=音量路由（AudioRouter），刻意不申请系统 AudioFocus（spec §7/§13③）。
 */
class MultiScreenActivity : AppCompatActivity() {
    companion object {
        private const val LIVE_OFFSET_MS = 15_000L   // 层1：同单屏，避开头部分片 403 窗口
        private const val MAX_AUTO_RETRY = 20L       // 层3：fatal 重连上限，同单屏
        private const val STAGGER_MS = 800L          // 错峰起播间隔（spec §6）
    }

    /** 单格视图 + 单格播放器状态。player 首次选台时创建，之后换台复用实例。 */
    private class Cell(
        val index: Int,
        val root: FrameLayout,
        val pv: PlayerView,
        val nameLabel: TextView,
        val statusText: TextView,
        val speaker: TextView,
    ) {
        var player: ExoPlayer? = null
        var row: PickRow? = null
        var reconnects = 0L
        var pendingStart = false    // stopped 期间到达的起播请求，回 onStart 重投（终审 #1）
        var needsReconnect = false  // stopped 时撤销的重连定时器，回前台续联
        lateinit var reconnect: Runnable   // createCell 里绑定 Runnable { doReconnect(cell) }
    }

    private val repo get() = (application as App).repo
    private val handler = Handler(Looper.getMainLooper())
    private val router = AudioRouter()
    private val gate = StartGate(STAGGER_MS, { SystemClock.elapsedRealtime() }, { r, d -> handler.postDelayed(r, d) })
    private val cells = mutableListOf<Cell>()
    private var mode = 0
    private var selected = -1
    private var started = false   // onStart..onStop 之间才允许起播/续播（终审 #1）

    private lateinit var gridContainer: FrameLayout
    private lateinit var pickerOverlay: View
    private lateinit var drawer: ChannelDrawer
    private var drawerTarget: Cell? = null
    private lateinit var modeText: TextView
    private lateinit var selText: TextView
    private lateinit var audioText: TextView

    // BACK 分层（spec §4：短按=逐层收起，≥700ms=退出；400ms 起亮提示）。
    // 长短按以事件时间差（UP.eventTime - DOWN.downTime）在 UP 裁决，不用墙钟定时器判退出——
    // 主线程被 4 路起播占用时定时器可能先于排队中的 UP 触发，把 300ms 短按误判成退出（终审 #3，违反 §2 反目标）。
    private var backPending = false
    private var backDownAt = 0L
    private val backHint = Runnable { Toast.makeText(this, "继续按住退出多屏", Toast.LENGTH_SHORT).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_screen)
        gridContainer = findViewById(R.id.gridContainer)
        pickerOverlay = findViewById(R.id.pickerOverlay)
        modeText = findViewById(R.id.multiModeText)
        selText = findViewById(R.id.multiSelText)
        audioText = findViewById(R.id.multiAudioText)
        drawer = ChannelDrawer(findViewById(android.R.id.content), repo, lifecycleScope)
        // 布局选择层每次进入都弹（spec §3.2）；选完即切分，播放中不可换布局（§13①）
        findViewById<View>(R.id.pickerTwo).setOnClickListener { pickLayout(2) }
        findViewById<View>(R.id.pickerFour).setOnClickListener { pickLayout(4) }
        pickerOverlay.visibility = View.VISIBLE
        findViewById<View>(R.id.pickerTwo).requestFocus()
    }

    private fun pickLayout(n: Int) {
        buildGrid(n)
        pickerOverlay.visibility = View.GONE
        cells.firstOrNull()?.root?.requestFocus()   // 首焦进 1 格
        // 无订阅时空格文案改指引（Review Focus #6 格内侧，终审 #5；抽屉侧 ChannelDrawer.load 已有）
        lifecycleScope.launch {
            if (repo.subscriptions().isNotEmpty()) return@launch   // 判空用订阅表（与抽屉 pickSubscription 的兜底一致）
            cells.forEach { c ->
                c.statusText.text = "暂无订阅：请到『订阅管理』添加"
                c.statusText.visibility = View.VISIBLE
            }
        }
    }

    /** 对选中格开抽屉换台/选台；开着则先收再开（post 串行滑移动画） */
    private fun openDrawerFor(cell: Cell) {
        drawerTarget = cell
        val show = {
            drawer.show(cell.row?.url) { row ->
                drawerTarget?.let { t ->
                    playInCell(t, row)
                    setAudioOwner(t.index)   // 声音自动切给最后选台的格（spec §3.4）
                    drawer.hide()
                    t.root.requestFocus()    // 焦点回格（Review Focus #3）
                }
            }
        }
        if (drawer.isOpen) drawer.hide { show() } else show()   // 收起动画结束再重开，无 post 竞态（终审 #4）
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.repeatCount == 0) {
                backPending = true
                backDownAt = event?.downTime?.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
                handler.postDelayed(backHint, 400)
            }
            return true   // 系统默认 BACK 永不可达
        }
        if (event?.repeatCount != 0) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {   // 声音切到选中格（spec §4/§7）
                if (pickerOverlay.visibility == View.VISIBLE) return true
                val row = cells.firstOrNull { it.index == selected }?.row
                if (row == null) Toast.makeText(this, "本格还没有频道", Toast.LENGTH_SHORT).show()
                else {
                    setAudioOwner(selected)
                    Toast.makeText(this, "声音已切到 ${row.name}", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {   // 确定：对选中格开抽屉（overlay/抽屉开着时不截）
                if (!drawer.isOpen && pickerOverlay.visibility != View.VISIBLE) {
                    cells.firstOrNull { it.root.hasFocus() }?.let { openDrawerFor(it); return true }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backPending) {
                backPending = false
                handler.removeCallbacks(backHint)
                if (!isFinishing) {
                    val downAt = event?.downTime?.takeIf { it > 0 } ?: backDownAt
                    val heldMs = (event?.eventTime ?: SystemClock.uptimeMillis()) - downAt
                    when {
                        heldMs >= 700 -> finish()   // 长按退出（松手触发：短按绝不误退优先于「按住瞬间退」）
                        !drawer.hide() ->            // 短按逐层收起：抽屉 → 提示（spec §3.7）
                            Toast.makeText(this, "长按返回键退出多屏", Toast.LENGTH_SHORT).show()
                        else ->                      // 收抽屉后焦点必须回格，否则确定键变哑（终审 #2，Review Focus #3）
                            cells.firstOrNull { it.index == selected }?.root?.requestFocus()
                    }
                }
            }
            return true   // 与 DOWN 成对消费
        }
        return super.onKeyUp(keyCode, event)
    }

    // —— 网格构建 ——

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun createCell(index: Int): Cell {
        val root = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.BLACK)
            foreground = ContextCompat.getDrawable(context, R.drawable.cell_focus_frame)
            isFocusable = true
            setOnFocusChangeListener { _, has -> if (has) selectCell(index) }
        }
        val pv = PlayerView(this).apply {
            setUseController(false)   // 多格禁内置控制器（同单屏真机反馈 #3）
            setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)   // 16:9 在格内自然居中（spec §5）
        }
        root.addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // 未选台：左上角标「格 N」，中央提示选台（spec §3.3）
        val nameLabel = overlayText("格 ${index + 1}")
        root.addOverlay(nameLabel, Gravity.TOP or Gravity.START)
        val statusText = overlayText("确定 选频道").apply {
            setTextColor(0xFFAAAAAA.toInt()); textSize = 14f
        }
        root.addOverlay(statusText, Gravity.CENTER)
        val speaker = overlayText("🔊").apply { visibility = View.GONE }
        root.addOverlay(speaker, Gravity.BOTTOM or Gravity.END)
        val cell = Cell(index, root, pv, nameLabel, statusText, speaker)
        cell.reconnect = Runnable { doReconnect(cell) }
        cells += cell
        return cell
    }

    private fun overlayText(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        setBackgroundColor(0x80000000.toInt())
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    private fun FrameLayout.addOverlay(tv: TextView, gravity: Int) {
        addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity).apply {
            if (gravity and Gravity.BOTTOM != 0) bottomMargin = dp(4)
            if (gravity and Gravity.END != 0) marginEnd = dp(4)
        })
    }

    private fun buildGrid(mode: Int) {
        this.mode = mode
        cells.clear()
        gridContainer.removeAllViews()
        val rowLp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        if (mode == 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            for (i in 0 until 2) row.addView(createCell(i).root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            gridContainer.addView(row)
        } else {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            for (r in 0 until 2) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until 2) line.addView(createCell(r * 2 + c).root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                col.addView(line, rowLp())
            }
            gridContainer.addView(col)
        }
        linkGridNavigation()
        modeText.text = "多屏 ${mode}路"
    }

    /** 格间方向键绕回（spec §4）：2 屏左右互指；4 屏田字上下左右互指 */
    private fun linkGridNavigation() {
        val ids = cells.map { it.root.id }
        fun link(i: Int, l: Int, r: Int, u: Int, d: Int) = with(cells[i].root) {
            nextFocusLeftId = l; nextFocusRightId = r; nextFocusUpId = u; nextFocusDownId = d
        }
        if (mode == 2) {
            link(0, ids[1], ids[1], View.NO_ID, View.NO_ID)
            link(1, ids[0], ids[0], View.NO_ID, View.NO_ID)
        } else {
            link(0, ids[1], ids[1], ids[2], ids[2])
            link(1, ids[0], ids[0], ids[3], ids[3])
            link(2, ids[3], ids[3], ids[0], ids[0])
            link(3, ids[2], ids[2], ids[1], ids[1])
        }
    }

    /** 焦点所在即选中格：描边白框 + 信息条联动（spec §5） */
    private fun selectCell(index: Int) {
        selected = index
        cells.forEach { it.root.isSelected = it.index == index }
        selText.text = "选中：" + (cells.firstOrNull { it.index == index }?.row?.name ?: "空")
    }

    // —— 播放器与三层防护 ——

    private fun ensurePlayer(cell: Cell) {
        if (cell.player != null) return
        // 构建与单屏 PlaybackActivity:151-156 一致；差异仅两处：禁自动焦点 + 绑本格 PlayerView
        val msFactory = DefaultMediaSourceFactory(DefaultHttpDataSource.Factory())
            .setLiveTargetOffsetMs(LIVE_OFFSET_MS)
            .setLoadErrorHandlingPolicy(IptvLoadPolicy())
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(msFactory).build()
        p.setAudioAttributes(AudioAttributes.DEFAULT, false)   // 声音路由交 AudioRouter（spec §7）
        cell.pv.player = p                                     // 必须绑定：漏了=永久 BUFFERING（探针教训）
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { scheduleReconnect(cell, error.errorCodeName) }
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> onCellRecovered(cell)
                    Player.STATE_BUFFERING -> if (cell.row != null && cell.reconnects == 0L) setStatus(cell, "缓冲中…")
                    else -> Unit
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) onCellRecovered(cell) }
        })
        cell.player = p
        router.attach(cell.index, p::setVolume)   // 初始音量随当前 owner
    }

    /** 选台/换台都走这里：本格重开流，不影响他格（spec §6） */
    private fun playInCell(cell: Cell, row: PickRow) {
        ensurePlayer(cell)
        handler.removeCallbacks(cell.reconnect)
        cell.reconnects = 0
        cell.row = row
        cell.nameLabel.text = row.name
        if (cell.index == selected) selectCell(cell.index)   // 信息条「选中」跟随换台
        setStatus(cell, "起播中…")
        val p = cell.player!!
        val url = row.url
        gate.request {
            if (isFinishing || isDestroyed || cell.player !== p) return@request   // 槽位到达时格已释放/换机
            if (!started) { cell.pendingStart = true; return@request }             // stopped 期间不拉流（终审 #1）
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun scheduleReconnect(cell: Cell, errorName: String) {
        handler.removeCallbacks(cell.reconnect)
        if (cell.reconnects >= MAX_AUTO_RETRY) {
            setStatus(cell, "${cell.row?.name ?: "频道"}　播放失败：$errorName（已停止自动重连，可重新选台）")
            return
        }
        val delay = backoffMs(cell.reconnects)
        cell.reconnects++
        setStatus(cell, "${cell.row?.name ?: "频道"}　播放失败：$errorName（${delay / 1000}s 后第 ${cell.reconnects} 次自动重连）")
        handler.postDelayed(cell.reconnect, delay)
    }

    private fun doReconnect(cell: Cell) {
        if (isFinishing || isDestroyed) return
        val p = cell.player ?: return
        setStatus(cell, "重连中…")
        gate.request {
            if (cell.player === p && !isFinishing && !isDestroyed) p.prepare()
        }
    }

    private fun onCellRecovered(cell: Cell) {
        handler.removeCallbacks(cell.reconnect)
        cell.reconnects = 0
        setStatus(cell, null)
    }

    private fun setStatus(cell: Cell, text: String?) {
        if (text == null) {
            cell.statusText.visibility = View.GONE
        } else {
            cell.statusText.text = text
            cell.statusText.visibility = View.VISIBLE
        }
    }

    // —— 声音（spec §7）——

    private fun setAudioOwner(index: Int) {
        router.setOwner(index)
        cells.forEach { it.speaker.visibility = if (it.index == router.owner) View.VISIBLE else View.GONE }
        val name = cells.firstOrNull { it.index == router.owner }?.row?.name
        audioText.text = if (name == null) "无声道" else "🔊 $name"
    }

    // —— 生命周期 ——

    override fun onStart() {
        super.onStart()
        started = true
        cells.forEach { c ->
            if (c.needsReconnect) {
                c.needsReconnect = false
                handler.postDelayed(c.reconnect, 1000)   // 回前台 1s 后续联（重连额度保留）
            }
            if (c.pendingStart) {
                c.pendingStart = false
                c.row?.let { playInCell(c, it) }         // stopped 期间到达的起播请求重投 gate
            } else if (c.row != null) {
                c.player?.playWhenReady = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        started = false
        cells.forEach { c ->
            c.player?.playWhenReady = false
            // Handler.hasCallbacks 是 API 28，minSdk 23 用额度推断：reconnects>0 且未恢复 = 重连在途
            // （恢复时 onCellRecovered 把 reconnects 归零；误判多一次 prepare 无害）
            if (c.player != null && c.row != null && c.reconnects > 0L) {
                handler.removeCallbacks(c.reconnect)     // 不在后台烧源/烧额度（终审 #1，spec §2 反目标）
                c.needsReconnect = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        cells.forEach { c ->
            c.pv.player = null   // 先解绑再释放（Review Focus #4）
            c.player?.release()
            c.player = null
            router.detach(c.index)
        }
        cells.clear()
    }
}
