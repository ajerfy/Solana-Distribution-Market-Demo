package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.data.PositionStore
import com.solanadistributionmarketdemo.data.ThemeStore
import com.solanadistributionmarketdemo.data.loadDemoPayload
import com.solanadistributionmarketdemo.data.rememberAppState
import com.solanadistributionmarketdemo.ui.AppShell
import com.solanadistributionmarketdemo.ui.DemoColors
import com.solanadistributionmarketdemo.ui.DemoTheme

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
            val state = rememberAppState(payload, store, themeStore)

            DemoTheme(state.themeMode.value) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = DemoColors.Background,
                ) {
                    AppShell(state, walletSender)
                }
            }
        }
    }
}
