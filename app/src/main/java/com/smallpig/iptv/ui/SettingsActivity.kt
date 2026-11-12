package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smallpig.iptv.R

/**
 * 设置页（spec 2026-11-11-ui-batch §⑤）：仅三行——显示方式 / 画廊路数 / 版本（反目标：不加第四行）。
 * 行=焦点+确认交互；返回首页后的生效由 HomeActivity.onResume 重读设置完成（Task 4）。
 */
class SettingsActivity : AppCompatActivity() {
    private val ui by lazy { UiSettings(this) }
    private lateinit var rowMode: TextView
    private lateinit var rowSpan: TextView
    private lateinit var rowVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        rowMode = findViewById(R.id.rowMode)
        rowSpan = findViewById(R.id.rowSpan)
        rowVersion = findViewById(R.id.rowVersion)
        rowMode.setOnClickListener { ui.mode = nextMode(ui.mode); refresh() }
        rowSpan.setOnClickListener { ui.span = nextSpan(ui.span); refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rowMode.text = "频道显示方式：${if (ui.mode == MODE_GRID) "画廊" else "列表"}（确认键切换）"
        rowSpan.text = "画廊一行几路：${ui.span}（确认键切换）"
        val info = packageManager.getPackageInfo(packageName, 0)
        rowVersion.text = "版本 ${info.versionName} (${info.versionCode})"
    }
}
