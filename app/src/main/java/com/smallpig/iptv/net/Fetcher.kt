package com.smallpig.iptv.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

class Fetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchText(url: String): String = call(url).use { resp ->
        resp.body?.string() ?: throw FetchException("空响应体")
    }

    /** 调用方负责关闭返回流 */
    fun stream(url: String): InputStream = call(url).let { resp ->
        resp.body?.byteStream() ?: run { resp.close(); throw FetchException("空响应体") }
    }

    private fun call(url: String): okhttp3.Response {
        val req = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw FetchException("非法 URL：$url", e)
        }
        val resp = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw FetchException(e.message ?: "网络错误", e)
        }
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw FetchException("HTTP $code")
        }
        return resp
    }
}
