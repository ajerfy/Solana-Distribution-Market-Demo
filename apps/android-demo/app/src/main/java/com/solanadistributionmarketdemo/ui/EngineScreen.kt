package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanadistributionmarketdemo.core.shortHash
import com.solanadistributionmarketdemo.data.AppState

@Composable
fun EngineScreen(state: AppState) {
    val market = state.payload.market
    val regimes = state.payload.regimeIndexes
    val perp = state.payload.perp
    val feePercent = "%.2f%%".format(market.takerFeeBps / 100.0)
    val lastSubmit = state.lastSubmit.value

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(
                    "Engine",
                    color = DemoColors.TextPrimary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A plain-language view of how Parabola prices markets, where liquidity comes from, and how money moves after a trade.",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Live feeds now", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TagPill("DEVNET", color = DemoColors.AccentChain)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Parabola is currently anchoring one live Polymarket event and one live SOL perp. The event becomes the crowd estimate for the probability market; the perp stays pinned to a live oracle-backed anchor.",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                StatRow("Featured event", market.title, DemoColors.AccentCrowd, strong = true)
                StatRow("Event source", market.sourceBadge ?: "Live market feed")
                StatRow("Live event estimate", market.currentMuDisplay.take(10), DemoColors.AccentCrowd)
                StatRow("Confidence width", market.currentSigmaDisplay.take(10))
                market.bestBidDisplay?.let { StatRow("Best bid / ask", "${it.take(6)} / ${market.bestAskDisplay?.take(6) ?: "—"}") }
                if (perp != null) {
                    CompactDivider()
                    StatRow("Featured perp", perp.symbol, DemoColors.AccentWarn, strong = true)
                    StatRow("Perp source", "Pyth Hermes")
                    StatRow("Current mark", "$${perp.markPriceDisplay.take(8)}")
                    StatRow("Funding rate", perp.spotFundingRateDisplay.take(10), if ((perp.spotFundingRateDisplay.toDoubleOrNull() ?: 0.0) >= 0) DemoColors.AccentLong else DemoColors.AccentShort)
                }
                CompactDivider()
                StatRow("Trading fee", feePercent)
                CompactDivider()
                StatRow("Status", market.status.uppercase(), DemoColors.AccentLong, strong = true)
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Where liquidity comes from", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "A maker seeds the pool with starting cash. Each trade adds collateral and pays a fee into the same pool. Winning payouts come out later; fees stay behind for liquidity providers.",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                StatRow("Starting maker seed", market.makerDepositDisplay.take(10))
                StatRow("Total backing now", market.backingDisplay.take(10), strong = true)
                StatRow("Fees earned so far", market.makerFeesEarnedDisplay.take(10), DemoColors.AccentLong)
                StatRow("Open trades", "${market.totalTrades} / ${market.maxOpenTrades}")
                CompactDivider()
                Text(
                    "On estimate markets, the closer your curve is to the eventual outcome, the more of that pool you can claim when the market resolves.",
                    color = DemoColors.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (perp != null) {
            item {
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Perpetual markets", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Perps let traders stay long or short without waiting for one final resolution event. Funding nudges the perp price back toward the underlying estimate over time.",
                        color = DemoColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow("Current mark", "$${perp.markPriceDisplay.take(8)}", strong = true)
                    StatRow("Funding rate", perp.spotFundingRateDisplay.take(10), if ((perp.spotFundingRateDisplay.toDoubleOrNull() ?: 0.0) >= 0) DemoColors.AccentLong else DemoColors.AccentShort)
                    StatRow("Open perp positions", perp.openPositions.toString())
                }
            }
        }
        if (regimes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Regime indexes", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A regime index bundles several markets into one tradeable theme. As the underlying markets move, the basket level updates and the theme can be traded like one view.",
                        color = DemoColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow("Live theme baskets", regimes.size.toString(), strong = true)
                    regimes.take(3).forEach { regime ->
                        StatRow(
                            regime.symbol,
                            "${regime.levelDisplay.take(7)}  (${regime.changeDisplay.take(8)})",
                            if ((regime.changeDisplay.toDoubleOrNull() ?: 0.0) >= 0) DemoColors.AccentLong else DemoColors.AccentShort,
                        )
                    }
                    if (regimes.size > 3) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${regimes.size - 3} more baskets are available from the Markets tab.",
                            color = DemoColors.TextDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Wallet + last action", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (lastSubmit == null) {
                    Text("No trade or demo transaction has been submitted in this session yet.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(lastSubmit.message, color = if (lastSubmit.isError) DemoColors.AccentShort else DemoColors.AccentLong, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (state.walletAddress.value != null) {
                    StatRow("Connected wallet", shortHash(state.walletAddress.value!!, 6, 6), DemoColors.AccentChain)
                } else {
                    Text(
                        "Connect a wallet by signing from a market ticket. Until then, the app stays in local demo mode.",
                        color = DemoColors.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
