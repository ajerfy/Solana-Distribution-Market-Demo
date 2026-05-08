package com.solanadistributionmarketdemo.data

import android.util.Log
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
    private const val TAG = "LiveMarketClient"

    private fun baseUrl(): String = BuildConfig.PARABOLA_LIVE_URL.trimEnd('/')
    private fun candidateBaseUrls(): List<String> = listOf(
        "http://127.0.0.1:8787",
        baseUrl(),
        "http://10.0.2.2:8787",
    ).distinct()

    fun endpoint(): String = "${candidateBaseUrls().first()}/api/demo-payload"
    fun simulationEndpoint(command: String): String = "${candidateBaseUrls().first()}/api/simulation/$command"

    suspend fun fetchLatestPayload(): Result<LivePayloadResponse> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Throwable? = null
            for (base in candidateBaseUrls()) {
                val endpoint = "$base/api/demo-payload"
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                        Log.i(TAG, "Live payload loaded from $endpoint")
                        return@runCatching LivePayloadResponse(
                            payload = loadDemoPayload(body),
                            endpoint = endpoint,
                        )
                    } finally {
                        conn.disconnect()
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "Live payload unavailable at $endpoint: ${error.message}")
                    lastError = error
                }
            }
            throw lastError ?: IllegalStateException("live backend unavailable")
        }
    }

    suspend fun postSimulationCommand(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Throwable? = null
            for (base in candidateBaseUrls()) {
                val endpoint = "$base/api/simulation/$command"
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 2_500
                        readTimeout = 2_500
                        useCaches = false
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        if (conn.responseCode !in 200..299) {
                            throw IllegalStateException("simulation backend returned HTTP ${conn.responseCode}")
                        }
                        conn.inputStream.bufferedReader().use { it.readText() }
                        Log.i(TAG, "Simulation command $command posted to $endpoint")
                        return@runCatching Unit
                    } finally {
                        conn.disconnect()
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "Simulation command $command failed at $endpoint: ${error.message}")
                    lastError = error
                }
            }
            throw lastError ?: IllegalStateException("simulation backend unavailable")
        }
    }
}
