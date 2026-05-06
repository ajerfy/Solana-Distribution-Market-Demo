package com.solanadistributionmarketdemo.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.solanadistributionmarketdemo.ui.ThemeMode

class AppState(
    val payload: DemoPayload,
    val positionStore: PositionStore,
    val themeStore: ThemeStore,
) {
    val markets: List<MarketListing> = MockMarkets.build(payload)
    val bets: SnapshotStateList<BetRecord> = mutableStateListOf<BetRecord>().apply {
        addAll(positionStore.load())
    }
    val selectedMarketId: MutableState<String?> = mutableStateOf(null)
    val selectedCategory: MutableState<MarketCategory> = mutableStateOf(MarketCategory.All)
    val activeTab: MutableState<NavTab> = mutableStateOf(NavTab.Markets)
    val showBetSheet: MutableState<Boolean> = mutableStateOf(false)
    val walletAddress: MutableState<String?> = mutableStateOf(null)
    val lastSubmit: MutableState<SubmitStatus?> = mutableStateOf(null)
    val themeMode: MutableState<ThemeMode> = mutableStateOf(themeStore.load())

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        themeStore.save(mode)
    }

    fun marketById(id: String?): MarketListing? = id?.let { markets.firstOrNull { m -> m.id == it } }

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

enum class NavTab(val label: String) {
    Markets("Markets"),
    Portfolio("Portfolio"),
    Activity("Activity"),
    Wallet("Wallet"),
}

@Composable
fun rememberAppState(payload: DemoPayload, store: PositionStore, themeStore: ThemeStore): AppState =
    remember(payload, store, themeStore) { AppState(payload, store, themeStore) }
