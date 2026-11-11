package com.smallpig.iptv.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * 首页显示方式设置（spec 2026-11-11-ui-batch §⑤）。
 * 纯函数在前（零 Android 依赖，JVM 可测）；SharedPreferences 封装在后。
 * 类内 get 一律经 norm 函数，脏值不逃逸到调用方。
 */

const val MODE_LIST = "list"
const val MODE_GRID = "grid"

/** 仅 "grid" 归 grid；其余（含 null/空/大小写不符）一律归 list */
fun normMode(v: String?): String = if (v == MODE_GRID) MODE_GRID else MODE_LIST

/** 列表⇄画廊两值互切；脏值视为 list 切向 grid */
fun nextMode(cur: String): String = if (normMode(cur) == MODE_GRID) MODE_LIST else MODE_GRID

/** 2..5 之外回退默认 3 */
fun normSpan(v: Int): Int = if (v in 2..5) v else 3

/** 2→3→4→5→2 循环；脏值直接回退默认 3（不进循环） */
fun nextSpan(cur: Int): Int = when (cur) {
    2 -> 3
    3 -> 4
    4 -> 5
    5 -> 2
    else -> 3
}

class UiSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    var mode: String
        get() = normMode(prefs.getString("display_mode", MODE_LIST))
        set(value) = prefs.edit().putString("display_mode", value).apply()

    var span: Int
        get() = normSpan(prefs.getInt("grid_span", 3))
        set(value) = prefs.edit().putInt("grid_span", value).apply()
}
