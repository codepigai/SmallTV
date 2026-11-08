package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlaybackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "播放（待实现）"; setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f; setPadding(48, 48, 48, 48)
        })
    }
}
