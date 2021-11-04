package functional

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
import org.veriblock.core.utilities.createLogger

private class ExampleMiniNode : MiniNode() {
    var logger = createLogger {}
    override suspend fun onEvent(e: RpcEvent) {
        logger.info("Got event: ${e.resultsCase.name} from ${peerName()}")
    }
}

internal class ExampleTest : BaseIntegrationTest() {
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

        syncAllNodecores(nodecores)
    }

    override suspend fun runTest() {
        logger.info("Running EXAMPLE test!")

        val n = ExampleMiniNode()
        n.connect(nodecores[0])

        // upon start both nodes have same last block
        var info1 = nodecores[0].http.getInfo()
        var info2 = nodecores[1].http.getInfo()
        info1.lastBlock shouldBe info2.lastBlock

        // generate 5 blocks on node0
        val toGenerate = 5
        nodecores[0].http.generateBlocks(toGenerate, randomAddress().toString())

        // best blocks are different
        info1 = nodecores[0].http.getInfo()
        info2 = nodecores[1].http.getInfo()
        info1.lastBlock shouldNotBe info2.lastBlock

        // wait until nodes are synced, default timeout is 60 sec
        syncAllNodecores(nodecores, timeout = 60_000)

        // best blocks are same
        info1 = nodecores[0].http.getInfo()
        info2 = nodecores[1].http.getInfo()
        info1.lastBlock shouldBe info2.lastBlock

        n.stats["ANNOUNCE"] shouldBe 1
        n.stats["ADVERTISE_BLOCKS"] shouldBe toGenerate
        n.close()
    }

    @Test
    fun run(): Unit = runBlocking {
        ExampleTest().main()
    }
}
