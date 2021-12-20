package testframework.wrapper.nodecore

import kotlinx.serialization.Serializable
import nodecore.api.grpc.RpcNodeInfo

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
data class NodeInfo(
    val address: String = "",
    val application: String = "MiniNode",
    val platform: String = "e2eTest",
    val startTimestamp: Int = 1552064237,
    val id: String = "Test",
    val port: Int = 12345,
    // mainnet, regtest, alphanet == 3
    // testnet, testnet_progpow == 2
    val protocolVersion: Int = 3,
    val share: Boolean = false,
    val capabilities: Long = 0
) {
    fun toProto(): RpcNodeInfo {
        return RpcNodeInfo.newBuilder()
            .setApplication(application)
            .setPlatform(platform)
            .setStartTimestamp(startTimestamp)
            .setId(id)
            .setPort(port)
            .setShare(share)
            .setProtocolVersion(protocolVersion)
            .setCapabilities(capabilities)
            .build()
    }
}

@Serializable
data class NodeHeight(
    val peer: String,
    val height: Int
)

@Serializable
data class GetPeerInfoReply(
    val success: Boolean,
    val results: List<Result>,
    val endpoints: List<Endpoint>,
    val connectedNodes: List<NodeInfo>,
    val disconnectedNodes: List<NodeInfo>,
    val candidateNodes: List<NodeInfo>,
    val nodeHeights: List<NodeHeight>
)

@Serializable
data class VbkInfoAddress(
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

@Serializable
data class SendCoinsRequest(
    val amounts: List<Output>,
    val sourceAddress: String? = null,
    val takeFeeFromOutputs: Boolean = false
)

@Serializable
data class SendCoinsReply(
    val success: Boolean,
    val results: List<Result>,
    val txIds: List<String>
)

@Serializable
data class GetPendingTransactionsReply(
    val success: Boolean,
    val results: List<Result>,
    val transactions: List<Transaction>
)


@Serializable
data class BlockHeaderContainer(
    val header: BlockHeader
)

@Serializable
data class VbkBlockData(
    val hash: String,
    val number: Int
)

@Serializable
data class BtcBlockData(
    val hash: String,
    val height: Int,
    val header: String
)

@Serializable
data class BlockchainStateInfo(
    val state: State
) {
    enum class State {
        LOADING,
        NORMAL,
        PAUSED,
        STALE,
        LOADED,
    }
}

@Serializable
data class OperatingStateInfo(
    val state: State
) {
    enum class State {
        STARTED,
        INITIALIZING,
        RUNNING,
        TERMINATING,
    }
}

@Serializable
data class NetworkStateInfo(
    val state: State
) {
    enum class State {
        DISCONNECTED,
        CONNECTED,
    }
}

@Serializable
data class StateInfo(
    val blockchainState: BlockchainStateInfo,
    val operatingState: OperatingStateInfo,
    val networkState: NetworkStateInfo,
    val connectedPeerCount: Int,
    val currentSyncPeer: String,
    val networkHeight: Int,
    val localBlockchainHeight: Int,
    val success: Boolean,
    val results: List<Result>,
    val networkVersion: String,
    val dataDirectory: String,
    val programVersion: String,
    val nodecoreStarttime: Long,
    val walletCacheSyncHeight: Int,
    val walletState: WalletState
) {
    enum class WalletState {
        DEFAULT,
        LOCKED,
        UNLOCKED,
    }
}

@Serializable
data class GetBlocksReply(
    val success: Boolean,
    val results: List<Result>,
    val blocks: List<Block>
)

@Serializable
data class BlockFeeTable(
    val popFeeShare: Long
)

@Serializable
data class BlockContentMetapackage(
    //val coinbaseTransaction: CoinbaseTransaction,
    //val popDatastore: PoPDatastore,
    val minerComment: String,
    val ledgerHash: String,
    val extraNonce: Long,
    val hash: String,
    val blockFeeTable: BlockFeeTable
)

@Serializable
data class Block(
    val number: Int,
    val timestamp: Int,
    val hash: String,
    val previousHash: String,
    val secondPreviousHash: String,
    val thirdPreviousHash: String,
    val encodedDifficulty: Int,
    val winningNonce: Int,
    val regularTransactions: List<TransactionUnion>,
    val popTransactions: List<TransactionUnion>,
    val totalFees: Long,
    val powCoinbaseReward: Long,
    val popCoinbaseReward: Long,
    val bitcoinBlockHeaders: List<String>,
    val blockContentMetapackage: BlockContentMetapackage,
    val size: Int,
    val version: Int,
    val merkleRoot: String
)

@Serializable
data class GetNewAddressReply(
    val success: Boolean,
    val results: List<Result>,
    val address: String,
    val additionalAddresses: List<String>
)

@Serializable
data class GetTransactionsReply(
    val success: Boolean,
    val results: List<Result>?,
    val transactions: List<TransactionInfo>?
)

@Serializable
data class Output(
    val address: String,
    val amount: Long
)

@Serializable
data class SignedTransaction(
    val signature: String,
    val publicKey: String,
    val signatureIndex: Long,
    val transaction: Transaction
)

@Serializable
data class MultisigSlot(
    val populated: Boolean,
    val signature: String,
    val publicKey: String,
    val ownerAddress: String
)

@Serializable
data class MultisigBundle(
    val slots: List<MultisigSlot>
)

@Serializable
data class SignedMultisigTransaction(
    val signatureBundle: MultisigBundle,
    val transaction: Transaction,
    val signatureIndex: Long
)

@Serializable
data class TransactionUnion(
    //oneof transaction {
    val unsigned: Transaction? = null,
    val signed: SignedTransaction? = null,
    val signedMultisig: SignedMultisigTransaction? = null
    //}
)

@Serializable
data class Transaction(
    val type: Type,
    val sourceAddress: String,
    val sourceAmount: Long,
    val outputs: List<Output>,
    val transactionFee: Long,
    val data: String,
    val bitcoinTransaction: String,
    val endorsedBlockHeader: String,
    val bitcoinBlockHeaderOfProof: BitcoinBlockHeader,
    val merklePath: String,
    val contextBitcoinBlockHeaders: List<BitcoinBlockHeader>,
    val timestamp: Int,
    val size: Int,
    val txId: String
) {
    enum class Type {
        ZEROUNUSED,
        STANDARD,
        PROOFOFPROOF,
        MULTISIG,
    }
}

@Serializable
data class TransactionInfo(
    val confirmations: Int,
    val transaction: Transaction,
    val blockNumber: Int,
    val timestamp: Int,
    val endorsedBlockHash: String,
    val bitcoinBlockHash: String,
    val bitcoinTxId: String,
    val bitcoinConfirmations: Int
)

