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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.solanadistributionmarketdemo.core.compactDecimal
import com.solanadistributionmarketdemo.data.ActivityEvent
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.MockActivity

private enum class DetailTab(val label: String) { Bet("Bet"), Stats("Stats"), Flow("Flow"), Rules("Rules") }

@Composable
fun MarketDetailScreen(state: AppState, market: MarketListing) {
    when (market.marketType) {
        com.solanadistributionmarketdemo.data.MarketType.Estimation -> EstimationDetailScreen(state, market)
        com.solanadistributionmarketdemo.data.MarketType.RegimeIndex -> {
            val regime = market.regime
            if (regime != null) {
                Box(modifier = Modifier.fillMaxSize().background(DemoColors.Background)) {
                    RegimeIndexDetailBody(state, market, regime, onBack = { state.closeMarket() })
                    RegimeBottomCta(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onPickSide = { side ->
                            BetSheetPrefill.regimeSide = side
                            state.showBetSheet.value = true
                        },
                    )
                }
            }
        }
        com.solanadistributionmarketdemo.data.MarketType.Perp -> {
            val perp = market.perp
            if (perp != null) {
                Box(modifier = Modifier.fillMaxSize().background(DemoColors.Background)) {
                    PerpDetailBody(state, market, perp, onBack = { state.closeMarket() })
                    PerpBottomCta(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onPickSide = { side ->
                            BetSheetPrefill.perpSide = side
                            state.showBetSheet.value = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EstimationDetailScreen(state: AppState, market: MarketListing) {
    var activeTab by remember(market.id) { mutableStateOf(DetailTab.Bet) }
    val activity = remember(market.id) { MockActivity.feedFor(market) }
    val yourBets = state.bets.filter { it.marketId == market.id }
    val yourLatest = yourBets.firstOrNull()

    var previewMu by remember(market.id) { mutableStateOf(market.crowdMu.toFloat()) }
    var previewSigma by remember(market.id) { mutableStateOf(market.crowdSigma.toFloat()) }
    var showAdvanced by remember(market.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DemoColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BackBar(onBack = { state.closeMarket() }, market = market)
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (market.isOnChain) TagPill("ON-CHAIN", color = DemoColors.AccentChain, filled = true)
                        TagPill(market.category.label.uppercase(), color = DemoColors.AccentCrowd)
                        Text(
                            "· resolves ${market.resolvesAt}",
                            color = DemoColors.TextDim,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(market.subtitle, color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DistributionChart(
                        crowdMu = market.crowdMu,
                        crowdSigma = market.crowdSigma,
                        yourMu = previewMu.toDouble(),
                        yourSigma = previewSigma.toDouble(),
                        realizedOutcome = yourLatest?.realizedOutcome,
                    )
                    LegendRow(market.unit)
                }
            }
            item {
                DistributionSliderCard(
                    market = market,
                    mu = previewMu,
                    sigma = previewSigma,
                    onMu = { previewMu = it },
                    onSigma = { previewSigma = it },
                    onResetToCrowd = {
                        previewMu = market.crowdMu.toFloat()
                        previewSigma = market.crowdSigma.toFloat()
                    },
                    onOpenAdvanced = { showAdvanced = true },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricPill(
                        label = "CROWD GUESS",
                        value = "${market.crowdMu.compactDecimal(2)} ${market.unit}",
                        accent = DemoColors.AccentCrowd,
                    )
                    MetricPill(label = "± RANGE", value = market.crowdSigma.compactDecimal(2))
                    MetricPill(label = "BETTORS", value = market.bettorCount.toString())
                    MetricPill(label = "VOL", value = formatShortMoney(market.volumeUsd))
                }
            }
            item { TabBar(activeTab, onSelect = { activeTab = it }) }
            when (activeTab) {
                DetailTab.Bet -> item { BetTab(state, market, yourLatest = yourLatest) }
                DetailTab.Stats -> item { StatsTab(market) }
                DetailTab.Flow -> items(activity, key = { "${market.id}-${it.anonHandle}-${it.ageMinutes}" }) { event ->
                    ActivityRow(event = event, unit = market.unit, modifier = Modifier.padding(horizontal = 20.dp))
                }
                DetailTab.Rules -> item { RulesTab(market) }
            }
        }
        BottomBetCta(
            modifier = Modifier.align(Alignment.BottomCenter),
            onPlace = {
                if (market.marketType == com.solanadistributionmarketdemo.data.MarketType.Estimation) {
                    BetSheetPrefill.muOverride = previewMu.toDouble()
                    BetSheetPrefill.sigmaOverride = previewSigma.toDouble()
                    state.showBetSheet.value = true
                }
            },
            isOnChain = market.isOnChain,
            marketType = market.marketType,
        )
        if (showAdvanced) {
            AdvancedSigmaSheet(
                market = market,
                sigma = previewSigma,
                onSigma = { previewSigma = it },
                onDismiss = { showAdvanced = false },
            )
        }
    }
}

@Composable
private fun DistributionSliderCard(
    market: MarketListing,
    mu: Float,
    sigma: Float,
    onMu: (Float) -> Unit,
    onSigma: (Float) -> Unit,
    onResetToCrowd: () -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tune your bet", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "reset",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable(onClick = onResetToCrowd),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "advanced",
                color = DemoColors.AccentCrowd,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenAdvanced),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Slide your average and pick how confident you are.",
            color = DemoColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))

        SliderRow(
            label = "Average",
            value = mu,
            range = market.muMin.toFloat()..market.muMax.toFloat(),
            display = "${mu.toDouble().compactDecimal(3)} ${market.unit}",
            crowd = market.crowdMu,
            unit = market.unit,
            accent = DemoColors.AccentYou,
            onChange = onMu,
        )
        Spacer(Modifier.height(10.dp))
        // Confidence is an inverse view of σ over [sigmaMin, sigmaMax]: lower σ → tighter range → higher confidence %.
        val sigmaMin = market.sigmaMin.toFloat()
        val sigmaMax = market.sigmaMax.toFloat()
        val span = (sigmaMax - sigmaMin).coerceAtLeast(0.0001f)
        val confidenceValue = (1f - (sigma - sigmaMin) / span).coerceIn(0f, 1f)
        SliderRow(
            label = "Confidence",
            value = confidenceValue,
            range = 0f..1f,
            display = "${(confidenceValue * 100).toInt()}%",
            crowd = (1f - (market.crowdSigma.toFloat() - sigmaMin) / span).coerceIn(0f, 1f).toDouble(),
            unit = " confident",
            accent = DemoColors.AccentCrowd,
            onChange = { c -> onSigma(sigmaMin + (1f - c) * span) },
            crowdAsPercentage = true,
        )
        Spacer(Modifier.height(12.dp))
        GhostButton(
            label = "Advanced selection · σ ${sigma.toDouble().compactDecimal(3)}",
            onClick = onOpenAdvanced,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdvancedSigmaSheet(
    market: MarketListing,
    sigma: Float,
    onSigma: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sigmaMin = market.sigmaMin.toFloat()
    val sigmaMax = market.sigmaMax.toFloat()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            "Advanced selection",
                            color = DemoColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Raw standard deviation control",
                            color = DemoColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Done",
                        color = DemoColors.AccentCrowd,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Moving σ here updates the chart behind this popup. Lower σ means a tighter, more confident curve.",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = "Standard deviation",
                    value = sigma,
                    range = sigmaMin..sigmaMax,
                    display = sigma.toDouble().compactDecimal(3),
                    crowd = market.crowdSigma,
                    unit = "",
                    accent = DemoColors.AccentLong,
                    onChange = onSigma,
                )
                CompactDivider()
                StatRow("Crowd σ", market.crowdSigma.compactDecimal(3), DemoColors.AccentCrowd)
                StatRow("Your σ", sigma.toDouble().compactDecimal(3), DemoColors.AccentLong, strong = true)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    label = "Done",
                    onClick = onDismiss,
                    accent = DemoColors.AccentYou,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    crowd: Double,
    unit: String,
    accent: androidx.compose.ui.graphics.Color,
    onChange: (Float) -> Unit,
    crowdAsPercentage: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DemoColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(
            display,
            color = accent,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = DemoColors.SurfaceMuted,
            activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
            inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
    val crowdText = if (crowdAsPercentage) {
        "crowd's ${(crowd * 100).toInt()}%${if (unit.isNotEmpty()) unit else ""}"
    } else {
        "crowd's ${crowd.compactDecimal(2)}${if (unit.isNotEmpty()) " $unit" else ""}"
    }
    Text(
        crowdText,
        color = DemoColors.TextDim,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun BackBar(onBack: () -> Unit, market: MarketListing) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DemoColors.SurfaceElevated)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text("Markets", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(market.unit.uppercase(), color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LegendRow(unit: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot("Crowd belief", DemoColors.AccentCrowd)
        LegendDot("Your curve", DemoColors.AccentYou)
        LegendDot("Make money", DemoColors.AccentLong)
        LegendDot("Lose money", DemoColors.AccentShort)
        LegendDot("Realized", DemoColors.AccentWarn)
        Spacer(Modifier.weight(1f))
        Text("x = $unit", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Text(label, color = DemoColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TabBar(active: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        DetailTab.entries.forEach { tab ->
            val selected = tab == active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    tab.label,
                    color = if (selected) DemoColors.TextPrimary else DemoColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(if (selected) DemoColors.AccentYou else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun BetTab(state: AppState, market: MarketListing, yourLatest: com.solanadistributionmarketdemo.data.BetRecord?) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Text("Quick takes", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap a stance, then tune in the bet sheet.",
                color = DemoColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            QuickTakes(state, market)
        }
        if (yourLatest != null) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your last bet", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (yourLatest.resolved && yourLatest.realizedPnl != null) {
                        PnlText(yourLatest.realizedPnl, prefix = "$")
                    } else {
                        TagPill("OPEN", color = DemoColors.AccentLong)
                    }
                }
                Spacer(Modifier.height(12.dp))
                StatRow("Your guess", "${yourLatest.mu.compactDecimal(3)} ${market.unit}", accent = DemoColors.AccentYou)
                StatRow("How sure", yourLatest.sigma.compactDecimal(3))
                StatRow("Stake", "$${yourLatest.stake.compactDecimal(2)}")
                StatRow("Stake locked", yourLatest.collateral.compactDecimal(4))
                StatRow("Fee", yourLatest.fee.compactDecimal(4))
                if (yourLatest.txSignatureHex != null) {
                    StatRow("Tx", com.solanadistributionmarketdemo.core.shortHash(yourLatest.txSignatureHex, 6, 6), accent = DemoColors.AccentChain)
                }
            }
        }
    }
}

@Composable
private fun QuickTakes(state: AppState, market: MarketListing) {
    val takes = listOf(
        QuickTake("Above crowd", "+0.6σ", market.crowdMu + market.crowdSigma * 0.6, market.crowdSigma),
        QuickTake("Below crowd", "−0.6σ", market.crowdMu - market.crowdSigma * 0.6, market.crowdSigma),
        QuickTake("Sharper", "0.5×σ", market.crowdMu, market.crowdSigma * 0.5),
        QuickTake("Wider", "1.6×σ", market.crowdMu, market.crowdSigma * 1.6),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        takes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    Box(modifier = Modifier.weight(1f)) {
                        QuickTakeCard(t) {
                            state.showBetSheet.value = true
                            state.lastSubmit.value = null
                            BetSheetPrefill.muOverride = t.mu
                            BetSheetPrefill.sigmaOverride = t.sigma
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class QuickTake(val title: String, val sub: String, val mu: Double, val sigma: Double)

@Composable
private fun QuickTakeCard(take: QuickTake, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(take.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(take.sub, color = DemoColors.AccentYou, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StatsTab(market: MarketListing) {
    Card(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Crowd μ over time", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Sparkline(values = market.crowdHistory, modifier = Modifier.fillMaxWidth(), height = 80.dp, strokeWidth = 2.5f)
        Spacer(Modifier.height(14.dp))
        StatRow("Total volume", formatShortMoney(market.volumeUsd))
        StatRow("Bettors", market.bettorCount.toString())
        StatRow("μ range", "${market.muMin.compactDecimal(2)} – ${market.muMax.compactDecimal(2)} ${market.unit}")
        StatRow("σ range", "${market.sigmaMin.compactDecimal(2)} – ${market.sigmaMax.compactDecimal(2)}")
    }
}

@Composable
private fun ActivityRow(event: ActivityEvent, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.anonHandle, color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    "μ=${event.mu.compactDecimal(2)} $unit  ·  σ=${event.sigma.compactDecimal(2)}",
                    color = DemoColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$${event.stake.compactDecimal(0)}", color = DemoColors.AccentLong, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${event.ageMinutes}m ago", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RulesTab(market: MarketListing) {
    Card(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionLabel("Resolution source")
        Spacer(Modifier.height(4.dp))
        Text(market.resolutionSource, color = DemoColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Rule")
        Spacer(Modifier.height(4.dp))
        Text(market.resolutionRule, color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Resolves at")
        Spacer(Modifier.height(4.dp))
        Text(market.resolvesAt, color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BottomBetCta(
    modifier: Modifier = Modifier,
    onPlace: () -> Unit,
    isOnChain: Boolean,
    marketType: com.solanadistributionmarketdemo.data.MarketType,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Transparent, DemoColors.Background)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (marketType) {
            com.solanadistributionmarketdemo.data.MarketType.Estimation ->
                PrimaryButton(
                    label = if (isOnChain) "Place bet  ·  sign on devnet" else "Place bet",
                    onClick = onPlace,
                    accent = DemoColors.AccentYou,
                )
            com.solanadistributionmarketdemo.data.MarketType.RegimeIndex,
            com.solanadistributionmarketdemo.data.MarketType.Perp ->
                PrimaryButton(
                    label = "Long / short flow coming next",
                    onClick = {},
                    enabled = false,
                    accent = DemoColors.SurfaceMuted,
                )
        }
    }
}

internal object BetSheetPrefill {
    var muOverride: Double? = null
    var sigmaOverride: Double? = null
    var regimeSide: RegimeBetSide? = null
    var perpSide: PerpBetSide? = null
    fun consume(): Pair<Double?, Double?> {
        val m = muOverride; val s = sigmaOverride
        muOverride = null; sigmaOverride = null
        return m to s
    }
    fun consumeRegimeSide(): RegimeBetSide? {
        val s = regimeSide; regimeSide = null; return s
    }
    fun consumePerpSide(): PerpBetSide? {
        val s = perpSide; perpSide = null; return s
    }
}

internal fun formatShortMoney(usd: Double): String = when {
    usd >= 1_000_000 -> "$${(usd / 1_000_000).compactDecimal(2)}M"
    usd >= 1_000 -> "$${(usd / 1_000).compactDecimal(1)}k"
    else -> "$${usd.compactDecimal(0)}"
}
