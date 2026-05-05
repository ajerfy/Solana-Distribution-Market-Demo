package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val payload = remember { loadDemoPayload(context.assets.open("demo_market.json").bufferedReader().use { it.readText() }) }
                HackathonMarketScreen(payload)
            }
        }
    }
}

data class DemoPayload(
    val market: DemoMarket,
    val presets: List<DemoPreset>,
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
private fun HackathonMarketScreen(payload: DemoPayload) {
    var selectedPreset by remember { mutableStateOf(payload.presets.first()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Solana Distribution Market Demo",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(payload.market.title, style = MaterialTheme.typography.titleMedium)
                    Text("Current mu: ${payload.market.currentMuDisplay}")
                    Text("Current sigma: ${payload.market.currentSigmaDisplay}")
                    Text("Backing: ${payload.market.backingDisplay}")
                    Text("Trades so far: ${payload.market.totalTrades}")
                }
            }
        }

        item {
            Text(
                text = "Tap a preset trade quote",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(payload.presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                selected = preset.id == selectedPreset.id,
                onSelect = { selectedPreset = preset },
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Selected trade intent", style = MaterialTheme.typography.titleMedium)
                    Text("Target mu: ${selectedPreset.targetMuDisplay}")
                    Text("Target sigma: ${selectedPreset.targetSigmaDisplay}")
                    Text("Collateral preview: ${selectedPreset.collateralRequiredDisplay}")
                    Text(
                        text = "Serialized instruction",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(selectedPreset.serializedInstructionHex.take(96) + "...")
                    Text(
                        text = "Wallet submission is the next milestone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: DemoPreset,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(preset.label, style = MaterialTheme.typography.titleSmall)
            Text("Target mu: ${preset.targetMuDisplay}")
            Text("Target sigma: ${preset.targetSigmaDisplay}")
            Text("Collateral: ${preset.collateralRequiredDisplay}")
            if (selected) {
                Text(
                    "Selected",
                    color = containerColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun loadDemoPayload(json: String): DemoPayload {
    val root = JSONObject(json)
    val marketObject = root.getJSONObject("market")
    val presetsArray = root.getJSONArray("presets")
    return DemoPayload(
        market = DemoMarket(
            title = marketObject.getString("title"),
            currentMuDisplay = marketObject.getString("current_mu_display"),
            currentSigmaDisplay = marketObject.getString("current_sigma_display"),
            backingDisplay = marketObject.getString("backing_display"),
            totalTrades = marketObject.getLong("total_trades"),
        ),
        presets = presetsArray.toPresetList(),
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
