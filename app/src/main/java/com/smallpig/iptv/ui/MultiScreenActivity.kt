package com.smallpig.iptv.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.smallpig.iptv.R

/**
 * 多屏观看（spec 2026-11-11）：2/4 格并发直播、逐格换台、单路声音、长按 BACK 退出。
 * 每次进入先弹布局选择层（§3.2）；网格与播放器状态机在后续任务填充。
 */
class MultiScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_screen)
        findViewById<View>(R.id.pickerOverlay).visibility = View.VISIBLE
        findViewById<View>(R.id.pickerTwo).requestFocus()
    }
}
