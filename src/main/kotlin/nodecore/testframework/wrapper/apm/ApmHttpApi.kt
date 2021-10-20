package nodecore.testframework.wrapper.apm

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

class ApmHttpApi(val name: String, _host: String, _port: Int) {
    data class MinerInfoResponse(
        val vbkAddress: String,
        val vbkBalance: Long,
        val status: MinerStatusResponse
    )

    data class MinerStatusResponse(
        val isReady: Boolean,
        val reason: String?
    )

    private val client = HttpClient() {
        defaultRequest {
            host = _host
            port = _port
            contentType(ContentType.parse("application/json"))
        }
        Json {}
    }

    suspend fun getMinerInfo(): MinerInfoResponse {
        return GET("/api/miner")
    }

    suspend fun mine(req: MineRequest): OperationSummaryResponse {
        return POST("/api/miner/mine", req)
    }

    suspend fun getOperationsList(filter: Filter? = null): OperationSummaryListResponse {
        return GET("/api/miner/operations?${filter?.toParams()}")
    }

    suspend fun getOperation(id: String): OperationDetailResponse {
        return GET("/api/miner/operations/${id}")
    }

    suspend fun getOperationLogs(id: String, level: String = "INFO"): List<String> {
        return GET("/api/miner/operations/${id}/logs?level=${level}")
    }

    suspend fun getOperationWorkflow(id: String): OperationWorkflow {
        return GET("/api/miner/operations/${id}/workflow")
    }

    private suspend inline fun <reified T> GET(url: String): T {
        logger.debug("$name <--http-- GET $url")
        val ret = client.get<T>(url)
        logger.debug("$name --http--> $ret")
        return ret
    }

    private suspend inline fun <reified T, reified U> POST(url: String, _body: U): T {
        logger.debug("$name <--http-- POST $url")
        val ret = client.post<T>(url) {
            body = _body!!
        }
        logger.debug("$name --http--> $ret")
        return ret
    }


    companion object {
        val logger = LoggerFactory.getLogger("ApmHttpApi")
    }
}
