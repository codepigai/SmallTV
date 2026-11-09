package com.smallpig.iptv.net

import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 直播流加载重试策略（真机反馈 #6）。
 * 实测 iPTV 类中继的直播「头部分片」存在最长约 20s 的 403 未就绪窗口，
 * Media3 默认仅重试 3 次、首次退避 8s，扛不过窗口即 fatal BAD_HTTP_STATUS 断流。
 * 这里改为最多 12 次、退避 1s→2s→4s→8s 封顶，多数瞬断可在无感内恢复。
 */
class IptvLoadPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 12

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        minOf(1000L * (1L shl loadErrorInfo.errorCount.coerceAtMost(3)), 8000L)
}

/** 服务端首字节延迟探测（真机反馈 #4/#8）。GET 到响应头即关闭，不拉取大响应体。 */
object RttProbe {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 返回毫秒；网络失败返回 -1。HTTP 非 2xx 也计时（衡量的是网络延迟，非业务成败）。 */
    suspend fun probe(url: String): Long = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().close()
            (System.nanoTime() - t0) / 1_000_000
        } catch (_: Exception) {
            -1L
        }
    }
}
