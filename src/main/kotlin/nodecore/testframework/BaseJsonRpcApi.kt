package nodecore.testframework

import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import nodecore.api.*
//import org.veriblock.core.utilities.createLogger
import java.util.*

open class BaseJsonRpcApi(
    val name: String,
    host: String,
    port: Int,
    suffix: String = "",
    username: String = "",
    password: String = ""
) {
    protected val apiConfig = HttpApiConfig("http://${host}:${port}/${suffix}")
    protected val httpClient = createHttpClient(HttpAuthConfig(username, password))

    protected suspend inline fun <reified T> performRequest(
        method: String,
        params: Any? = Collections.EMPTY_MAP
    ): T = httpClient.post<RpcResponse>() {
        val jsonBody = JsonRpcRequestBody(
            method = method, params = params
        ).toJson()
//        logger.debug { "$name <--jsonrpc-- $jsonBody" }
        val response = httpClient.post<RpcResponse>(apiConfig.url) { body = jsonBody }
//        logger.debug { "$name --jsonrpc--> $response" }
        return response.handle()
    }.handle()

    companion object {
//        val logger = createLogger { }
    }
}
