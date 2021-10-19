package nodecore.api

import io.ktor.client.request.get
import io.ktor.client.request.parameter

//private val apiConfig = config.extract<HttpApiConfig>("faucetApi")
//    ?: HttpApiConfig("http://95.217.67.120/alt-integration/api/v1.0/faucet")

private val httpClient = createHttpClient()

object FaucetApi {
    suspend fun getCoins(address: String): FaucetResponse = httpClient.get<RpcResponse>("http://95.217.67.120/alt-integration/api/v1.0/faucet") { // TODO: config URL
        parameter("address", address)
    }.handle()
}

data class FaucetResponse(
    val success: Boolean,
    val txIds: List<String>
)
