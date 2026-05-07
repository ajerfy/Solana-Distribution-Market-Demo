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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.data.PositionStore
import com.solanadistributionmarketdemo.data.ThemeStore
import com.solanadistributionmarketdemo.data.loadDemoPayload
import com.solanadistributionmarketdemo.data.rememberAppState
import com.solanadistributionmarketdemo.ui.AppShell
import com.solanadistributionmarketdemo.ui.DemoColors
import com.solanadistributionmarketdemo.ui.DemoTheme
import com.solanadistributionmarketdemo.ui.OnboardingStore
import com.solanadistributionmarketdemo.ui.ParabolaEntranceScreen

class MainActivity : ComponentActivity() {
    private lateinit var walletSender: ActivityResultSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletSender = ActivityResultSender(this)
        setContent {
            val context = LocalContext.current
            val payload = remember {
                loadDemoPayload(
                    context.assets.open("demo_market.json").bufferedReader().use { it.readText() }
                )
            }
            val store = remember { PositionStore(context) }
            val themeStore = remember { ThemeStore(context) }
            val onboardingStore = remember { OnboardingStore(context) }
            val state = rememberAppState(payload, store, themeStore)
            var entered by rememberSaveable { mutableStateOf(false) }
            var tutorialReplayToken by remember { mutableStateOf(0) }
            state.replayOnboarding = {
                onboardingStore.reset()
                tutorialReplayToken += 1
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
