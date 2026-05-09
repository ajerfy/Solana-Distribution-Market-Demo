package com.solanadistributionmarketdemo.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.solanadistributionmarketdemo.ui.ThemeMode

class AppState(
    initialPayload: DemoPayload,
    val positionStore: PositionStore,
    val themeStore: ThemeStore,
) {
    private val payloadState: MutableState<DemoPayload> = mutableStateOf(initialPayload)
    val payload: DemoPayload
        get() = payloadState.value
    val markets: List<MarketListing>
        get() = MockMarkets.build(payload)
    val bets: SnapshotStateList<BetRecord> = mutableStateListOf<BetRecord>().apply {
        addAll(positionStore.load())
    }
    val selectedMarketId: MutableState<String?> = mutableStateOf(null)
    val selectedCategory: MutableState<MarketCategory> = mutableStateOf(MarketCategory.All)
    val selectedMarketTypeFilter: MutableState<MarketTypeFilter> = mutableStateOf(MarketTypeFilter.All)
    val activeTab: MutableState<NavTab> = mutableStateOf(NavTab.Markets)
    val showBetSheet: MutableState<Boolean> = mutableStateOf(false)
    val walletAddress: MutableState<String?> = mutableStateOf(null)
    val lastSubmit: MutableState<SubmitStatus?> = mutableStateOf(null)
    val themeMode: MutableState<ThemeMode> = mutableStateOf(themeStore.load())
    val liveSyncStatus: MutableState<LiveSyncStatus> = mutableStateOf(
        LiveSyncStatus(
            mode = LiveSyncMode.Demo,
            source = "Bundled demo asset",
            endpoint = null,
            lastUpdatedMillis = null,
            message = "Using seeded payload until a live backend responds.",
        )
    )
    var replayOnboarding: () -> Unit = {}

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        themeStore.save(mode)
    }

    fun marketById(id: String?): MarketListing? = id?.let { markets.firstOrNull { m -> m.id == it } }

    fun updatePayload(next: DemoPayload) {
        val current = payloadState.value
        val currentSimulation = current.simulation
        val nextSimulation = next.simulation
        val shouldKeepCurrentSimulation = currentSimulation != null &&
            (nextSimulation == null || nextSimulation.isOlderThan(currentSimulation))
        val mergedPayload = if (shouldKeepCurrentSimulation) {
            next.copy(
                market = next.market.copy(
                    status = if (currentSimulation.running) "Live crowd simulation" else next.market.status,
                    currentMuDisplay = currentSimulation.currentMuDisplay,
                    currentSigmaDisplay = currentSimulation.currentSigmaDisplay,
                    totalTrades = currentSimulation.tradeCount,
                ),
                simulation = currentSimulation,
            )
        } else {
            next
        }
        payloadState.value = mergedPayload
        if (selectedMarketId.value != null && marketById(selectedMarketId.value) == null) {
            closeMarket()
        }
    }

    fun updateSimulation(next: DemoSimulation) {
        val current = payloadState.value
        val previousSimulation = current.simulation
        if (previousSimulation != null && next.isOlderThan(previousSimulation)) {
            return
        }
        val feeDelta = displayDelta(previousSimulation?.feesEarnedDisplay, next.feesEarnedDisplay)
        val volumeDelta = displayDelta(previousSimulation?.totalVolumeDisplay, next.totalVolumeDisplay)
        val nextMarket = current.market.copy(
            status = if (next.running) "Live crowd simulation" else current.market.status,
            currentMuDisplay = next.currentMuDisplay,
            currentSigmaDisplay = next.currentSigmaDisplay,
            backingDisplay = addDisplayValue(current.market.backingDisplay, volumeDelta),
            makerFeesEarnedDisplay = addDisplayValue(current.market.makerFeesEarnedDisplay, feeDelta),
            totalTrades = next.tradeCount,
        )
        payloadState.value = current.copy(
            market = nextMarket,
            simulation = next,
        )
    }

    fun updateLiveSync(status: LiveSyncStatus) {
        liveSyncStatus.value = status
    }

    fun openMarket(id: String) {
        selectedMarketId.value = id
        showBetSheet.value = false
    }

    fun closeMarket() {
        selectedMarketId.value = null
        showBetSheet.value = false
    }

    fun addBet(record: BetRecord) {
        bets.add(0, record)
        positionStore.save(bets.toList())
    }

    fun replaceBet(updated: BetRecord) {
        val index = bets.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            bets[index] = updated
            positionStore.save(bets.toList())
        }
    }

    fun resolveBet(record: BetRecord, realized: Double, pnl: Double) {
        replaceBet(record.copy(resolved = true, realizedOutcome = realized, realizedPnl = pnl))
    }

    fun clearAll() {
        bets.clear()
        positionStore.clear()
    }
}

private fun DemoSimulation.isOlderThan(current: DemoSimulation): Boolean =
    revision < current.revision || (revision == current.revision && tick < current.tick)

private fun displayDelta(previous: String?, next: String): Double {
    val nextValue = next.toDoubleOrNull() ?: return 0.0
    val previousValue = previous?.toDoubleOrNull() ?: nextValue
    return nextValue - previousValue
}

private fun addDisplayValue(current: String, delta: Double): String {
    val currentValue = current.toDoubleOrNull() ?: return current
    return "%.9f".format((currentValue + delta).coerceAtLeast(0.0))
}

enum class NavTab(val label: String) {
    Markets("Markets"),
    Portfolio("Portfolio"),
    Engine("Engine"),
    Wallet("Wallet"),
}

enum class LiveSyncMode {
    Demo,
    Connecting,
    Live,
    Error,
}

data class LiveSyncStatus(
    val mode: LiveSyncMode,
    val source: String,
    val endpoint: String?,
    val lastUpdatedMillis: Long?,
    val message: String,
)

@Composable
fun rememberAppState(payload: DemoPayload, store: PositionStore, themeStore: ThemeStore): AppState =
    remember(payload, store, themeStore) { AppState(payload, store, themeStore) }
