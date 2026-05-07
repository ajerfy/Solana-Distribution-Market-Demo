package com.solanadistributionmarketdemo.data

import com.solanadistributionmarketdemo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class LivePayloadResponse(
    val payload: DemoPayload,
    val endpoint: String,
)

object LiveMarketClient {
    private fun baseUrl(): String = BuildConfig.PARABOLA_LIVE_URL.trimEnd('/')

    fun endpoint(): String = "${baseUrl()}/api/demo-payload"

    suspend fun fetchLatestPayload(): Result<LivePayloadResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(endpoint()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("live backend returned HTTP ${conn.responseCode}")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                LivePayloadResponse(
                    payload = loadDemoPayload(body),
                    endpoint = endpoint(),
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
