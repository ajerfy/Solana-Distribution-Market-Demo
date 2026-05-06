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
        quote: DemoPreset,
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
                    val transaction = buildMemoTransaction(walletAddress, buildMemoText(quote))
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
                        result.e.message ?: "Wallet submission failed."
                    )
                }
            }
        } catch (error: Exception) {
            WalletSubmitResult.Failure(error.message ?: "Wallet submission failed.")
        }
    }

    private fun buildMemoText(quote: DemoPreset): String {
        return buildString {
            append("distribution-market-demo|")
            append("mu=")
            append(quote.targetMuDisplay)
            append("|sigma=")
            append(quote.targetSigmaDisplay)
            append("|collateral=")
            append(quote.collateralRequiredDisplay)
            append("|payload=")
            append(quote.serializedInstructionHex)
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

private fun encodeHex(bytes: ByteArray): String {
    val hex = StringBuilder(bytes.size * 2)
    bytes.forEach { byte ->
        hex.append(((byte.toInt() ushr 4) and 0x0f).toString(16))
        hex.append((byte.toInt() and 0x0f).toString(16))
    }
    return hex.toString()
}
