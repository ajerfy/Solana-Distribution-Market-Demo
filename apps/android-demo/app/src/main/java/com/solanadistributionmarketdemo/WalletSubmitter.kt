package com.solanadistributionmarketdemo

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.networking.KtorNetworkDriver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.SolanaRpcClient
import com.solana.transaction.AccountMeta
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction

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
                    iconUri = Uri.parse("https://github.githubassets.com/favicons/favicon.png"),
                    identityName = IDENTITY_NAME,
                )
            )

            when (
                val result = walletAdapter.transact(sender) { authResult ->
                    val walletAddress = SolanaPublicKey(authResult.accounts[0].publicKey)
                    val transaction = buildMemoTransaction(walletAddress, memoText)
                    signAndSendTransactions(arrayOf(transaction.serialize()))
                }
            ) {
                is TransactionResult.Success -> {
                    val signatureHex = result.payload
                        .signatures
                        .firstOrNull()
                        ?.let(::encodeHex)
                        ?: return WalletSubmitResult.Failure(
                            "The wallet approved the transaction, but no signature came back."
                        )
                    WalletSubmitResult.Success(
                        signatureHex = signatureHex,
                        walletAddress = encodeHex(result.authResult.accounts[0].publicKey),
                    )
                }
                is TransactionResult.NoWalletFound -> WalletSubmitResult.NoWalletFound
                is TransactionResult.Failure -> {
                    WalletSubmitResult.Failure(
                        walletMessage(result.message.ifBlank { result.e.message ?: "Wallet submission failed." })
                    )
                }
            }
        } catch (error: Exception) {
            WalletSubmitResult.Failure(walletMessage(error.message ?: "Wallet submission failed."))
        }
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
        return buildString {
            append("distribution-market-demo|")
            append("mu=")
            append(targetMu)
            append("|sigma=")
            append(targetSigma)
            append("|collateral=")
            append(collateral)
            append("|payload=")
            append(payload)
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
}

private fun walletMessage(message: String): String {
    return when {
        message.contains("LifecycleOwner", ignoreCase = true) ||
            message.contains("register before", ignoreCase = true) ->
            "Wallet connection is not ready. Restart the app and try again, or install/open a compatible Solana wallet."

        message.contains("ActivityNotFound", ignoreCase = true) ->
            "No compatible Solana wallet is connected or installed on this device."

        else -> message
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
