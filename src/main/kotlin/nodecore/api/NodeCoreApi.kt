package nodecore.api

import io.ktor.client.request.post
import kotlinx.coroutines.delay
//import nodecore.config
//import org.veriblock.core.utilities.createLogger
import java.util.Collections.EMPTY_MAP
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

//private val apiConfig = config.extract<HttpApiConfig>("nodeCoreApi")
//    ?: HttpApiConfig("http://localhost:10600/api")

private val httpClient = createHttpClient()

//private val logger = createLogger {}

object NodeCoreApi {
    suspend fun getInfo(): VbkInfo = performRequest(
        method = "getinfo"
    )

    suspend fun getStateInfo(): StateInfo = performRequest(
        method = "getstateinfo"
    )

    suspend fun getLastBlock(): BlockHeaderContainer = performRequest(
        method = "getlastblock"
    )

    suspend fun getLastBitcoinBlockAtVeriBlockBlock(vbkHash: String): BtcBlockData = performRequest(
        method = "getlastbitcoinblockatveriblockblock",
        params = mapOf(
            "vbkBlockHash" to vbkHash
        )
    )

    suspend fun getNewAddress(count: Int = 1): GetNewAddressReply = performRequest(
        method = "getnewaddress",
        params = mapOf(
            "count" to count
        )
    )

    suspend fun getBlocksByHeight(searchLength: Int, heights: List<Int>): GetBlocksReply = performRequest(
        method = "getblocks",
        params = mapOf(
            "searchLength" to searchLength,
            "filters" to heights.map {
                mapOf("index" to it)
            }
        )
    )

    suspend fun getBlocksByHash(searchLength: Int, hashes: List<String>): GetBlocksReply = performRequest(
        method = "getblocks",
        params = mapOf(
            "searchLength" to searchLength,
            "filters" to listOf(hashes.map {
                mapOf("hash" to it)
            })
        )
    )

    suspend fun getTransaction(txId: String): GetTransactionsReply = performRequest(
        method = "gettransactions",
        params = mapOf(
            "searchLength" to 0,
            "ids" to listOf(txId)
        )
    )

    suspend fun sendCoins(sourceAddress: String, amounts: List<Output>): SendCoinsReply = performRequest(
        method = "sendcoins",
        params = mapOf(
            "sourceAddress" to sourceAddress,
            "amounts" to amounts
        )
    )

    suspend fun getPendingTransactions(): GetPendingTransactionsReply = performRequest(
        method = "getpendingtransactions"
    )

    suspend fun checkConnection() {
        while (true) {
            try {
                getInfo()
                break
            } catch (e: Exception) {
//                logger.warn { "NodeCore not available yet, trying again in 10s..." }
                delay(10_000L)
            }
        }
    }

    suspend fun checkSyncStatus() {
        var previousState = 0
        var previousSpeed = 0.0
        while (true) {
            try {
                val stateInfo = getStateInfo()
                val syncState = stateInfo.networkHeight - stateInfo.localBlockchainHeight
                if (syncState.absoluteValue >= 5) {
                    val syncSummary = if (previousState > 0) {
                        val increment = previousState - syncState
                        val speed = increment / 5.0
                        val fixedSpeed = if (previousSpeed > 0) {
                            (speed + previousSpeed) / 2.0
                        } else {
                            speed
                        }
                        previousSpeed = fixedSpeed
                        val remainingTimeSeconds = (syncState / fixedSpeed).roundToInt()
                        val remainingTime = if (remainingTimeSeconds >= 60) {
                            "${remainingTimeSeconds / 60}m ${remainingTimeSeconds % 60}s"
                        } else {
                            "${remainingTimeSeconds}s"
                        }
                        " Download Speed=${String.format("%.2f", fixedSpeed)}Bk/s Remaining Time=$remainingTime"
                    } else {
                        ""
                    }
//                    logger.warn { "Waiting for NodeCore to synchronize. $syncState blocks left (LocalHeight=${stateInfo.localBlockchainHeight} NetworkHeight=${stateInfo.networkHeight}$syncSummary)" }
                    previousState = syncState

                    delay(5_000L)
                    continue
                }

//                logger.info("NodeCore is synchronized.. continuing.")
                break
            } catch (e: Exception) {
//                logger.warn { "NodeCore not available, trying again in 5s..." }
                delay(5_000L)
            }
        }
    }

    suspend fun waitUntilBlock(blockHeight: Int) {
        while (true) {
            val info = getInfo()
            val tip = info.lastBlock.number
//            logger.info { "Current Tip: $tip" }
            if (tip >= blockHeight) {
                return
            }
            delay(30_0000)
        }
    }
}

private suspend inline fun <reified T> performRequest(
    method: String,
    params: Any? = EMPTY_MAP
): T = httpClient.post<RpcResponse>("http://localhost:10600/api") { // TODO: config URL
    body = JsonRpcRequestBody(method, params).toJson()
}.handle()
