package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanadistributionmarketdemo.core.compactDecimal
import com.solanadistributionmarketdemo.core.shortHash
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.BetRecord
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.MockActivity

@Composable
fun PortfolioScreen(state: AppState) {
    val totalStaked = state.bets.sumOf { it.stake }
    val realizedPnl = state.bets.filter { it.resolved }.sumOf { it.realizedPnl ?: 0.0 }
    val open = state.bets.filter { !it.resolved }
    val resolved = state.bets.filter { it.resolved }

    if (state.bets.isEmpty()) {
        EmptyState(
            title = "No bets yet",
            subtitle = "Pick a market, set your guess, place a bet.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("Portfolio", color = DemoColors.TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("${state.bets.size} positions across ${state.bets.map { it.marketId }.toSet().size} markets", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(label = "STAKED", value = "$${totalStaked.compactDecimal(0)}")
                MetricPill(label = "RESOLVED P/L", value = "${if (realizedPnl >= 0) "+" else ""}$${realizedPnl.compactDecimal(2)}",
                    accent = if (realizedPnl >= 0) DemoColors.AccentLong else DemoColors.AccentShort)
                MetricPill(label = "OPEN", value = open.size.toString())
                MetricPill(label = "DONE", value = resolved.size.toString())
            }
        }
        if (open.isNotEmpty()) {
            item { SectionLabel("OPEN POSITIONS", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            items(open, key = { it.id }) { bet ->
                BetCard(state, bet, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        if (resolved.isNotEmpty()) {
            item { SectionLabel("RESOLVED", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            items(resolved, key = { it.id }) { bet ->
                BetCard(state, bet, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        item {
            GhostButton(
                "Clear all bets",
                onClick = { state.clearAll() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BetCard(state: AppState, bet: BetRecord, modifier: Modifier = Modifier) {
    val market: MarketListing? = state.marketById(bet.marketId)
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bet.isOnChain) TagPill("ON-CHAIN", color = DemoColors.AccentChain, filled = true)
                    if (bet.resolved) TagPill("DONE", color = DemoColors.TextDim) else TagPill("OPEN", color = DemoColors.AccentLong)
                }
                Text(
                    bet.marketTitle,
                    color = DemoColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
            if (bet.resolved && bet.realizedPnl != null) {
                PnlText(bet.realizedPnl, prefix = "$")
            } else {
                Text("$${bet.stake.compactDecimal(0)}", color = DemoColors.AccentYou, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (market != null) {
            DistributionChart(
                crowdMu = market.crowdMu,
                crowdSigma = market.crowdSigma,
                yourMu = bet.mu,
                yourSigma = bet.sigma,
                realizedOutcome = bet.realizedOutcome,
                height = 130.dp,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("YOUR GUESS", "${bet.mu.compactDecimal(2)}${market?.unit?.let { " $it" } ?: ""}", accent = DemoColors.AccentYou)
            MetricPill("± RANGE", bet.sigma.compactDecimal(2))
            MetricPill("STAKE", "$${bet.stake.compactDecimal(0)}")
            if (bet.resolved && bet.realizedOutcome != null) {
                MetricPill("REALIZED", bet.realizedOutcome.compactDecimal(2), accent = DemoColors.AccentWarn)
            }
        }
        if (!bet.resolved && market != null) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = "Resolve  ·  draw outcome",
                onClick = {
                    val (realized, pnl) = simulateRealizedAndPnl(market, bet, System.currentTimeMillis())
                    state.resolveBet(bet, realized, pnl)
                },
                accent = DemoColors.AccentWarn,
            )
        }
        if (bet.txSignatureHex != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "tx ${shortHash(bet.txSignatureHex, 8, 8)}",
                color = DemoColors.AccentChain,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun ActivityScreen(state: AppState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("Live flow", color = DemoColors.TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Recent bets across all markets.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val combined = state.markets.flatMap { m -> MockActivity.feedFor(m, count = 6).map { e -> m to e } }
            .sortedBy { it.second.ageMinutes }
        items(combined.size) { i ->
            val (market, event) = combined[i]
            Card(modifier = Modifier.padding(horizontal = 20.dp), padding = PaddingValues(14.dp), onClick = { state.openMarket(market.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(DemoColors.AccentLong))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.anonHandle, color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(market.title, color = DemoColors.TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text(
                            "μ=${event.mu.compactDecimal(2)} ${market.unit}  ·  σ=${event.sigma.compactDecimal(2)}  ·  $${event.stake.compactDecimal(0)}",
                            color = DemoColors.TextDim,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text("${event.ageMinutes}m", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun WalletScreen(state: AppState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("Wallet", color = DemoColors.TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Connection status & devnet identity.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (state.walletAddress.value != null) DemoColors.AccentLong else DemoColors.TextDim)
                    )
                    Text(
                        if (state.walletAddress.value != null) "Connected" else "Not connected",
                        color = DemoColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    state.walletAddress.value?.let { shortHash(it, 6, 6) } ?: "Sign a bet on the on-chain market to connect.",
                    color = DemoColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("NETWORK")
                Spacer(Modifier.height(4.dp))
                Text("Solana devnet", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("api.devnet.solana.com", color = DemoColors.TextDim, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                SectionLabel("SEEDED MARKET")
                Spacer(Modifier.height(4.dp))
                Text(state.payload.market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "id ${shortHash(state.payload.market.marketIdHex, 6, 6)} · v${state.payload.market.stateVersion}",
                    color = DemoColors.TextDim,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("APPEARANCE")
                Spacer(Modifier.height(8.dp))
                ThemeSwitch(state)
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("HELP")
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    label = "Replay tutorials",
                    onClick = { state.replayOnboarding() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.lastSubmit.value?.let { status ->
            item {
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionLabel("LAST SUBMISSION")
                    Spacer(Modifier.height(4.dp))
                    Text(status.message, color = if (status.isError) DemoColors.AccentShort else DemoColors.AccentLong, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ThemeSwitch(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = state.themeMode.value == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) DemoColors.AccentYou else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { state.setTheme(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${if (mode == ThemeMode.Light) "☀" else "☾"}  ${mode.label}",
                    color = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
