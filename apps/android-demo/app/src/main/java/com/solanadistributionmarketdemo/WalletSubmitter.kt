package com.solanadistributionmarketdemo

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.networking.KtorNetworkDriver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.Commitment
import com.solana.rpc.TransactionOptions
import com.solana.rpc.SolanaRpcClient
import com.solana.transaction.AccountMeta
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import com.solanadistributionmarketdemo.core.encodeHex
import com.solanadistributionmarketdemo.core.formattedDecimal
import com.solanadistributionmarketdemo.data.ContinuousQuotePreview
import com.solanadistributionmarketdemo.data.DemoPerpMarket
import com.solanadistributionmarketdemo.data.DemoPerpQuote
import com.solanadistributionmarketdemo.data.DemoPreset
import com.solanadistributionmarketdemo.data.DemoRegimeIndex
import com.solanadistributionmarketdemo.data.DemoRegimeQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Devnet JSON-RPC bases to try in order. Some devices/OEM builds fail DNS for the official
 * hostname only; Chrome can still work because it may use a different resolver path or DNS-over-HTTPS.
 * See: https://docs.solana.com/cluster/rpc-endpoints (official) and public alternates such as Ankr.
 */
private val DEVNET_RPC_CANDIDATES = listOf(
    "https://api.devnet.solana.com",
    "https://rpc.ankr.com/solana_devnet",
)

/** Remember which base URL worked so blockhash, send, and confirm stay consistent. */
private val activeDevnetRpcBase = AtomicReference<String?>(null)

private const val MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
private const val IDENTITY_NAME = "Parabola"

sealed class WalletSubmitResult {
    data class Success(
        val signatureHex: String,
        val walletAddress: String,
    ) : WalletSubmitResult()

    data class Failure(val message: String) : WalletSubmitResult()

    data object NoWalletFound : WalletSubmitResult()
}

object WalletSubmitter {
    suspend fun submitTradeMemo(
        sender: ActivityResultSender,
        quote: ContinuousQuotePreview,
    ): WalletSubmitResult {
        return submitMemo(sender, buildMemoText(quote))
    }

    suspend fun submitRegimeMemo(
        sender: ActivityResultSender,
        index: DemoRegimeIndex,
        quote: DemoRegimeQuote,
    ): WalletSubmitResult {
        return submitMemo(
            sender,
            "${quote.memoPayload}|index=${index.id}|title=${index.title}",
        )
    }

    suspend fun submitPerpMemo(
        sender: ActivityResultSender,
        market: DemoPerpMarket,
        quote: DemoPerpQuote,
    ): WalletSubmitResult {
        return submitMemo(
            sender,
            "${quote.memoPayload}|market=${market.symbol}|title=${market.title}",
        )
    }

    private suspend fun submitMemo(
        sender: ActivityResultSender,
        memoText: String,
    ): WalletSubmitResult {
        // Pre-fetch the blockhash BEFORE opening the wallet, while the demo app is fully foregrounded.
        // Some Android builds (Solana Seeker among them) restrict per-app outbound networking once
        // another activity is foregrounded by an intent, so doing this first is safer.
        val blockhash = try {
            fetchRecentBlockhashWithRetry()
        } catch (error: Exception) {
            return WalletSubmitResult.Failure(
                walletMessage(describeException(error), memoText.length)
            )
        }
        // Step 1: ask the wallet to sign — and ONLY sign — and return the bytes.
        val signOutcome: TransactionResult<ByteArray> = try {
            val walletAdapter = MobileWalletAdapter(
                connectionIdentity = ConnectionIdentity(
                    identityUri = Uri.parse("https://github.com/ajerfy/Solana-Distribution-Market-Demo"),
                    iconUri = Uri.EMPTY,
                    identityName = IDENTITY_NAME,
                )
            )
            walletAdapter.transact(sender) { authResult ->
                val walletAddress = SolanaPublicKey(authResult.accounts[0].publicKey)
                val transaction = buildMemoTransactionWithBlockhash(walletAddress, memoText, blockhash)
                val signResult = signTransactions(arrayOf(transaction.serialize()))
                signResult.signedPayloads.firstOrNull()
                    ?: throw IllegalStateException(
                        "The wallet returned no signed payload. Make sure Phantom is on devnet and try again."
                    )
            }
        } catch (error: Exception) {
            return WalletSubmitResult.Failure(walletMessage(describeException(error), memoText.length))
        }

        val signedTxBytes: ByteArray
        val walletPubkey: ByteArray
        when (signOutcome) {
            is TransactionResult.Success -> {
                signedTxBytes = signOutcome.payload
                walletPubkey = signOutcome.authResult.accounts[0].publicKey
            }
            is TransactionResult.NoWalletFound -> return WalletSubmitResult.NoWalletFound
            is TransactionResult.Failure -> return WalletSubmitResult.Failure(
                walletMessage(describeFailure(signOutcome.message, signOutcome.e), memoText.length)
            )
        }

        // Step 2: broadcast OUTSIDE walletAdapter.transact, with our app fully foregrounded.
        // Small settle delay because Android sometimes briefly restricts network mid-resume
        // after a wallet activity hands control back via intent result.
        delay(250L)
        return try {
            val signatureBase58 = sendSignedTransactionWithRetry(signedTxBytes)
            bestEffortConfirm(signatureBase58)
            WalletSubmitResult.Success(
                signatureHex = signatureBase58,
                walletAddress = encodeHex(walletPubkey),
            )
        } catch (error: Exception) {
            WalletSubmitResult.Failure(walletMessage(describeException(error), memoText.length))
        }
    }

