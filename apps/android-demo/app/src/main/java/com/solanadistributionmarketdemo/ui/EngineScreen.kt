package com.solanadistributionmarketdemo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanadistributionmarketdemo.core.shortHash
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.DemoSimulation
import com.solanadistributionmarketdemo.data.DemoSimulationTrade
import com.solanadistributionmarketdemo.data.LiveMarketClient
import com.solanadistributionmarketdemo.data.SubmitStatus
import kotlinx.coroutines.launch

@Composable
fun EngineScreen(state: AppState) {
    val market = state.payload.market
    val regimes = state.payload.regimeIndexes
    val perp = state.payload.perp
    val simulation = state.payload.simulation
    val feePercent = "%.2f%%".format(market.takerFeeBps / 100.0)
    val lastSubmit = state.lastSubmit.value
    val scope = rememberCoroutineScope()
    var pendingSimulationCommand by remember { mutableStateOf<String?>(null) }
    fun sendSimulationCommand(command: String) {
        if (pendingSimulationCommand != null) return
        scope.launch {
            pendingSimulationCommand = command
            LiveMarketClient.postSimulationCommand(command)
                .onSuccess { response ->
                    state.updateSimulation(response.simulation)
                    state.lastSubmit.value = SubmitStatus("Simulation command sent: $command")
                }
                .onFailure { error ->
                    state.lastSubmit.value = SubmitStatus(
                        "Simulation command failed: ${error.message ?: "backend unavailable"}",
                        isError = true,
                    )
                }
            pendingSimulationCommand = null
        }
    }

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
            if (simulation != null) {
                SimulationMovementCard(
                    simulation = simulation,
                    unit = market.unitLabel ?: "%",
                    pendingCommand = pendingSimulationCommand,
                    onCommand = ::sendSimulationCommand,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                Card(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Live movement demo",
                        color = DemoColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start the local backend to stream simulated crowd trades into this screen. The bundled asset still works, but it does not animate market movement.",
                        color = DemoColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

@Composable
private fun SimulationMovementCard(
    simulation: DemoSimulation,
    unit: String,
    pendingCommand: String?,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousMu = simulation.previousMuDisplay.toDoubleOrNull()
    val previousSigma = simulation.previousSigmaDisplay.toDoubleOrNull()
    val previousSkew = simulation.previousSkewDisplay.toDoubleOrNull() ?: 0.0
    val currentMu = simulation.currentMuDisplay.toDoubleOrNull()
    val currentSigma = simulation.currentSigmaDisplay.toDoubleOrNull()
    val currentSkew = simulation.currentSkewDisplay.toDoubleOrNull() ?: 0.0
    val chartFallbackMu = currentMu ?: previousMu ?: 50.0
    val chartFallbackSigma = currentSigma ?: previousSigma ?: 10.0
    val regimeLabel = when (simulation.regime) {
        "bullish" -> "bullish"
        "bearish" -> "bearish"
        "volatile" -> "volatile"
        "shock" -> "shock"
        else -> "drift"
    }
    val animatedPreviousMu = animateFloatAsState(
        targetValue = (previousMu ?: chartFallbackMu).toFloat(),
        animationSpec = tween(durationMillis = 450),
        label = "simulation previous mean",
    ).value.toDouble()
    val animatedPreviousSigma = animateFloatAsState(
        targetValue = (previousSigma ?: chartFallbackSigma).toFloat(),
        animationSpec = tween(durationMillis = 450),
        label = "simulation previous confidence",
    ).value.toDouble()
    val animatedCurrentMu = animateFloatAsState(
        targetValue = (currentMu ?: chartFallbackMu).toFloat(),
        animationSpec = tween(durationMillis = 650),
        label = "simulation current mean",
    ).value.toDouble()
    val animatedCurrentSigma = animateFloatAsState(
        targetValue = (currentSigma ?: chartFallbackSigma).toFloat(),
        animationSpec = tween(durationMillis = 650),
        label = "simulation current confidence",
    ).value.toDouble()
    val animatedPreviousSkew = animateFloatAsState(
        targetValue = previousSkew.toFloat(),
        animationSpec = tween(durationMillis = 450),
        label = "simulation previous skew",
    ).value.toDouble()
    val animatedCurrentSkew = animateFloatAsState(
        targetValue = currentSkew.toFloat(),
        animationSpec = tween(durationMillis = 650),
        label = "simulation current skew",
    ).value.toDouble()
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Live market movement",
                    color = DemoColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${simulation.scenario} · $regimeLabel · ${simulation.speed}x",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TagPill(if (simulation.running) "RUNNING" else "PAUSED", color = if (simulation.running) DemoColors.AccentLong else DemoColors.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                label = if (simulation.running) "Pause" else "Start",
                onClick = { onCommand(if (simulation.running) "pause" else "start") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
                accent = if (simulation.running) DemoColors.AccentWarn else DemoColors.AccentYou,
            )
            GhostButton(
                label = "Speed ${simulation.speed}x",
                onClick = { onCommand("speed") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GhostButton(
                label = "Reset",
                onClick = { onCommand("reset") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
            GhostButton(
                label = "News shock",
                onClick = { onCommand("shock") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RegimeModeButton(
                label = "Bullish",
                selected = simulation.regime == "bullish",
                accent = DemoColors.AccentLong,
                onClick = { onCommand("regime/bullish") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
            RegimeModeButton(
                label = "Bearish",
                selected = simulation.regime == "bearish",
                accent = DemoColors.AccentShort,
                onClick = { onCommand("regime/bearish") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
            RegimeModeButton(
                label = "Volatile",
                selected = simulation.regime == "volatile",
                accent = DemoColors.AccentWarn,
                onClick = { onCommand("regime/volatile") },
                modifier = Modifier.weight(1f),
                enabled = pendingCommand == null,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Pick a regime to show clustered higher beliefs, clustered lower beliefs, or aggressive two-sided flow. The asymmetric shading is a visual pressure layer; settlement still uses the Normal quote engine.",
            color = DemoColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (previousMu != null && previousSigma != null && currentMu != null && currentSigma != null) {
            Spacer(Modifier.height(12.dp))
            DistributionChart(
                crowdMu = animatedPreviousMu,
                crowdSigma = animatedPreviousSigma,
                yourMu = animatedCurrentMu,
                yourSigma = animatedCurrentSigma,
                crowdSkew = animatedPreviousSkew,
                yourSkew = animatedCurrentSkew,
                domainMin = 0.0,
                domainMax = 100.0,
                height = 150.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricPill("TRADES", simulation.tradeCount.toString(), DemoColors.AccentCrowd)
            MetricPill("FEES", simulation.feesEarnedDisplay.take(8), DemoColors.AccentLong)
            MetricPill("VOLUME", simulation.totalVolumeDisplay.take(8), DemoColors.AccentWarn)
        }
        if (simulation.tradeTape.isNotEmpty()) {
            CompactDivider(Modifier.padding(vertical = 14.dp))
            Text(
                "Recent crowd trades",
                color = DemoColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                simulation.tradeTape.take(4).forEach { trade ->
                    SimulationTradeRow(trade = trade, unit = unit)
                }
            }
        }
        simulation.lastError?.let { error ->
            CompactDivider(Modifier.padding(vertical = 14.dp))
            Text(
                error,
                color = DemoColors.AccentShort,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RegimeModeButton(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (selected) {
        PrimaryButton(label = label, onClick = onClick, modifier = modifier, enabled = enabled, accent = accent)
    } else {
        GhostButton(label = label, onClick = onClick, modifier = modifier, enabled = enabled)
    }
}

@Composable
private fun SimulationTradeRow(trade: DemoSimulationTrade, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DemoColors.SurfaceElevated, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${trade.handle} · ${trade.agentType}",
                color = DemoColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                trade.action,
                color = DemoColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "avg ${trade.targetMuDisplay.take(6)} $unit · confidence width ${trade.targetSigmaDisplay.take(6)}",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                trade.totalDebitDisplay.take(7),
                color = if (trade.accepted) DemoColors.AccentLong else DemoColors.AccentShort,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "fee ${trade.feeDisplay.take(6)}",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
