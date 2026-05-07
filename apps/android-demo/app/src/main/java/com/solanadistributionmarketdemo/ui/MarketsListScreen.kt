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
import androidx.compose.foundation.lazy.LazyRow
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
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.LiveSyncMode
import com.solanadistributionmarketdemo.data.MarketCategory
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.MarketType
import com.solanadistributionmarketdemo.data.MarketTypeFilter

@Composable
fun MarketsListScreen(state: AppState) {
    val selectedCat = state.selectedCategory.value
    val selectedType = state.selectedMarketTypeFilter.value
    val markets = state.markets.filter { market ->
        val categoryMatches = selectedCat == MarketCategory.All || market.category == selectedCat
        val typeMatches = selectedType.marketType == null || market.marketType == selectedType.marketType
        categoryMatches && typeMatches
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state) }
        item {
            CategoryRail(
                selected = selectedCat,
                onSelect = { state.selectedCategory.value = it },
            )
        }
        item {
            MarketTypeRail(
                selected = selectedType,
                onSelect = { state.selectedMarketTypeFilter.value = it },
                markets = state.markets,
                selectedCategory = selectedCat,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("${markets.size} markets")
                SectionLabel(selectedType.summaryLabel())
            }
        }
        if (markets.isEmpty()) {
            item {
                EmptyMarketFilterState(
                    selectedCategory = selectedCat,
                    selectedType = selectedType,
                    onReset = {
                        state.selectedCategory.value = MarketCategory.All
                        state.selectedMarketTypeFilter.value = MarketTypeFilter.All
                    },
                )
            }
        } else {
            items(markets, key = { it.id }) { market ->
                MarketRow(market = market, onClick = { state.openMarket(market.id) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun Header(state: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val liveStatus = state.liveSyncStatus.value
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "PARABOLA",
                color = DemoColors.AccentYou,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TagPill(
                when (liveStatus.mode) {
                    LiveSyncMode.Live -> "LIVE ORACLE"
                    LiveSyncMode.Connecting -> "CONNECTING"
                    LiveSyncMode.Error -> "LIVE ERROR"
                    LiveSyncMode.Demo -> "DEMO DATA"
                },
                color = when (liveStatus.mode) {
                    LiveSyncMode.Live -> DemoColors.AccentLong
                    LiveSyncMode.Connecting -> DemoColors.AccentWarn
                    LiveSyncMode.Error -> DemoColors.AccentShort
                    LiveSyncMode.Demo -> DemoColors.AccentChain
                }
            )
            TagPill("DEVNET", color = DemoColors.AccentChain)
            Spacer(Modifier.weight(1f))
            ThemeToggleButton(state)
        }
        Text(
            "Bet on the shape, not the side.",
            color = DemoColors.TextPrimary,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Pick a guess, set how sure you are, win when reality lands close. Live on Solana devnet.",
            color = DemoColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            liveStatus.message,
            color = DemoColors.TextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ThemeToggleButton(state: AppState) {
    val light = state.themeMode.value == ThemeMode.Light
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(DemoColors.SurfaceElevated)
            .clickable { state.setTheme(if (light) ThemeMode.Dark else ThemeMode.Light) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (light) "☀" else "☾",
            color = DemoColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            if (light) "Light" else "Dark",
            color = DemoColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryRail(selected: MarketCategory, onSelect: (MarketCategory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MarketCategory.entries) { cat ->
            ChipPill(
                label = cat.label,
                leading = cat.emoji,
                selected = cat == selected,
                onClick = { onSelect(cat) },
            )
        }
    }
}

@Composable
private fun MarketTypeRail(
    selected: MarketTypeFilter,
    onSelect: (MarketTypeFilter) -> Unit,
    markets: List<MarketListing>,
    selectedCategory: MarketCategory,
) {
    val categoryMarkets = markets.filter { selectedCategory == MarketCategory.All || it.category == selectedCategory }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MarketTypeFilter.entries) { filter ->
            val count = categoryMarkets.count { filter.marketType == null || it.marketType == filter.marketType }
            ChipPill(
                label = "${filter.label} $count",
                leading = filter.glyph,
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun EmptyMarketFilterState(
    selectedCategory: MarketCategory,
    selectedType: MarketTypeFilter,
    onReset: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 20.dp), padding = PaddingValues(18.dp)) {
        Text(
            "No ${selectedType.label.lowercase()} in ${selectedCategory.label.lowercase()} yet.",
            color = DemoColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Switch category or clear filters to see every active Parabola market.",
            color = DemoColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        GhostButton("Clear filters", onClick = onReset, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MarketRow(market: MarketListing, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CategoryGlyph(market.category)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (market.isOnChain) TagPill("ON-CHAIN", color = DemoColors.AccentChain, filled = true)
                    MarketTypeBadge(market.marketType)
                    Text(market.category.label.uppercase(), color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
                }
                Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(market.subtitle, color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Sparkline(values = market.crowdHistory, modifier = Modifier.width(72.dp), color = DemoColors.AccentCrowd)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CrowdBlock(label = "CROWD GUESS", value = "${market.crowdMu.compactDecimal(2)} ${market.unit}", modifier = Modifier.weight(1.2f))
            CrowdBlock(label = "± RANGE", value = market.crowdSigma.compactDecimal(2), modifier = Modifier.weight(0.8f))
            CrowdBlock(label = "VOLUME", value = formatVolume(market.volumeUsd), modifier = Modifier.weight(1f))
            CrowdBlock(label = "RESOLVES", value = market.resolvesAt.shorten(), modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun MarketTypeBadge(type: com.solanadistributionmarketdemo.data.MarketType) {
    when (type) {
        MarketType.Estimation ->
            TagPill("ESTIMATE", color = DemoColors.AccentCrowd)
        MarketType.RegimeIndex ->
            TagPill("REGIME", color = DemoColors.AccentLong)
        MarketType.Perp ->
            TagPill("PERP", color = DemoColors.AccentWarn)
    }
}

private fun MarketTypeFilter.summaryLabel(): String = when (this) {
    MarketTypeFilter.All -> "sorted · trending"
    MarketTypeFilter.Estimates -> "filter · estimates"
    MarketTypeFilter.Perps -> "filter · perps"
    MarketTypeFilter.RegimeIndexes -> "filter · regime indexes"
}

@Composable
private fun CategoryGlyph(category: MarketCategory) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            category.emoji,
            color = DemoColors.AccentYou,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun CrowdBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DemoColors.SurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = DemoColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun formatVolume(usd: Double): String = when {
    usd >= 1_000_000 -> "$${(usd / 1_000_000).compactDecimal(2)}M"
    usd >= 1_000 -> "$${(usd / 1_000).compactDecimal(1)}k"
    else -> "$${usd.compactDecimal(0)}"
}

private fun String.shorten(): String = if (length > 12) take(11) + "…" else this