    private suspend fun bestEffortConfirm(signatureBase58: String) {
        // Confirmation is best-effort. We use HttpURLConnection (system stack) instead of
        // SolanaRpcClient + KtorNetworkDriver because Ktor's CIO engine fails DNS on Seeker.
        try {
            withContext(Dispatchers.IO) {
                val base = activeDevnetRpcBase.get() ?: DEVNET_RPC_CANDIDATES.first()
                confirmSignatureViaHttpURLConnection(signatureBase58, base)
            }
        } catch (_: Exception) {
            // Already broadcast; UI shows success with the signature. Confirmation isn't critical.
        }
    }

    private fun confirmSignatureViaHttpURLConnection(signatureBase58: String, rpcBaseUrl: String) {
        val conn = (URL(rpcBaseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 6_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = """{"jsonrpc":"2.0","id":1,"method":"getSignatureStatuses","params":[["$signatureBase58"],{"searchTransactionHistory":true}]}"""
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            // Best-effort: don't even read the response body — getting here means the call succeeded.
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun describeFailure(message: String, throwable: Throwable?): String {
        val raw = sequenceOf(message, throwable?.message, throwable?.javaClass?.simpleName)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            .orEmpty()
        return if (raw.isEmpty()) {
            throwable?.let { "${it.javaClass.simpleName}: <no message>" } ?: "Wallet submission failed."
        } else raw
    }

    private fun describeException(error: Throwable): String {
        val parts = mutableListOf<String>()
        parts += error.javaClass.simpleName
        error.message?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let { parts += it }
        error.cause?.let { cause ->
            cause.javaClass.simpleName.takeIf { it != error.javaClass.simpleName }?.let { parts += "cause=$it" }
            cause.message?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let { parts += it }
        }
        return parts.joinToString(" · ")
    }

    private fun buildMemoText(quote: DemoPreset): String {
        return buildMemoText(
            targetMu = quote.targetMuDisplay,
            targetSigma = quote.targetSigmaDisplay,
            collateral = quote.collateralRequiredDisplay,
            payload = quote.serializedInstructionHex,
        )
    }

    private fun buildMemoText(quote: ContinuousQuotePreview): String {
        return buildMemoText(
            targetMu = quote.targetMu.formattedDecimal(),
            targetSigma = quote.targetSigma.formattedDecimal(),
            collateral = quote.collateralRequired.formattedDecimal(),
            payload = quote.serializedInstructionHex,
        )
    }

    private fun buildMemoText(
        targetMu: String,
        targetSigma: String,
        collateral: String,
        payload: String,
    ): String {
        val payloadFingerprint = payload.take(24)
        return buildString {
            append("distribution-market-demo|")
            append("mu=")
            append(targetMu)
            append("|sigma=")
            append(targetSigma)
            append("|collateral=")
            append(collateral)
            append("|payload_prefix=")
            append(payloadFingerprint)
        }
    }

    private fun buildMemoTransactionWithBlockhash(
        signerAddress: SolanaPublicKey,
        memoText: String,
        blockhash: String,
    ): Transaction {
        val memoInstruction = TransactionInstruction(
            SolanaPublicKey.from(MEMO_PROGRAM_ID),
            listOf(AccountMeta(signerAddress, true, true)),
            memoText.encodeToByteArray(),
        )

        val memoMessage = Message.Builder()
            .addInstruction(memoInstruction)
            .addFeePayer(signerAddress)
            .setRecentBlockhash(blockhash)
            .build()

        return Transaction(memoMessage)
    }

    /**
     * Fetches a recent blockhash via java.net.HttpURLConnection (the system HTTP stack).
     * We bypass Ktor's CIO engine here because on some devices — including the Solana Seeker —
     * CIO's NIO selector throws UnresolvedAddressException for hosts the OS resolves fine.
     */
    private suspend fun fetchRecentBlockhashWithRetry(): String = withContext(Dispatchers.IO) {
        val preferred = activeDevnetRpcBase.get()
        val ordered =
            if (preferred != null) {
                listOf(preferred) + DEVNET_RPC_CANDIDATES.filter { it != preferred }
            } else {
                DEVNET_RPC_CANDIDATES
            }

        val failures = mutableListOf<String>()
        var lastError: Throwable? = null
        for (baseUrl in ordered) {
            repeat(3) { attempt ->
                try {
                    val hash = fetchBlockhashViaHttpURLConnection(baseUrl)
                    activeDevnetRpcBase.set(baseUrl)
                    return@withContext hash
                } catch (error: UnresolvedAddressException) {
                    lastError = error
                    failures += "$baseUrl: ${error.javaClass.simpleName}"
                    delay(200L * (attempt + 1))
                } catch (error: java.net.UnknownHostException) {
                    lastError = error
                    failures += "$baseUrl: UnknownHostException"
                    delay(200L * (attempt + 1))
                } catch (error: java.net.ConnectException) {
                    lastError = error
                    failures += "$baseUrl: ConnectException"
                    delay(200L * (attempt + 1))
                } catch (error: java.net.SocketTimeoutException) {
                    lastError = error
                    failures += "$baseUrl: SocketTimeoutException"
                    delay(200L * (attempt + 1))
                }
            }
        }
        val detail = failures.distinct().joinToString("; ").ifBlank { lastError?.message ?: "unknown" }
        throw IllegalStateException(
            "Could not reach any devnet RPC (${DEVNET_RPC_CANDIDATES.joinToString()}). Tried: $detail. " +
                "If Chrome works but this app does not, disable Private DNS (Settings → Network), allow unrestricted battery/data for Parabola, or switch Wi‑Fi.",
            lastError,
        )
    }

    private suspend fun sendSignedTransactionWithRetry(signedTxBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val primary = activeDevnetRpcBase.get() ?: DEVNET_RPC_CANDIDATES.first()
            val ordered =
                listOf(primary) + DEVNET_RPC_CANDIDATES.filter { it != primary }
            var lastError: Throwable? = null
            for (baseUrl in ordered) {
                repeat(3) { attempt ->
                    try {
                        val sig = sendSignedTransactionViaHttpURLConnection(signedTxBytes, baseUrl)
                        activeDevnetRpcBase.set(baseUrl)
                        return@withContext sig
                    } catch (error: UnresolvedAddressException) {
                        lastError = error; delay(200L * (attempt + 1))
                    } catch (error: java.net.UnknownHostException) {
                        lastError = error; delay(200L * (attempt + 1))
                    } catch (error: java.net.ConnectException) {
                        lastError = error; delay(200L * (attempt + 1))
                    } catch (error: java.net.SocketTimeoutException) {
                        lastError = error; delay(200L * (attempt + 1))
                    }
                }
            }
            throw lastError ?: IllegalStateException("Could not broadcast the signed transaction to devnet.")
        }

    private fun sendSignedTransactionViaHttpURLConnection(signedTxBytes: ByteArray, rpcBaseUrl: String): String {
        val base64 = android.util.Base64.encodeToString(signedTxBytes, android.util.Base64.NO_WRAP)
        val conn = (URL(rpcBaseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = """{"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":["$base64",{"encoding":"base64","skipPreflight":false,"preflightCommitment":"processed"}]}"""
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("Devnet RPC sendTransaction HTTP $code: ${text.take(220).ifBlank { "<no body>" }}")
            }
            val json = JSONObject(text)
            json.optJSONObject("error")?.let { err ->
                throw IllegalStateException("Devnet RPC sendTransaction error: ${err.optString("message", "<no message>")}")
            }
            return json.optString("result").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Devnet RPC sendTransaction returned no signature.")
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchBlockhashViaHttpURLConnection(rpcBaseUrl: String): String {
        val conn = (URL(rpcBaseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("Devnet RPC HTTP $code: ${text.take(200).ifBlank { "<no body>" }}")
            }
            val json = JSONObject(text)
            json.optJSONObject("error")?.let { err ->
                throw IllegalStateException("Devnet RPC error: ${err.optString("message", "<no message>")}")
            }
            val result = json.optJSONObject("result")
                ?: throw IllegalStateException("Devnet RPC returned no result for getLatestBlockhash.")
            val value = result.optJSONObject("value")
                ?: throw IllegalStateException("Devnet RPC getLatestBlockhash result missing value object.")
            return value.optString("blockhash").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Devnet RPC getLatestBlockhash returned empty blockhash.")
        } finally {
            conn.disconnect()
        }
    }
}

private fun walletMessage(message: String, memoLength: Int): String {
    val normalized = message.trim().ifBlank { "Wallet submission failed." }
    return when {
        normalized.equals("null", ignoreCase = true) ->
            "The wallet returned no error details and the demo memo was not confirmed. We attempted a short $memoLength-byte devnet memo. Try opening the wallet first, then retry. If it persists, the wallet likely signed nothing or dropped the send handoff."

        normalized.contains("UnresolvedAddressException", ignoreCase = true) ||
            normalized.contains("UnknownHostException", ignoreCase = true) ||
            normalized.contains("Could not reach any devnet RPC", ignoreCase = true) ->
            "Couldn't reach a devnet RPC (tried public mirrors). Chrome can still work if it uses a different DNS path. Try: disable Private DNS, set Parabola to unrestricted battery + allow background data, or switch network. Diagnostic: $normalized"

        normalized.contains("ConnectException", ignoreCase = true) ||
            normalized.contains("SocketTimeoutException", ignoreCase = true) ->
            "Network call to devnet failed. Diagnostic: $normalized"

        normalized.contains("LifecycleOwner", ignoreCase = true) ||
            normalized.contains("register before", ignoreCase = true) ->
            "Wallet connection is not ready. Restart the app and try again, or install/open a compatible Solana wallet."

        normalized.contains("ActivityNotFound", ignoreCase = true) ->
            "No compatible Solana wallet is connected or installed on this device."

        normalized.contains("local association", ignoreCase = true) ->
            "Couldn't open a session with the wallet. Force-stop Phantom (Settings → Apps → Phantom → Force Stop) and the demo app, then try again — Phantom often holds a stale MWA session after an interrupted sign attempt."

        normalized.contains("Transaction simulation failed", ignoreCase = true) ||
            normalized.contains("InsufficientFundsForFee", ignoreCase = true) ||
            normalized.contains("AccountNotFound", ignoreCase = true) ->
            "Devnet rejected the memo because this wallet account either doesn't exist on devnet yet or has no SOL to pay the fee. In Phantom: settings → developer → request a devnet airdrop, then retry. ($normalized)"

        normalized.contains("BlockhashNotFound", ignoreCase = true) ||
            normalized.contains("blockhash", ignoreCase = true) ->
            "The blockhash expired between fetch and broadcast (the wallet popup took longer than ~60 seconds). Just tap submit again. ($normalized)"

        normalized.contains("does not appear to exist on devnet", ignoreCase = true) ||
            normalized.contains("0 devnet SOL", ignoreCase = true) ->
            normalized

        else -> "${normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }} (memo length: $memoLength bytes)"
    }
}
