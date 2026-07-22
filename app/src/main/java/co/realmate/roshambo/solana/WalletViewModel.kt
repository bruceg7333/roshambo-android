package co.realmate.roshambo.solana

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Handles Solana wallet login via Mobile Wallet Adapter (devnet). */
class WalletViewModel : ViewModel() {

    private val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://roshambo.app"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Roshambo"
        )
    ).apply { blockchain = Solana.Devnet }

    var address by mutableStateOf<String?>(null); private set
    var connecting by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var balanceSol by mutableStateOf<Double?>(null); private set
    var skrBalance by mutableStateOf<Double?>(null); private set
    var publishing by mutableStateOf(false); private set
    var lastSignature by mutableStateOf<String?>(null); private set

    private var authToken: String? = null
    private var publicKeyBytes: ByteArray? = null
    private val rpcUrl = "https://api.devnet.solana.com"
    // Devnet test-SKR mint (stand-in for mainnet SKR in this demo).
    private val skrMint = "ntyHnfdteyrWoLgNqJgJpVz3ZboT8GFnBLoVFmfq6dF"

    val shortAddress: String?
        get() = address?.let { if (it.length > 8) "${it.take(4)}…${it.takeLast(4)}" else it }

    fun connect(sender: ActivityResultSender) {
        if (connecting) return
        connecting = true
        error = null
        viewModelScope.launch {
            try {
                when (val result = walletAdapter.connect(sender)) {
                    is TransactionResult.Success -> {
                        val account = result.authResult.accounts.firstOrNull()
                        if (account != null) {
                            publicKeyBytes = account.publicKey
                            address = Base58.encode(account.publicKey)
                            authToken = result.authResult.authToken
                            android.util.Log.i("RoshamboWallet", "Connected: $address")
                            refreshBalance()
                        } else {
                            error = "No account returned"
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        error = "No compatible wallet found"
                        android.util.Log.e("RoshamboWallet", "NoWalletFound")
                    }
                    is TransactionResult.Failure -> {
                        error = result.e.message ?: "Connection failed"
                        android.util.Log.e("RoshamboWallet", "Failure", result.e)
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Error"
                android.util.Log.e("RoshamboWallet", "Exception", e)
            }
            connecting = false
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            runCatching { walletAdapter.disconnect(sender) }
            address = null
            authToken = null
            publicKeyBytes = null
            balanceSol = null
            skrBalance = null
            lastSignature = null
        }
    }

    fun refreshBalance() {
        val addr = address ?: return
        viewModelScope.launch {
            balanceSol = fetchBalance(addr)
            skrBalance = fetchSkrBalance(addr)
        }
    }

    private suspend fun fetchSkrBalance(addr: String): Double? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true; connectTimeout = 10_000; readTimeout = 10_000
            }
            val body = """{"jsonrpc":"2.0","id":1,"method":"getTokenAccountsByOwner",""" +
                """"params":["$addr",{"mint":"$skrMint"},{"encoding":"jsonParsed"}]}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val accounts = JSONObject(response).getJSONObject("result").getJSONArray("value")
            if (accounts.length() == 0) return@withContext 0.0
            accounts.getJSONObject(0)
                .getJSONObject("account").getJSONObject("data").getJSONObject("parsed")
                .getJSONObject("info").getJSONObject("tokenAmount").getDouble("uiAmount")
        } catch (e: Exception) {
            android.util.Log.e("RoshamboWallet", "skr balance error", e)
            null
        }
    }

    /** Publish a score memo on-chain: build a Memo tx, have the wallet sign+send. */
    fun publishScore(sender: ActivityResultSender, memoText: String) {
        val pk = publicKeyBytes ?: run { error = "Connect a wallet first"; return }
        if (publishing) return
        publishing = true
        error = null
        viewModelScope.launch {
            try {
                val blockhash = fetchLatestBlockhash() ?: run {
                    error = "Could not fetch blockhash"; publishing = false; return@launch
                }
                val tx = MemoTransaction.build(pk, Base58.decode(blockhash), memoText.toByteArray())
                when (val result = walletAdapter.transact(sender) {
                    signAndSendTransactions(arrayOf(tx))
                }) {
                    is TransactionResult.Success -> {
                        val sig = result.payload?.signatures?.firstOrNull()
                        lastSignature = sig?.let { Base58.encode(it) }
                        android.util.Log.i("RoshamboWallet", "Published: $lastSignature")
                        refreshBalance()
                    }
                    is TransactionResult.NoWalletFound -> error = "No compatible wallet found"
                    is TransactionResult.Failure -> error = result.e.message ?: "Publish failed"
                }
            } catch (e: Exception) {
                error = e.message ?: "Publish error"
                android.util.Log.e("RoshamboWallet", "publish error", e)
            }
            publishing = false
        }
    }

    private suspend fun fetchLatestBlockhash(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response).getJSONObject("result").getJSONObject("value").getString("blockhash")
        } catch (e: Exception) {
            android.util.Log.e("RoshamboWallet", "blockhash error", e)
            null
        }
    }

    private suspend fun fetchBalance(addr: String): Double? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = """{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$addr"]}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val lamports = JSONObject(response).getJSONObject("result").getLong("value")
            lamports / 1_000_000_000.0
        } catch (e: Exception) {
            android.util.Log.e("RoshamboWallet", "balance error", e)
            null
        }
    }
}
