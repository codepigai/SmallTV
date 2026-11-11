package com.smallpig.iptv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.net.IptvLoadPolicy
import com.smallpig.iptv.net.RttProbe
import com.smallpig.iptv.parse.bitrateLabel
import com.smallpig.iptv.parse.codecDisplayName
import com.smallpig.iptv.parse.rttLabel
import kotlinx.coroutines.launch

class PlaybackActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
        const val EXTRA_TVG_ID = "tvg_id"   // 保持 Task 7 已提交值，勿改回 "tvgId"
        private const val LAST_CHANNEL_KEY = "lastChannel"
        private const val LIVE_OFFSET_MS = 15_000L   // 起播点离直播边缘的时移量：避开真机实测的头部分片 403 窗口
        private const val MAX_AUTO_RETRY = 20L       // 致命错误自动重连上限，超限后停手等用户操作
    }

    private val repo get() = (application as App).repo
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var infoPanel: View
    private lateinit var nowBar: TextView
    private lateinit var epgPanel: EpgPanel
    private lateinit var drawer: ChannelDrawer
    private var infoShown = false

    private var chName: String = ""
    private var chUrl: String = ""
    private var chTvgId: String? = null
    private var errListener: Player.Listener? = null

    private val handler = Handler(Looper.getMainLooper())

    private var barHasError = false
    private val hideBar = Runnable { nowBar.animate().alpha(0f).setDuration(800).start() }
    private fun touchBar() {
        nowBar.animate().cancel()
        nowBar.alpha = 1f
        handler.removeCallbacks(hideBar)
        if (!barHasError) handler.postDelayed(hideBar, 6000)
    }
    private fun showErrorBar(msg: String) {
        barHasError = true
        nowBar.animate().cancel()
        nowBar.alpha = 1f
        handler.removeCallbacks(hideBar)
        nowBar.text = msg
    }
    private fun hideBarNow() {
        handler.removeCallbacks(hideBar)
        nowBar.animate().cancel()
        nowBar.animate().alpha(0f).setDuration(150).start()
    }

    // —— 断流自动恢复（真机反馈 #6）——
    // 层1：起播点距直播边缘 15s（DefaultMediaSourceFactory.setLiveOffset），避开头部未就绪分片
    // 层2：IptvLoadPolicy 加载层多次短退避重试，扛 403 窗口
    // 层3：走到 fatal 后，这里按退避重新 prepare，直到恢复或用户操作
    private var reconnects = 0L
    private val reconnect = Runnable { doReconnect() }
    private fun cancelReconnect() {
        handler.removeCallbacks(reconnect)
        reconnects = 0L
    }
    private fun scheduleReconnect(errorName: String) {
        handler.removeCallbacks(reconnect)
        if (reconnects >= MAX_AUTO_RETRY) {
            showErrorBar("$chName　播放失败：$errorName（已停止自动重连，按『下键』换台或退出重进）")
            return
        }
        val delay = minOf(2000L * (1L shl reconnects.coerceAtMost(3).toInt()), 15_000L)
        reconnects++
        showErrorBar("$chName　播放失败：$errorName（${delay / 1000}s 后第 $reconnects 次自动重连，『下键』可换台）")
        handler.postDelayed(reconnect, delay)
    }
    private fun doReconnect() {
        if (isFinishing || !::player.isInitialized) return
        try { player.prepare() } catch (_: Exception) {}
    }
    private fun onPlaybackRecovered() {
        if (!barHasError && reconnects == 0L) return
        cancelReconnect()
        barHasError = false
        nowBar.text = chName
        touchBar()
        loadEpg()
    }

    // —— 信息条轮询与网络延迟探测（真机反馈 #4）——
    private var tick = 0
    private var lastRttMs = -1L
    private var estBps = -1L
    private var probing = false
    private val poll = object : Runnable {
        override fun run() {
            if (infoShown) {
                updateInfo()
                if (tick % 4 == 0) probeNet()
            }
            tick++
            handler.postDelayed(this, 1000)
        }
    }
    private fun probeNet() {
        if (probing) return
        probing = true
        val urlSnap = chUrl
        lifecycleScope.launch {
            val ms = RttProbe.probe(urlSnap)
            probing = false
            if (urlSnap == chUrl && ms >= 0) lastRttMs = ms
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chName = intent.getStringExtra(EXTRA_NAME) ?: "频道"
        chUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        chTvgId = intent.getStringExtra(EXTRA_TVG_ID)
        setContentView(R.layout.activity_playback)
        playerView = findViewById(R.id.playerView)
        infoPanel = findViewById(R.id.infoPanel)
        nowBar = findViewById(R.id.nowBar)
        epgPanel = EpgPanel(findViewById(android.R.id.content), repo, lifecycleScope)
        drawer = ChannelDrawer(findViewById(android.R.id.content), repo, lifecycleScope)
        nowBar.text = chName; touchBar()

        // media3 1.x：直播时移与重试策略都挂在 MediaSource.Factory 上（ExoPlayer.Builder 无对应 setter）
        val msFactory = DefaultMediaSourceFactory(DefaultHttpDataSource.Factory())
            .setLiveTargetOffsetMs(LIVE_OFFSET_MS)
            .setLoadErrorHandlingPolicy(IptvLoadPolicy())
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(msFactory)
            .build()
        // 带宽估计（码率回退显示用）：Media3 1.x 不再暴露 getBandwidthMeter，经 AnalyticsListener 收集
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                estBps = bitrateEstimate
            }
        })
        playerView.player = player
        attachErrorListener()
        player.setMediaItem(MediaItem.fromUri(chUrl))
        player.prepare()
        player.playWhenReady = true

        loadEpg()
        saveLastChannel()
    }

    override fun onStart() { super.onStart(); if (::player.isInitialized) { player.playWhenReady = true; handler.post(poll); touchBar() } }
    override fun onStop() { super.onStop(); handler.removeCallbacks(poll); if (::player.isInitialized) player.playWhenReady = false }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // BACK 不参与「按键重现信息条」，否则永远收不掉、退不出（真机反馈 #2）
        if (keyCode != KeyEvent.KEYCODE_BACK) touchBar()
        // 键位重构（spec 2026-11-11-ui-batch §③）：面板开着时焦点在面板行内、DPAD 由列表消费，
        // 本方法只处理无焦点消费的场景——上下换台天然不抢面板导航（Review Focus 1）
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { step(1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event?.repeatCount != 0) return true
                if (epgPanel.isOpen) epgPanel.hide()
                else { drawer.hide(); epgPanel.show(chName, chTvgId) }  // 面板互斥（裁决5）
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (event?.repeatCount != 0) return true
                infoShown = !infoShown; infoPanel.visibility = if (infoShown) View.VISIBLE else View.GONE
                if (infoShown) { updateInfo(); probeNet() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (drawer.isOpen) drawer.hide()
                else {
                    epgPanel.hide()  // 面板互斥（裁决5）；换台恒在当前订阅（裁决6：showSubChips=false）
                    drawer.show(chUrl, showSubChips = false) { row ->
                        drawer.hide()
                        restartWith(row.name, row.url, row.tvgId)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> return true  // 无功能（用户确认吞掉，裁决4）
            KeyEvent.KEYCODE_BACK -> {
                // 返回键逐层收起（Review Focus 2）：频道抽屉 → 节目单 → 信息面板 → nowBar → 退出
                if (drawer.isOpen) { drawer.hide(); return true }
                if (epgPanel.isOpen) { epgPanel.hide(); return true }
                if (infoShown) { infoShown = false; infoPanel.visibility = View.GONE; return true }
                if (nowBar.alpha > 0f) { hideBarNow(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun step(dir: Int) {
        lifecycleScope.launch {
            val sub = repo.currentSubscription() ?: return@launch
            val list = repo.channels(sub.id)
            if (list.isEmpty()) return@launch
            val idx = list.indexOfFirst { it.streamUrl == chUrl }
            val next = list[((if (idx < 0) 0 else idx) + dir + list.size) % list.size]
            restartWith(next.name, next.streamUrl, next.tvgId)
        }
    }

    private fun restartWith(name: String, url: String, tvgId: String?) {
        chName = name; chUrl = url; chTvgId = tvgId
        cancelReconnect()
        lastRttMs = -1L
        barHasError = false; nowBar.text = name; touchBar()
        if (epgPanel.isOpen) epgPanel.show(name, tvgId)  // 面板开着时切台：标题与内容跟随新频道
        attachErrorListener()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare(); player.playWhenReady = true
        loadEpg()
        saveLastChannel()
    }

    private fun attachErrorListener() {
        errListener?.let { player.removeListener(it) }
        val urlSnap = chUrl
        val nameSnap = chName
        val l = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (urlSnap != chUrl) return
                scheduleReconnect(error.errorCodeName)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onPlaybackRecovered()
            }
        }
        errListener = l
        player.addListener(l)
    }

    private fun saveLastChannel() {
        lifecycleScope.launch {
            repo.saveState(LAST_CHANNEL_KEY, "$chName|$chUrl|${chTvgId ?: ""}")
        }
    }

    private fun loadEpg() {
        lifecycleScope.launch {
            try {
                // 修正 A：Repository.refreshEpg 参数是 Subscription（内部对 epgUrl==null 返回 0），计划的 String 写法编译不过
                if (repo.epgCount() == 0) repo.currentSubscription()?.let { repo.refreshEpg(it) }
            } catch (_: Exception) {}
            try {
                val (cur, next) = repo.nowNext(chName, chTvgId)
                if ((cur != null || next != null) && !barHasError) {
                    nowBar.text = buildString {
                        append(chName)
                        cur?.let { append("　正在：${it.title}") }
                        next?.let { append("　接下：${it.title}") }
                    }
                    touchBar()
                }
                if (epgPanel.isOpen) epgPanel.reload()
            } catch (_: Exception) {}
        }
    }

    private fun updateInfo() {
        val vf = player.videoFormat
        val af = player.audioFormat
        findViewById<TextView>(R.id.infoVideo).text = "视频：" + when {
            vf == null -> "未知"
            else -> listOfNotNull(
                vf.width.takeIf { it > 0 }?.let { "${it}x${vf.height}" } ?: "分辨率未知",
                codecDisplayName(vf.codecs, vf.sampleMimeType),
                bitrateLabel(vf.bitrate?.toLong() ?: -1L, estBps),
            ).joinToString(" · ")
        }
        findViewById<TextView>(R.id.infoAudio).text = "音频：" + when {
            af == null -> "未知"
            else -> listOfNotNull(
                codecDisplayName(af.codecs, af.sampleMimeType),
                if (af.sampleRate > 0) "${af.sampleRate} Hz" else null,
                af.bitrate?.takeIf { it > 0 }?.let { "%.0f kbps".format(it / 1e3) },
            ).joinToString(" · ")
        }
        findViewById<TextView>(R.id.infoNet).text = "网络：" + rttLabel(lastRttMs)
    }
}
