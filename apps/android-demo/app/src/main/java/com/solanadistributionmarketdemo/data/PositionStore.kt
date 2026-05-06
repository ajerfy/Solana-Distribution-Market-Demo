package com.solanadistributionmarketdemo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PositionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<BetRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toBet() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(records: List<BetRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun BetRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("marketId", marketId)
        put("marketTitle", marketTitle)
        put("mu", mu)
        put("sigma", sigma)
        put("stake", stake)
        put("collateral", collateral)
        put("fee", fee)
        put("placedAtMillis", placedAtMillis)
        put("resolved", resolved)
        if (realizedOutcome != null) put("realizedOutcome", realizedOutcome)
        if (realizedPnl != null) put("realizedPnl", realizedPnl)
        if (txSignatureHex != null) put("txSignatureHex", txSignatureHex)
        put("isOnChain", isOnChain)
    }

    private fun JSONObject.toBet(): BetRecord = BetRecord(
        id = getString("id"),
        marketId = getString("marketId"),
        marketTitle = getString("marketTitle"),
        mu = getDouble("mu"),
        sigma = getDouble("sigma"),
        stake = getDouble("stake"),
        collateral = getDouble("collateral"),
        fee = getDouble("fee"),
        placedAtMillis = getLong("placedAtMillis"),
        resolved = optBoolean("resolved", false),
        realizedOutcome = if (has("realizedOutcome")) getDouble("realizedOutcome") else null,
        realizedPnl = if (has("realizedPnl")) getDouble("realizedPnl") else null,
        txSignatureHex = optString("txSignatureHex").ifEmpty { null },
        isOnChain = optBoolean("isOnChain", false),
    )

    private companion object {
        const val PREFS = "position-store"
        const val KEY = "bets"
    }
}
