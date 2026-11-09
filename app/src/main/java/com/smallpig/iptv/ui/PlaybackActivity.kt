package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import kotlinx.coroutines.launch

class PlaybackActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
        const val EXTRA_TVG_ID = "tvg_id"   // 保持 Task 7 已提交值，勿改回 "tvgId"
        private const val LAST_CHANNEL_KEY = "lastChannel"
    }

    private val repo get() = (application as App).repo
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var infoPanel: View
    private lateinit var nowBar: TextView
    private lateinit var controls: View
    private var infoShown = false
    private var controlsShown = false

    private var chName: String = ""
    private var chUrl: String = ""
    private var chTvgId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControls(false) }

    private val poll = object : Runnable {
        override fun run() {
            if (infoShown) updateInfo()
            handler.postDelayed(this, 1000)
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
        controls = findViewById(R.id.controls)
        nowBar.text = chName

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                nowBar.text = "$chName　播放失败：${error.errorCodeName}（按返回退出或『下一台』切换）"
            }
        })
        player.setMediaItem(MediaItem.fromUri(chUrl))
        player.prepare()
        player.playWhenReady = true

        findViewById<TextView>(R.id.ctrlPrev).setOnClickListener { step(-1) }
        findViewById<TextView>(R.id.ctrlNext).setOnClickListener { step(1) }
        findViewById<TextView>(R.id.ctrlFav).setOnClickListener { toggleFav() }
        findViewById<TextView>(R.id.ctrlEpg).setOnClickListener {
            startActivity(Intent(this, EpgActivity::class.java).apply {
                putExtra(EpgActivity.EXTRA_NAME, chName); putExtra(EpgActivity.EXTRA_TVG_ID, chTvgId)
            })
        }

        loadEpg()
        lifecycleScope.launch {
            repo.saveState(LAST_CHANNEL_KEY, "$chName|$chUrl|${chTvgId ?: ""}")
        }
    }

    override fun onStart() { super.onStart(); player.playWhenReady = true; handler.post(poll) }
    override fun onStop() { super.onStop(); handler.removeCallbacks(poll); player.playWhenReady = false }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); player.release() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { setControls(!controlsShown); return true }
            KeyEvent.KEYCODE_MENU -> {
                infoShown = !infoShown; infoPanel.visibility = if (infoShown) View.VISIBLE else View.GONE
                if (infoShown) updateInfo(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsShown) { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsShown) { step(1); return true }
            KeyEvent.KEYCODE_BACK -> {
                if (controlsShown || infoShown) { setControls(false); infoShown = false; infoPanel.visibility = View.GONE; return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setControls(show: Boolean) {
        controlsShown = show
        controls.visibility = if (show) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideControls)
        if (show) {
            handler.postDelayed(hideControls, 5000)
            findViewById<View>(R.id.ctrlNext).requestFocus()
        } else playerView.requestFocus()
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
        nowBar.text = name
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare(); player.playWhenReady = true
        loadEpg()
    }

    private fun toggleFav() {
        lifecycleScope.launch {
            val nowFav = repo.toggleFavorite(chName, null, chUrl)
            findViewById<TextView>(R.id.ctrlFav).text = if (nowFav) "已收藏" else "收藏"
            Toast.makeText(this@PlaybackActivity, if (nowFav) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
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
                if (cur != null || next != null) {
                    nowBar.text = buildString {
                        append(chName)
                        cur?.let { append("　正在：${it.title}") }
                        next?.let { append("　接下：${it.title}") }
                    }
                }
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
                vf.sampleMimeType?.substringAfter('/')?.uppercase() ?: "编码未知",
                if (vf.bitrate > 0) "%.1f Mbps".format(vf.bitrate / 1e6) else "码率未知",
            ).joinToString(" · ")
        }
        findViewById<TextView>(R.id.infoAudio).text = "音频：" + when {
            af == null -> "未知"
            else -> listOfNotNull(
                af.sampleMimeType?.substringAfter('/')?.uppercase() ?: "未知",
                if (af.sampleRate > 0) "${af.sampleRate} Hz" else null,
                if (af.bitrate > 0) "%.0f kbps".format(af.bitrate / 1e3) else null,
            ).joinToString(" · ")
        }
    }
}
