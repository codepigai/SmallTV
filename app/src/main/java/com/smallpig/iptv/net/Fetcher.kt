package com.smallpig.iptv.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

class Fetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchText(url: String): String = call(url).use { resp ->
        try {
            resp.body?.string() ?: throw FetchException("空响应体")
        } catch (e: IOException) {
            throw ioFail(e)
        }
    }

    /** 调用方负责关闭返回流；读取阶段的 IOException 不会自动转 FetchException，优先用 withStream */
    fun stream(url: String): InputStream = call(url).let { resp ->
        resp.body?.byteStream() ?: run { resp.close(); throw FetchException("空响应体") }
    }

    /** 限域读取并保证关闭；读取阶段 IOException 统一转 FetchException */
    fun <R> withStream(url: String, block: (InputStream) -> R): R =
        try {
            stream(url).use(block)
        } catch (e: FetchException) {
            throw e
        } catch (e: IOException) {
            throw ioFail(e)
        }

    private fun ioFail(e: IOException): FetchException = FetchException(describe(e), e)

    private fun describe(e: IOException): String = when (e) {
        is SocketTimeoutException -> "连接超时"
        is UnknownHostException -> "DNS 解析失败"
        is ConnectException -> "连接失败"
        else -> e.message ?: "网络错误"
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
            throw ioFail(e)
        }
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw FetchException("服务器错误 $code")
        }
        return resp
    }
}
