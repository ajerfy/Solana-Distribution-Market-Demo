package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HackathonMarketScreen()
            }
        }
    }
}

@Composable
private fun HackathonMarketScreen() {
    var targetMu by remember { mutableStateOf("100.0") }
    var targetSigma by remember { mutableStateOf("10.0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Solana Distribution Market Demo",
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Seeded market")
                Text("Current mu: 95.0")
                Text("Current sigma: 10.0")
                Text("Backing: 50.0")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Trader quote")
                OutlinedTextField(
                    value = targetMu,
                    onValueChange = { targetMu = it },
                    label = { Text("Target mu") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetSigma,
                    onValueChange = { targetSigma = it },
                    label = { Text("Target sigma") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Preview trade intent")
                }
                TextButton(onClick = { }) {
                    Text("Wallet submit flow: next milestone")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Expected SDK response")
                Text("Collateral preview: pending")
                Text("Serialized instruction: pending")
            }
        }
    }
}
