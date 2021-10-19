package nodecore.testframework.wrapper.nodecore

import kotlinx.serialization.Serializable
import nodecore.api.VbkBlockData

@Serializable
data class Result(
    val error: Boolean,
    val code: String,
    val message: String,
    val details: String
) {
    override fun toString(): String {
        return "[$code] $message: $details"
    }
}

@Serializable
data class ProtocolReply(
    val success: Boolean,
    val results: List<Result>
)

@Serializable
data class VbkInfoAddress (
    val address: String
)

@Serializable
data class VbkInfo(
    val lastBlock: VbkBlockData,
    val defaultAddress: VbkInfoAddress
)

@Serializable
data class NodeRequest(
    val endpoint: List<Endpoint>
)

@Serializable
data class Endpoint(
    val address: String,
    val port: Int
)

@Serializable
data class GenerateBlocksReply(
    val result: Result?,
    val hash: List<String>
)

@Serializable
data class BlockHeader(
    val header: String,
    val hash: String
)

@Serializable
data class GetLastBlockReply(
    val header: BlockHeader
)

@Serializable
data class GetLastBitcoinBlockReply(
    val header: String,
    val height: Int,
    val hash: String
)

@Serializable
data class BitcoinBlockHeader(
    val header: String
)

@Serializable
data class SubmitPopRequest(
    val endorsedBlockHeader: String,
    val bitcoinTransaction: String,
    val bitcoinMerklePathToRoot: String,
    val bitcoinBlockHeaderOfProof: BitcoinBlockHeader,
    val contextBitcoinBlockHeaders: List<BitcoinBlockHeader>,
    val address: String
)
