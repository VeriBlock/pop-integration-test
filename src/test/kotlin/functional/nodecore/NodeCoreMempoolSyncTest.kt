package functional

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import nodecore.api.grpc.RpcEvent
import nodecore.testframework.BaseIntegrationTest
import nodecore.testframework.wrapper.nodecore.MiniNode
import nodecore.testframework.connectNodes
import nodecore.testframework.randomAddress
import kotlin.test.Test
import nodecore.api.Output
import nodecore.api.SendCoinsRequest
import nodecore.api.grpc.RpcOutput
import nodecore.api.grpc.RpcSendCoinsRequest
import nodecore.api.grpc.utilities.extensions.asHexByteString
import nodecore.api.grpc.utilities.extensions.toHex
import nodecore.testframework.toByteString
import org.veriblock.core.utilities.createLogger

class NodeCoreMempoolSyncTest : BaseIntegrationTest() {
    override suspend fun setup() = coroutineScope {
        addNodecore()
        addNodecore()

        // before nodes started, you can append arbitrary values to nodecore.properties
        nodecores[0].nodecoreProperties.appendText("# a comment")
        // do not overwrite existing settings!

        // start nodes in parallel
        nodecores.map {
            async { it.start() }
        }.awaitAll()

        // Connect the nodes as a "chain".  This allows us
        // to split the network between nodes 1 and 2 to get
        // two halves that can work on competing chains.
        //
        // Topology looks like this:
        // node0 <-- node1 <-- node2 <-- node3

        for (i in 0 until nodecores.size - 1) {
            connectNodes(nodecores[i + 1], nodecores[i])
        }

        syncAll(nodecores)
    }

    override suspend fun runTest() {
        logger.info("Running Mempool sync test!")

        // upon start both nodes have same last block
        var info1 = nodecores[0].http.getInfo()
        var info2 = nodecores[1].http.getInfo()
        info1.lastBlock shouldBe info2.lastBlock
        
        val srcAddress = info1.defaultAddress.address
        val dstAddress = info2.defaultAddress.address

        // generate 5 blocks on node0
        val toGenerate = 5
        nodecores[0].http.generateBlocks(toGenerate, srcAddress)

        // best blocks are different
        info1 = nodecores[0].http.getInfo()
        info2 = nodecores[1].http.getInfo()
        info1.lastBlock shouldNotBe info2.lastBlock

        // wait until nodes are synced, default timeout is 60 sec
        syncAll(nodecores, timeout = 60_000)

        // send coins
        val sendCoinsReply = nodecores[0].http.sendCoins(
            SendCoinsRequest(listOf(
                Output(
                    amount = 100_000L,
                    address = dstAddress
                )
            ))
        )
        sendCoinsReply.txIds.shouldNotBeEmpty()
        
        val txId = sendCoinsReply.txIds.first()
        
        nodecores[0].http.getPendingTransactions().transactions[0].txId shouldBe txId
    
        // Add a new node and connect it to the network. It should get the pending transactions too
        addNodecore().start()
        connectNodes(nodecores[1], nodecores[2])
        
        syncAll(nodecores)
    }

    @Test
    fun run() = runBlocking {
        NodeCoreMempoolSyncTest().main()
    }
}
