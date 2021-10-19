package nodecore.testframework.wrapper.apm

import kotlinx.serialization.Serializable

@Serializable
data class Filter(
    val status: String? = null,
    val limit: Int? = null,
    val offset: Long? = null,
) {
    fun toParams(): String {
        val params = ArrayList<String>()
        if(status != null) {
            params.add("status=${status}")
        }
        if(limit != null) {
            params.add("limit=${limit}")
        }
        if(offset != null) {
            params.add("offset=${offset}")
        }

        return params.joinToString { "&" }
    }
}

@Serializable
data class OperationSummaryResponse(
    val operationId: String,
    val chain: String,
    val endorsedBlockHeight: Int?,
    val state: String,
    val task: String
)

@Serializable
data class OperationSummaryListResponse(
    val operations: List<OperationSummaryResponse>,
    val totalCount: Int
)

@Serializable
data class MineRequest(
    val chainSymbol: String,
    val height: Int? = null
)

@Serializable
data class OperationDetailResponse(
    val operationId: String,
    val chain: String,
    val endorsedBlockHeight: Int?,
    val state: String,
    val task: String,
    val stateDetail: Map<String, String>
)

@Serializable
data class OperationWorkflow(
    val operationId: String,
    val stages: List<OperationWorkflowStage>
)

@Serializable
data class OperationWorkflowStage(
    val status: String,
    val taskName: String,
    val extraInformation: String
)

@Serializable
data class ConfiguredAltchainList(
    val altchains: List<ConfiguredAltchain>
)

@Serializable
data class ConfiguredAltchain(
    val id: Long,
    val key: String,
    val name: String,
    val payoutDelay: Int
)
