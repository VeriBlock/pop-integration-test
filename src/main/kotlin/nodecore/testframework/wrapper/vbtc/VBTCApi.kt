package nodecore.testframework.wrapper.vbtc

import nodecore.testframework.BaseJsonRpcApi

class VBTCApi(
    name: String,
    host: String,
    port: Int,
    username: String,
    password: String,
    timeoutMillis: Long = 0
) : BaseJsonRpcApi(
    name, host, port, "", username, password, timeoutMillis
) {

    suspend fun getBlockchainInfo(): BlockchainInfoReply = performRequest(
        method = "getblockchaininfo"
    )

    suspend fun generateToAddress(blocks: Int, address: String, attempts: Int = 2147000000): List<String> = performRequest(
        method = "generatetoaddress",
        params = listOf(blocks, address, attempts)
    )

    suspend fun getNewAddress(): String = performRequest(
        method = "getnewaddress"
    )

    suspend fun getRawPopMempool(): GetRawPopMempoolReply = performRequest(
        method = "getrawpopmempool"
    )

    suspend fun getPopParams(): GetPopParamsResponse = performRequest(
        method = "getpopparams"
    )
}
