package com.solanadistributionmarketdemo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val PREFS = "onboarding-store"
private const val KEY_SEEN = "seen"

class OnboardingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hasSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)
    fun markSeen() { prefs.edit().putBoolean(KEY_SEEN, true).apply() }
    fun reset() { prefs.edit().remove(KEY_SEEN).apply() }
}

private data class OnboardingStep(
    val glyph: String,
    val title: String,
    val body: String,
    val accent: Color,
)

@Composable
fun OnboardingOverlay(store: OnboardingStore, onDone: () -> Unit) {
    val steps = listOf(
        OnboardingStep(
            glyph = "◆",
            title = "Pick a market",
            body = "Browse questions about real outcomes — CPI, BTC close, Coachella attendance, the weather. Tap one to see what the crowd thinks.",
            accent = DemoColors.AccentCrowd,
        ),
        OnboardingStep(
            glyph = "◯",
            title = "Set your guess",
            body = "Slide to your number, then slide to set how sure you are. A wider range covers more outcomes; a tighter one pays more if you're right.",
            accent = DemoColors.AccentYou,
        ),
        OnboardingStep(
            glyph = "◉",
            title = "Win when reality lands close",
            body = "Place a stake. The closer reality lands to your guess, the more you win. Sign on Solana devnet — the on-chain market is real.",
            accent = DemoColors.AccentLong,
        ),
    )
    var step by remember { mutableStateOf(0) }
    val current = steps[step]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DemoColors.Background.copy(alpha = 0.96f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(current.accent.copy(alpha = 0.16f))
                    .border(1.dp, current.accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    current.glyph,
                    color = current.accent,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${step + 1} of ${steps.size}",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                current.title,
                color = DemoColors.TextPrimary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                current.body,
                color = DemoColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GhostButton(
                        label = "Skip",
                        onClick = { store.markSeen(); onDone() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        label = if (step == steps.lastIndex) "Get started" else "Next",
                        onClick = {
                            if (step == steps.lastIndex) {
                                store.markSeen(); onDone()
                            } else {
                                step += 1
                            }
                        },
                        accent = current.accent,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (i == step) current.accent else DemoColors.SurfaceMuted)
                    )
                }
            }
        }
    }
}
