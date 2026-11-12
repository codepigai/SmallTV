package com.smallpig.iptv.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smallpig.iptv.R

/**
 * 设置页两栏（spec 2026-11-12-ui-visual §⑥ 参考 config.png）：左 logo，右「通用」卡片行。
 * 仅三行——显示方式 / 宫格路数 / 版本（反目标：不加第四行）。
 * 行=焦点+确认交互；名称文案在 XML 静态，代码只写值文本；返回首页后的生效由 HomeActivity.onResume 重读设置完成。
 */
class SettingsActivity : AppCompatActivity() {
    private val ui by lazy { UiSettings(this) }
    private lateinit var rowModeValue: TextView
    private lateinit var rowSpanValue: TextView
    private lateinit var rowVersionValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        rowModeValue = findViewById(R.id.rowModeValue)
        rowSpanValue = findViewById(R.id.rowSpanValue)
        rowVersionValue = findViewById(R.id.rowVersionValue)
        findViewById<View>(R.id.rowMode).setOnClickListener { ui.mode = nextMode(ui.mode); refresh() }
        findViewById<View>(R.id.rowSpan).setOnClickListener { ui.span = nextSpan(ui.span); refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rowModeValue.text = "${if (ui.mode == MODE_GRID) "画廊" else "列表"}（确认键切换）"
        rowSpanValue.text = "${ui.span}（确认键切换）"
        val info = packageManager.getPackageInfo(packageName, 0)
        rowVersionValue.text = "版本 ${info.versionName} (${info.versionCode})"
    }
}
