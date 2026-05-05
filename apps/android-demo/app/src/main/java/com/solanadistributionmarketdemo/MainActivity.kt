package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val payload = remember {
                        loadDemoPayload(
                            context.assets.open("demo_market.json").bufferedReader().use { it.readText() }
                        )
                    }
                    TradeAppScreen(payload)
                }
            }
        }
    }
}

data class DemoPayload(
    val market: DemoMarket,
    val presets: List<DemoPreset>,
    val quoteGrid: List<DemoPreset>,
)

data class DemoMarket(
    val title: String,
    val currentMuDisplay: String,
    val currentSigmaDisplay: String,
    val backingDisplay: String,
    val totalTrades: Long,
)

data class DemoPreset(
    val id: String,
    val label: String,
    val targetMuDisplay: String,
    val targetSigmaDisplay: String,
    val collateralRequiredDisplay: String,
    val serializedInstructionHex: String,
)

@Composable
private fun TradeAppScreen(payload: DemoPayload) {
    var selectedPreset by remember { mutableStateOf(payload.presets.first()) }
    var targetMu by remember { mutableStateOf(payload.presets.first().targetMuDisplay) }
    var targetSigma by remember { mutableStateOf(payload.presets.first().targetSigmaDisplay) }
    var previewQuote by remember { mutableStateOf(payload.presets.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MarketHero(payload.market)

        QuoteBuilderCard(
            targetMu = targetMu,
            targetSigma = targetSigma,
            onMuChange = { targetMu = it },
            onSigmaChange = { targetSigma = it },
            onPreview = {
                previewQuote = nearestQuote(
                    payload.quoteGrid,
                    targetMu.toDoubleOrNull(),
                    targetSigma.toDoubleOrNull(),
                )
            },
        )

        SelectedQuoteCard(previewQuote = previewQuote)

        PresetStrip(
            presets = payload.presets,
            selectedId = selectedPreset.id,
            onSelect = { preset ->
                selectedPreset = preset
                targetMu = preset.targetMuDisplay
                targetSigma = preset.targetSigmaDisplay
                previewQuote = preset
            },
        )

        MarketDepthHint(payload.quoteGrid.size)
    }
}

@Composable
private fun MarketHero(market: DemoMarket) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Trade a forecast",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = market.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Set your own Normal distribution for where you think the outcome lands.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(label = "Market mean", value = market.currentMuDisplay)
                StatPill(label = "Market sigma", value = market.currentSigmaDisplay)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(label = "Backing", value = market.backingDisplay)
                StatPill(label = "Trades", value = market.totalTrades.toString())
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuoteBuilderCard(
    targetMu: String,
    targetSigma: String,
    onMuChange: (String) -> Unit,
    onSigmaChange: (String) -> Unit,
    onPreview: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Build your distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose the center of your forecast and how tight or wide you want the distribution to be.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = targetMu,
                onValueChange = onMuChange,
                label = { Text("Target mu") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = targetSigma,
                onValueChange = onSigmaChange,
                label = { Text("Target sigma") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preview Trade")
            }
        }
    }
}

@Composable
private fun SelectedQuoteCard(previewQuote: DemoPreset) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Trade preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(label = "Chosen mean", value = previewQuote.targetMuDisplay)
                StatPill(label = "Chosen sigma", value = previewQuote.targetSigmaDisplay)
            }
            Text(
                text = "Required collateral: ${previewQuote.collateralRequiredDisplay}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = previewQuote.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Trade payload preview",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = previewQuote.serializedInstructionHex.take(110) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { }) {
                Text("Wallet submit flow: next milestone")
            }
        }
    }
}

@Composable
private fun PresetStrip(
    presets: List<DemoPreset>,
    selectedId: String,
    onSelect: (DemoPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick market views",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            presets.forEach { preset ->
                AssistChip(
                    onClick = { onSelect(preset) },
                    label = {
                        Text(
                            if (preset.id == selectedId) {
                                "${preset.label} • selected"
                            } else {
                                preset.label
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MarketDepthHint(quoteCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "This demo is currently searching across $quoteCount SDK-generated quote points and snapping your entry to the closest available trade.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun loadDemoPayload(json: String): DemoPayload {
    val root = JSONObject(json)
    val marketObject = root.getJSONObject("market")
    val presetsArray = root.getJSONArray("presets")
    val quoteGridArray = root.getJSONArray("quote_grid")
    return DemoPayload(
        market = DemoMarket(
            title = marketObject.getString("title"),
            currentMuDisplay = marketObject.getString("current_mu_display"),
            currentSigmaDisplay = marketObject.getString("current_sigma_display"),
            backingDisplay = marketObject.getString("backing_display"),
            totalTrades = marketObject.getLong("total_trades"),
        ),
        presets = presetsArray.toPresetList(),
        quoteGrid = quoteGridArray.toPresetList(),
    )
}

private fun JSONArray.toPresetList(): List<DemoPreset> {
    val presets = mutableListOf<DemoPreset>()
    for (index in 0 until length()) {
        val preset = getJSONObject(index)
        presets.add(
            DemoPreset(
                id = preset.getString("id"),
                label = preset.getString("label"),
                targetMuDisplay = preset.getString("target_mu_display"),
                targetSigmaDisplay = preset.getString("target_sigma_display"),
                collateralRequiredDisplay = preset.getString("collateral_required_display"),
                serializedInstructionHex = preset.getString("serialized_instruction_hex"),
            )
        )
    }
    return presets
}

private fun nearestQuote(
    quotes: List<DemoPreset>,
    requestedMu: Double?,
    requestedSigma: Double?,
): DemoPreset {
    if (requestedMu == null || requestedSigma == null) {
        return quotes.first()
    }

    return quotes.minByOrNull { quote ->
        val mu = quote.targetMuDisplay.toDoubleOrNull() ?: 0.0
        val sigma = quote.targetSigmaDisplay.toDoubleOrNull() ?: 0.0
        val muDistance = requestedMu - mu
        val sigmaDistance = requestedSigma - sigma
        (muDistance * muDistance) + (sigmaDistance * sigmaDistance)
    } ?: quotes.first()
}
