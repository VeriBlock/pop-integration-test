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

@Serializable
data class GetPopParamsResponse(
    val popActivationHeight: Int,
    val popRewardPercentage: Int,
    val popRewardCoefficient: Int,
    val popPayoutDelay: Int,
    val bootstrapBlock: GenericBlock,
    val vbkBootstrap: BlockAndNetwork,
    val btcBootstrap: BlockAndNetwork,
    val networkId: Int,
    val maxVbkBlocksInAltBlock: Int,
    val maxVTBsInAltBlock: Int,
    val endorsementSettlementInterval: Int,
    val finalityDelay: Int,
    val keystoneInterval: Int,
    val maxAltchainFutureBlockTime: Int,
    val maxReorgDistance: Int,
)

@Serializable
data class GenericBlock(
    val hash: String,
    val prevhash: String,
    val height: Int,
)

@Serializable
data class BlockAndNetwork(
    val block: GenericBlock,
    val network: String,
)
