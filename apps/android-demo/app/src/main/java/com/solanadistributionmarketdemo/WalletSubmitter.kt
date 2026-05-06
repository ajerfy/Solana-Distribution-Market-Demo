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
import java.util.Locale

private const val DEVNET_RPC_URL = "https://api.devnet.solana.com"
private const val MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
private const val IDENTITY_NAME = "Solana Distribution Market Demo"

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
        return try {
            val walletAdapter = MobileWalletAdapter(
                connectionIdentity = ConnectionIdentity(
                    identityUri = Uri.parse("https://github.com/ajerfy/Solana-Distribution-Market-Demo"),
                    iconUri = Uri.parse("/favicon.ico"),
                    identityName = IDENTITY_NAME,
                )
            )

            when (
                val result = walletAdapter.transact(sender) { authResult ->
                    val walletAddress = SolanaPublicKey(authResult.accounts[0].publicKey)
                    ensureDevnetBalance(walletAddress)
                    val transaction = buildMemoTransaction(walletAddress, memoText)
                    val sendResult = signAndSendTransactions(arrayOf(transaction.serialize()))
                    val signatureBytes = sendResult.signatures.firstOrNull()
                        ?: throw IllegalStateException(
                            "The wallet returned no signature for the devnet memo. Make sure Phantom is on devnet and try again."
                        )
                    val signatureHex = encodeHex(signatureBytes)
                    bestEffortConfirm(signatureHex)
                    signatureHex
                }
            ) {
                is TransactionResult.Success -> {
                    val signatureHex = result.payload.ifBlank {
                        return WalletSubmitResult.Failure(
                            "The wallet approved the transaction, but no signature came back."
                        )
                    }
                    WalletSubmitResult.Success(
                        signatureHex = signatureHex,
                        walletAddress = encodeHex(result.authResult.accounts[0].publicKey),
                    )
                }
                is TransactionResult.NoWalletFound -> WalletSubmitResult.NoWalletFound
                is TransactionResult.Failure -> WalletSubmitResult.Failure(
                    walletMessage(describeFailure(result.message, result.e), memoText.length)
                )
            }
        } catch (error: Exception) {
            WalletSubmitResult.Failure(
                walletMessage(describeException(error), memoText.length)
            )
        }
    }

    private suspend fun bestEffortConfirm(signatureHex: String) {
        try {
            val rpcClient = SolanaRpcClient(DEVNET_RPC_URL, KtorNetworkDriver())
            rpcClient.confirmTransaction(
                signatureHex,
                TransactionOptions(commitment = Commitment.CONFIRMED),
            )
        } catch (_: Exception) {
            // Confirmation is best-effort: the wallet already returned a signature, the demo can proceed.
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

    private suspend fun buildMemoTransaction(
        signerAddress: SolanaPublicKey,
        memoText: String,
    ): Transaction {
        val rpcClient = SolanaRpcClient(DEVNET_RPC_URL, KtorNetworkDriver())
        val blockhashResponse = rpcClient.getLatestBlockhash()
        val blockhash = blockhashResponse.result?.blockhash
            ?: throw IllegalStateException("Could not fetch a recent blockhash from devnet.")

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

    private suspend fun ensureDevnetBalance(address: SolanaPublicKey) {
        val rpcClient = SolanaRpcClient(DEVNET_RPC_URL, KtorNetworkDriver())
        val balance = rpcClient.getBalance(address, Commitment.CONFIRMED).result
            ?: throw IllegalStateException(
                "This wallet does not appear to exist on devnet yet. Request a devnet airdrop in the wallet and try again."
            )

        if (balance <= 0L) {
            throw IllegalStateException(
                "This wallet has 0 devnet SOL. Request a devnet airdrop in the wallet before submitting the demo memo."
            )
        }
    }
}

private fun walletMessage(message: String, memoLength: Int): String {
    val normalized = message.trim().ifBlank { "Wallet submission failed." }
    return when {
        normalized.equals("null", ignoreCase = true) ->
            "The wallet returned no error details and the demo memo was not confirmed. We attempted a short $memoLength-byte devnet memo. Try opening the wallet first, then retry. If it persists, the wallet likely signed nothing or dropped the send handoff."

        normalized.contains("LifecycleOwner", ignoreCase = true) ||
            normalized.contains("register before", ignoreCase = true) ->
            "Wallet connection is not ready. Restart the app and try again, or install/open a compatible Solana wallet."

        normalized.contains("ActivityNotFound", ignoreCase = true) ->
            "No compatible Solana wallet is connected or installed on this device."

        normalized.contains("does not appear to exist on devnet", ignoreCase = true) ||
            normalized.contains("0 devnet SOL", ignoreCase = true) ->
            normalized

        else -> "${normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }} (memo length: $memoLength bytes)"
    }
}

fun encodeHex(bytes: ByteArray): String {
    val hex = StringBuilder(bytes.size * 2)
    bytes.forEach { byte ->
        hex.append(((byte.toInt() ushr 4) and 0x0f).toString(16))
        hex.append((byte.toInt() and 0x0f).toString(16))
    }
    return hex.toString()
}
