package nodecore.testframework.wrapper.vbtc

import kotlinx.serialization.Serializable

@Serializable
data class BlockchainInfoReply(
    val chain: String,
    val blocks: Int,
    val headers: Int,
    val bestblockhash: String,
    val initialblockdownload: Boolean
)

@Serializable
data class GetRawPopMempoolReply(
    val vbkblocks: List<String>,
    val vtbs: List<String>,
    val atvs: List<String>,
)
