package testframework.wrapper.nodecore

import testframework.BaseJsonRpcApi
import testframework.toRequest
import org.veriblock.sdk.models.Address
import org.veriblock.sdk.models.VeriBlockPopTransaction

// TODO: add all functions of HTTP API here
class NodeHttpApi(
    name: String,
    host: String,
    port: Int
) : BaseJsonRpcApi(
    name = name,
    host = host,
    port = port,
    suffix = "api"
) {
    suspend fun getInfo(): VbkInfo = performRequest(
        method = "getinfo"
    )

    suspend fun getStateInfo(): VbkInfo = performRequest(
        method = "getstateinfo"
    )

    suspend fun addNode(e: List<Endpoint>): ProtocolReply = performRequest(
        method = "addnode",
        params = NodeRequest(e)
    )

    suspend fun removeNode(e: List<Endpoint>): ProtocolReply = performRequest(
        method = "removenode",
        params = NodeRequest(e)
    )

    suspend fun generateBlocks(number: Int, address: String): GenerateBlocksReply = performRequest(
        method = "generateblocks",
        params = mapOf(
            "blocks" to number,
            "address" to address
        )
    )

    suspend fun getLastBlock(): GetLastBlockReply = performRequest(
        method = "getlastblock"
    )

    suspend fun getLastBitcoinBlock(): GetLastBitcoinBlockReply = performRequest(
        method = "getlastbitcoinblock"
    )

    suspend fun submitPop(req: SubmitPopRequest): ProtocolReply = performRequest(
        method = "submitpop",
        params = req
    )
    
    suspend fun submitPop(poptx: VeriBlockPopTransaction, address: Address): ProtocolReply = performRequest(
        method = "submitpop",
        params = poptx.toRequest(address)
    )
    
    suspend fun sendCoins(req: SendCoinsRequest): SendCoinsReply = performRequest(
        method = "sendcoins",
        params = req
    )
    
    suspend fun getPendingTransactions(): GetPendingTransactionsReply = performRequest(
        method = "getpendingtransactions"
    )
}
