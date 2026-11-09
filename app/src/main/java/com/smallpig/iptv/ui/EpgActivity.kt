package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EpgActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "节目单（待实现）"; setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f; setPadding(48, 48, 48, 48)
        })
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_TVG_ID = "tvg_id"
    }
}
