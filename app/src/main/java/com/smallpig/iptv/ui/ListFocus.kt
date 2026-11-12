package com.smallpig.iptv.ui

import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * 把焦点落到列表目标行（真机反馈②）。
 *
 * 原先 notify 后直接 `post { getChildAt(0)?.requestFocus() }` 不可靠：post 是 handler 消息，
 * 先于下一帧 traversal 执行，而 notifyDataSetChanged 的新 child 要到那次 traversal 才创建——
 * 首次打开时行还不存在，requestFocus 静默失败，焦点留在播放器上，UP/DOWN 又被面板拦截变死键。
 *
 * 这里先立即尝试（child 已在，如二次打开同数据），落空则等一次 onGlobalLayout
 * （measure/layout 之后必然派发）再试；命中即摘监听。空列表静默放弃——
 * 空面板的 UP/DOWN 由 PlaybackActivity 键位的 hasFocusInside 判断兜底放行换台。
 */
fun focusRowAfterLayout(list: RecyclerView, target: Int) {
    fun tryFocus(): Boolean {
        val v = list.layoutManager?.findViewByPosition(target) ?: list.getChildAt(0) ?: return false
        v.requestFocus()
        return true
    }
    if (tryFocus()) return
    list.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val vto = list.viewTreeObserver
            if (vto.isAlive) vto.removeOnGlobalLayoutListener(this)
            tryFocus()
        }
    })
}
