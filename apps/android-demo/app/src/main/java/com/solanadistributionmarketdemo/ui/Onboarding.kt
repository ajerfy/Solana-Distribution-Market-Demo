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
import com.solanadistributionmarketdemo.data.NavTab

private const val PREFS = "onboarding-store"

class OnboardingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hasSeen(tab: NavTab): Boolean = prefs.getBoolean(keyFor(tab), false)
    fun markSeen(tab: NavTab) { prefs.edit().putBoolean(keyFor(tab), true).apply() }
    fun markAllSeen() {
        prefs.edit().apply {
            NavTab.entries.forEach { putBoolean(keyFor(it), true) }
        }.apply()
    }
    fun reset() {
        prefs.edit().apply {
            NavTab.entries.forEach { remove(keyFor(it)) }
            remove("seen")
        }.apply()
    }

    private fun keyFor(tab: NavTab): String = "seen_${tab.name.lowercase()}"
}

private data class OnboardingStep(
    val glyph: String,
    val title: String,
    val body: String,
    val accent: Color,
)

@Composable
fun BottomTabTutorialOverlay(
    tab: NavTab,
    onDone: () -> Unit,
    onSkipAll: () -> Unit,
) {
    val steps = tab.tutorialSteps()
    var step by remember { mutableStateOf(0) }
    val current = steps[step]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DemoColors.Background.copy(alpha = 0.82f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DemoColors.Surface)
                .border(1.dp, DemoColors.BorderStrong, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "PARABOLA",
                    color = DemoColors.AccentYou,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                TagPill("${tab.label} ${step + 1}/${steps.size}", color = current.accent)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(current.accent.copy(alpha = 0.16f))
                        .border(1.dp, current.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        current.glyph,
                        color = current.accent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        current.title,
                        color = DemoColors.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        current.body,
                        color = DemoColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
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
            Spacer(Modifier.height(2.dp))
            Text(
                "These tutorials appear once per bottom tab. You can replay them from Wallet.",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GhostButton(
                        label = "Skip all",
                        onClick = onSkipAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        label = if (step == steps.lastIndex) "Done" else "Next",
                        onClick = {
                            if (step == steps.lastIndex) {
                                onDone()
                            } else {
                                step += 1
                            }
                        },
                        accent = current.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTab.tutorialSteps(): List<OnboardingStep> = when (this) {
    NavTab.Markets -> listOf(
        OnboardingStep(
            glyph = "◆",
            title = "Browse the market board",
            body = "The top rail filters by topic: Crypto, Macro, Weather, Sports, Events, and more. Tap any market card to open its chart, rules, and trade flow.",
            accent = DemoColors.AccentCrowd,
        ),
        OnboardingStep(
            glyph = "▦",
            title = "Choose the market type",
            body = "The second rail switches between Estimates, Perps, and Regime indexes. Use it with the category rail to show only the exact market structure you want.",
            accent = DemoColors.AccentWarn,
        ),
    )
    NavTab.Portfolio -> listOf(
        OnboardingStep(
            glyph = "▲",
            title = "Track your positions",
            body = "Portfolio shows open and resolved bets, stake, realized P/L, transaction hashes, and simulated resolution for demo positions.",
            accent = DemoColors.AccentLong,
        )
    )
    NavTab.Engine -> listOf(
        OnboardingStep(
            glyph = "▦",
            title = "Inspect backend state",
            body = "Engine shows what the program sees: maker pool, quote envelope, fee settings, regime baskets, perp funding, vault cash, and last submission receipt.",
            accent = DemoColors.AccentChain,
        )
    )
    NavTab.Wallet -> listOf(
        OnboardingStep(
            glyph = "◉",
            title = "Manage wallet and help",
            body = "Wallet shows connection status, devnet identity, theme controls, and the replay button for these bottom-tab tutorials.",
            accent = DemoColors.AccentYou,
        )
    )
}
