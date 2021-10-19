package nodecore.api

import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
//import nodecore.config

//private val apiConfig = config.extract<HttpApiConfig>("nodeCoreApi")
//    ?: HttpApiConfig("http://localhost:10600/api")

private val httpClient = createHttpClient()

object SyncNodeCoreApi {
    fun getInfo(): VbkInfo = runBlocking {
        httpClient.post<RpcResponse>("http://localhost:10600/api") { // TODO: config URL
            body = JsonRpcRequestBody("getinfo").toJson()
        }.handle()
    }

    fun getLastBlock(): BlockHeaderContainer = runBlocking {
        httpClient.post<RpcResponse>("http://localhost:10600/api") { // TODO: config URL
            body = JsonRpcRequestBody("getlastblock").toJson()
        }.handle()
    }

    fun getLastBitcoinBlockAtVeriBlockBlock(vbkHash: String): BtcBlockData = runBlocking {
        httpClient.post<RpcResponse>("http://localhost:10600/api") { // TODO: config URL
            body = JsonRpcRequestBody(
                "getlastbitcoinblockatveriblockblock",
                mapOf(
                    "vbkBlockHash" to vbkHash
                )
            ).toJson()
        }.handle()
    }
}
