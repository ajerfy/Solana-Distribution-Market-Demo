package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanadistributionmarketdemo.core.compactDecimal
import com.solanadistributionmarketdemo.core.shortHash
import com.solanadistributionmarketdemo.data.AppState

@Composable
fun EngineScreen(state: AppState) {
    val market = state.payload.market
    val regimes = state.payload.regimeIndexes
    val perp = state.payload.perp

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
                    "What the program actually sees — pool state, quote envelope parameters, and per-market specifics. The numbers below come straight from the seeded devnet payload.",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Maker pool", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TagPill("DEVNET", color = DemoColors.AccentChain)
                }
                Spacer(Modifier.height(10.dp))
                StatRow("Maker deposit", market.makerDepositDisplay.take(10))
                StatRow("Backing", market.backingDisplay.take(10))
                StatRow("Maker fees earned", market.makerFeesEarnedDisplay.take(10))
                StatRow("Open trades", "${market.totalTrades} / ${market.maxOpenTrades}")
                CompactDivider()
                StatRow("Market id", shortHash(market.marketIdHex, 8, 6), DemoColors.AccentChain)
                StatRow("State version", market.stateVersion.toString())
                StatRow("Status", market.status.uppercase(), DemoColors.AccentLong, strong = true)
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Quote envelope", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                StatRow("Current μ", market.currentMuDisplay.take(10), DemoColors.AccentCrowd)
                StatRow("Current σ", market.currentSigmaDisplay.take(10))
                StatRow("K value", market.kDisplay.take(10))
                CompactDivider()
                StatRow("Taker fee", "${market.takerFeeBps} bps")
                StatRow("Min taker fee", market.minTakerFeeDisplay.take(10))
                StatRow("Coarse samples", market.coarseSamples.toString())
                StatRow("Refine samples", market.refineSamples.toString())
                CompactDivider()
                StatRow("Expiry slot", market.expirySlot.toString())
                StatRow("Quote slot", market.demoQuoteSlot.toString())
                StatRow("Quote expires", market.demoQuoteExpirySlot.toString())
            }
        }
        if (regimes.isNotEmpty()) {
            item { SectionLabel("THEME BASKETS · ${regimes.size}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            items(regimes, key = { it.id }) { r ->
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.symbol, color = DemoColors.AccentLong, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("level ${r.levelDisplay.take(7)}", color = DemoColors.TextDim, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(r.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    StatRow("Constituents", r.constituents.size.toString())
                    StatRow("Rebalance every", "${r.nextRebalanceSlot - r.rebalanceSlot} slots")
                    StatRow("Change", r.changeDisplay.take(10), if ((r.changeDisplay.toDoubleOrNull() ?: 0.0) >= 0) DemoColors.AccentLong else DemoColors.AccentShort)
                }
            }
        }
        if (perp != null) {
            item { SectionLabel("PERP · ${perp.symbol}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            item {
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    StatRow("Mark", "$${perp.markPriceDisplay.take(8)}", DemoColors.TextPrimary, strong = true)
                    StatRow("Anchor μ", perp.anchorMuDisplay.take(8), DemoColors.AccentCrowd)
                    StatRow("AMM μ", perp.ammMuDisplay.take(8), DemoColors.AccentYou)
                    StatRow("AMM σ", perp.ammSigmaDisplay.take(8))
                    StatRow("KL pressure", perp.klDisplay.take(8))
                    CompactDivider()
                    StatRow("Funding rate", perp.spotFundingRateDisplay.take(10), if ((perp.spotFundingRateDisplay.toDoubleOrNull() ?: 0.0) >= 0) DemoColors.AccentLong else DemoColors.AccentShort)
                    StatRow("Funding interval", "${perp.fundingInterval} slots")
                    StatRow("Next funding", "slot ${perp.nextFundingSlot}")
                    CompactDivider()
                    StatRow("Vault cash", perp.vaultCashDisplay.take(10))
                    StatRow("LP NAV", perp.lpNavDisplay.take(10))
                    StatRow("Open positions", perp.openPositions.toString())
                }
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("RECEIPT · last submission")
                Spacer(Modifier.height(6.dp))
                val last = state.lastSubmit.value
                if (last == null) {
                    Text("No memos signed yet this session.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(last.message, color = if (last.isError) DemoColors.AccentShort else DemoColors.AccentLong, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.walletAddress.value != null) {
                    Spacer(Modifier.height(8.dp))
                    StatRow("Connected wallet", shortHash(state.walletAddress.value!!, 6, 6), DemoColors.AccentChain)
                }
            }
        }
    }
}
