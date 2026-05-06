package com.solanadistributionmarketdemo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.NavTab

@Composable
fun AppShell(state: AppState, walletSender: ActivityResultSender) {
    Box(modifier = Modifier.fillMaxSize().background(DemoColors.Background)) {
        val selectedMarket = state.marketById(state.selectedMarketId.value)
        if (selectedMarket != null) {
            BackHandler(enabled = true) { state.closeMarket() }
            MarketDetailScreen(state, selectedMarket)
        } else {
            when (state.activeTab.value) {
                NavTab.Markets -> MarketsListScreen(state)
                NavTab.Portfolio -> PortfolioScreen(state)
                NavTab.Engine -> EngineScreen(state)
                NavTab.Wallet -> WalletScreen(state)
            }
            BottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                active = state.activeTab.value,
                onSelect = { state.activeTab.value = it },
            )
        }

        if (state.showBetSheet.value && selectedMarket != null) {
            when (selectedMarket.marketType) {
                com.solanadistributionmarketdemo.data.MarketType.Estimation ->
                    BetSheet(
                        state = state,
                        market = selectedMarket,
                        walletSender = walletSender,
                        onDismiss = { state.showBetSheet.value = false },
                    )
                com.solanadistributionmarketdemo.data.MarketType.RegimeIndex -> {
                    val regime = selectedMarket.regime
                    val side = BetSheetPrefill.consumeRegimeSide() ?: RegimeBetSide.Long
                    if (regime != null) {
                        RegimeBetSheet(
                            state = state,
                            market = selectedMarket,
                            regime = regime,
                            initialSide = side,
                            walletSender = walletSender,
                            onDismiss = { state.showBetSheet.value = false },
                        )
                    }
                }
                com.solanadistributionmarketdemo.data.MarketType.Perp -> {
                    val perp = selectedMarket.perp
                    val side = BetSheetPrefill.consumePerpSide() ?: PerpBetSide.Long
                    if (perp != null) {
                        PerpBetSheet(
                            state = state,
                            market = selectedMarket,
                            perp = perp,
                            initialSide = side,
                            walletSender = walletSender,
                            onDismiss = { state.showBetSheet.value = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(modifier: Modifier = Modifier, active: NavTab, onSelect: (NavTab) -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DemoColors.SurfaceElevated)
                .border(1.dp, DemoColors.Border, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                NavItem(tab = tab, selected = tab == active, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) DemoColors.AccentYou else Color.Transparent
    val fg = if (selected) DemoColors.OnAccent else DemoColors.TextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            glyphFor(tab),
            color = fg,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall,
        )
        if (selected) {
            Text(
                tab.label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun glyphFor(tab: NavTab): String = when (tab) {
    NavTab.Markets -> "◆"
    NavTab.Portfolio -> "▲"
    NavTab.Engine -> "▦"
    NavTab.Wallet -> "◉"
}
