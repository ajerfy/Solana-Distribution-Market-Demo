package com.solanadistributionmarketdemo.data

import android.util.Log
import com.solanadistributionmarketdemo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class LivePayloadResponse(
    val payload: DemoPayload,
    val endpoint: String,
)

data class LiveSimulationResponse(
    val simulation: DemoSimulation,
    val endpoint: String,
)

object LiveMarketClient {
    private const val TAG = "LiveMarketClient"
    private const val DEVICE_LOOPBACK_URL = "http://127.0.0.1:8787"
    private const val EMULATOR_HOST_URL = "http://10.0.2.2:8787"

    private fun baseUrl(): String = BuildConfig.PARABOLA_LIVE_URL.trimEnd('/')
    private fun candidateBaseUrls(): List<String> {
        val configured = baseUrl()
        return if (configured == EMULATOR_HOST_URL) {
            listOf(DEVICE_LOOPBACK_URL, EMULATOR_HOST_URL)
        } else {
            listOf(configured, DEVICE_LOOPBACK_URL, EMULATOR_HOST_URL)
        }.distinct()
    }

    fun endpoint(): String = "${candidateBaseUrls().first()}/api/demo-payload"
    fun simulationEndpoint(command: String): String = "${candidateBaseUrls().first()}/api/simulation/$command"
    fun simulationStreamEndpoint(): String = "${candidateBaseUrls().first()}/api/simulation/stream"

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

    suspend fun postSimulationCommand(command: String): Result<LiveSimulationResponse> = withContext(Dispatchers.IO) {
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
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        Log.i(TAG, "Simulation command $command posted to $endpoint")
                        return@runCatching LiveSimulationResponse(
                            simulation = loadSimulationPayload(body),
                            endpoint = endpoint,
                        )
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

    fun streamSimulation(): Flow<LiveSimulationResponse> = flow {
        while (currentCoroutineContext().isActive) {
            var connected = false
            var lastError: Throwable? = null
            for (base in candidateBaseUrls()) {
                val endpoint = "$base/api/simulation/stream"
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2_500
                        readTimeout = 12_000
                        useCaches = false
                        setRequestProperty("Accept", "text/event-stream")
                    }
                    try {
                        if (conn.responseCode !in 200..299) {
                            throw IllegalStateException("simulation stream returned HTTP ${conn.responseCode}")
                        }
                        Log.i(TAG, "Simulation stream connected to $endpoint")
                        connected = true
                        val data = StringBuilder()
                        conn.inputStream.bufferedReader().use { reader ->
                            while (currentCoroutineContext().isActive) {
                                val line = reader.readLine() ?: break
                                when {
                                    line.startsWith("data:") -> {
                                        if (data.isNotEmpty()) data.append('\n')
                                        data.append(line.removePrefix("data:").trimStart())
                                    }

                                    line.isBlank() && data.isNotEmpty() -> {
                                        val body = data.toString()
                                        data.clear()
                                        emit(
                                            LiveSimulationResponse(
                                                simulation = loadSimulationPayload(body),
                                                endpoint = endpoint,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "Simulation stream unavailable at $endpoint: ${error.message}")
                    lastError = error
                }
                if (connected) break
            }
            if (!connected) {
                Log.w(TAG, "Simulation stream retrying: ${lastError?.message ?: "backend unavailable"}")
            }
            delay(1_200)
        }
    }.flowOn(Dispatchers.IO)
}
