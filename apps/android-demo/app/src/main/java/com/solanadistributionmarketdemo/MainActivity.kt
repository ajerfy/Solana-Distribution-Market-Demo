package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.data.DemoPayload
import com.solanadistributionmarketdemo.data.LiveMarketClient
import com.solanadistributionmarketdemo.data.LiveSyncMode
import com.solanadistributionmarketdemo.data.LiveSyncStatus
import com.solanadistributionmarketdemo.data.PositionStore
import com.solanadistributionmarketdemo.data.ThemeStore
import com.solanadistributionmarketdemo.data.loadDemoPayload
import com.solanadistributionmarketdemo.data.rememberAppState
import com.solanadistributionmarketdemo.ui.AppShell
import com.solanadistributionmarketdemo.ui.DemoColors
import com.solanadistributionmarketdemo.ui.DemoTheme
import com.solanadistributionmarketdemo.ui.OnboardingStore
import com.solanadistributionmarketdemo.ui.ParabolaEntranceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private lateinit var walletSender: ActivityResultSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletSender = ActivityResultSender(this)
        setContent {
            val context = LocalContext.current
            val store = remember { PositionStore(context) }
            val themeStore = remember { ThemeStore(context) }
            val onboardingStore = remember { OnboardingStore(context) }
            val initialTheme = remember { themeStore.load() }
            var payload by remember { mutableStateOf<DemoPayload?>(null) }
            var entered by rememberSaveable { mutableStateOf(false) }
            var enterRequested by rememberSaveable { mutableStateOf(false) }
            var tutorialReplayToken by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                payload = withContext(Dispatchers.IO) {
                    loadDemoPayload(
                        context.assets.open("demo_market.json").bufferedReader().use { it.readText() }
                    )
                }
            }

            val loadedPayload = payload
            if (loadedPayload == null) {
                DemoTheme(initialTheme) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                        color = DemoColors.Background,
                    ) {
                        ParabolaEntranceScreen(onEnter = { enterRequested = true })
                    }
                }
            } else {
                val state = rememberAppState(loadedPayload, store, themeStore)
                state.replayOnboarding = {
                    onboardingStore.reset()
                    tutorialReplayToken += 1
                }

                LaunchedEffect(loadedPayload) {
                    if (enterRequested) {
                        entered = true
                    }
                }

                LaunchedEffect(state) {
                    state.updateLiveSync(
                        LiveSyncStatus(
                            mode = LiveSyncMode.Connecting,
                            source = "Parabola live backend",
                            endpoint = LiveMarketClient.endpoint(),
                            lastUpdatedMillis = null,
                            message = "Looking for a live oracle backend.",
                        )
                    )
                    while (true) {
                        val now = System.currentTimeMillis()
                        LiveMarketClient.fetchLatestPayload()
                            .onSuccess { response ->
                                state.updatePayload(response.payload)
                                val liveFeed = response.payload.liveFeed
                                val mode = when (liveFeed?.mode?.lowercase()) {
                                    "live" -> LiveSyncMode.Live
                                    "connecting" -> LiveSyncMode.Connecting
                                    "degraded", "error" -> LiveSyncMode.Error
                                    else -> LiveSyncMode.Demo
                                }
                                state.updateLiveSync(
                                    LiveSyncStatus(
                                        mode = mode,
                                        source = liveFeed?.source ?: "Parabola live backend",
                                        endpoint = response.endpoint,
                                        lastUpdatedMillis = liveFeed?.lastUpdateUnixMs?.takeIf { it > 0 } ?: now,
                                        message = liveFeed?.message ?: "Oracle data is live.",
                                    )
                                )
                            }
                            .onFailure { error ->
                                if (state.liveSyncStatus.value.mode != LiveSyncMode.Live) {
                                    state.updateLiveSync(
                                        LiveSyncStatus(
                                            mode = LiveSyncMode.Demo,
                                            source = "Bundled demo asset",
                                            endpoint = LiveMarketClient.endpoint(),
                                            lastUpdatedMillis = null,
                                            message = "Live backend unavailable: ${error.message ?: "unknown error"}",
                                        )
                                    )
                                }
                            }
                        delay(3_000)
                    }
                }

                LaunchedEffect(state) {
                    LiveMarketClient.streamSimulation().collect { response ->
                        state.updateSimulation(response.simulation)
                        val currentStatus = state.liveSyncStatus.value
                        if (currentStatus.mode != LiveSyncMode.Live) {
                            state.updateLiveSync(
                                LiveSyncStatus(
                                    mode = LiveSyncMode.Live,
                                    source = currentStatus.source,
                                    endpoint = response.endpoint,
                                    lastUpdatedMillis = System.currentTimeMillis(),
                                    message = "Simulation stream is live.",
                                )
                            )
                        }
                    }
                }

                DemoTheme(state.themeMode.value) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                        color = DemoColors.Background,
                    ) {
                        Crossfade(
                            targetState = entered,
                            animationSpec = tween(durationMillis = 700),
                            label = "Parabola entrance",
                        ) { hasEntered ->
                            if (hasEntered) {
                                AppShell(
                                    state = state,
                                    walletSender = walletSender,
                                    onboardingStore = onboardingStore,
                                    tutorialReplayToken = tutorialReplayToken,
                                )
                            } else {
                                ParabolaEntranceScreen(onEnter = { entered = true })
                            }
                        }
                    }
                }
            }
        }
    }
}
