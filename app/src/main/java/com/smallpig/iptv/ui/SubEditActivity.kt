package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubEditActivity : AppCompatActivity() {
    companion object { const val EXTRA_SUB_ID = "subId" }

    private val repo get() = (application as App).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_edit)
        val etName: EditText = findViewById(R.id.etName)
        val etM3u: EditText = findViewById(R.id.etM3u)
        val etEpg: EditText = findViewById(R.id.etEpg)
        val msg: TextView = findViewById(R.id.editMsg)
        val subId = intent.getLongExtra(EXTRA_SUB_ID, -1)
        var existing: com.smallpig.iptv.data.Subscription? = null

        findViewById<TextView>(R.id.editTitle).text = if (subId >= 0) "编辑订阅" else "添加订阅"
        lifecycleScope.launch {
            if (subId >= 0) {
                existing = repo.subscriptionById(subId)
                existing?.let {
                    etName.setText(it.name); etM3u.setText(it.m3uUrl); etEpg.setText(it.epgUrl ?: "")
                }
            }
        }

        findViewById<TextView>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val m3u = etM3u.text.toString().trim()
            val epg = etEpg.text.toString().trim()
            if (name.isEmpty() || m3u.isEmpty()) { msg.text = "名称与 m3u 链接必填"; return@setOnClickListener }
            if (!(m3u.startsWith("http://") || m3u.startsWith("https://"))) { msg.text = "m3u 链接需以 http 开头"; return@setOnClickListener }
            msg.text = "保存中，正在下载 m3u 测试…"
            lifecycleScope.launch {
                try {
                    val id = withContext(Dispatchers.IO) {
                        val cur = existing
                        val newId = if (cur == null) repo.addSubscription(name, m3u, epg)
                        else { repo.updateSubscription(cur.copy(name = name, m3uUrl = m3u, epgUrl = epg.ifBlank { null })); cur.id }
                        newId to repo.refreshChannels(newId)
                    }
                    var epgNote = "（无 EPG）"
                    if (epg.isNotBlank()) {
                        epgNote = try { "，EPG ${repo.refreshEpg(repo.subscriptionById(id.first)!!)} 条节目" }
                        catch (e: Exception) { "，EPG 解析失败：${e.message}" }
                    }
                    msg.text = "成功：解析到 ${id.second} 个频道$epgNote"
                    withContext(Dispatchers.IO) { Thread.sleep(700) }
                    finish()
                } catch (e: Exception) {
                    msg.text = "失败：${e.message}（订阅已保存，可稍后在『刷新频道/EPG』重试）"
                }
            }
        }
    }
}
