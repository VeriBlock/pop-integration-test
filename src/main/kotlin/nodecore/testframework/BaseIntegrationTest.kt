package nodecore.testframework

import kotlinx.coroutines.*
import nodecore.testframework.wrapper.apm.ApmSettings
import nodecore.testframework.wrapper.apm.TestAPM
import nodecore.testframework.wrapper.nodecore.NodeSettings
import nodecore.testframework.wrapper.nodecore.TestNode
import nodecore.testframework.wrapper.vbtc.TestVBTC
import nodecore.testframework.wrapper.vbtc.VBtcSettings
import org.junit.ComparisonFailure
//import org.veriblock.core.utilities.createLogger
import java.io.File
import kotlin.system.exitProcess

enum class TestStatus(val state: String) {
    PASSED("PASSED"),
    FAILED("FAILED")
}

abstract class BaseIntegrationTest(
    val totalNodes: Int
) {
    private var willCleanup = false
    private var exitCode = 0
    private val progpowStartupTime = System.currentTimeMillis() / 1000 - 1000

    // all instances of Altchain POP miners
    val apms = ArrayList<TestAPM>() /* empty by default */
    val nodes = ArrayList<TestNode>() /* empty by default */
    val vbtcs = ArrayList<TestVBTC>() /* empty by default */
//    val logger = createLogger {}
    val baseNodeRpcPort = 23300
    val baseNodeP2pPort = 23200
    val baseNodeHttpPort = 23100
    val baseApmHttpPort = 24100
    val baseApmP2pPort = 24200
    val baseBtcP2pPort = 25100
    val baseBtcRpcPort = 25200
    val baseBtcZmqPort = 25300
    val baseDir: File = createTempDir(
        prefix = "veriblock_${System.currentTimeMillis()}_",
    )

    var status: TestStatus = TestStatus.FAILED
    var rpcTimeout = 60

    // if true, and test passed, will cleanup
    // if false, and test passed, will not cleanup
    var shouldCleanup = true

    // override this function to define test body
    abstract suspend fun runTest()

    /**
     * Override setupNetwork to specify network topology.
     *
     * By default, all nodes started asynchronously (in parallel), and connected as a "chain":
     * node0 <-- node1 <-- node2 <-- node3
     */
    open suspend fun setupNetwork() {
        assert(rpcTimeout > 0)

        coroutineScope {
            nodes.map {
                async { it.start() }
            }.awaitAll()
        }

        // Connect the nodes as a "chain".  This allows us
        // to split the network between nodes 1 and 2 to get
        // two halves that can work on competing chains.
        //
        // Topology looks like this:
        // node0 <-- node1 <-- node2 <-- node3

        for (i in 0 until nodes.size - 1) {
            connectNodes(nodes[i + 1], nodes[i])
        }

        syncAll(nodes)
    }

    // override this function to add nodecore with custom parameters
    open fun setupNodes() {
        for (i in 0 until totalNodes) {
            addNodecore()
        }
    }

    fun addNodecore(args: ArrayList<String> = ArrayList(), jvmArgs: ArrayList<String> = ArrayList()): TestNode {
        val ncSettings = NodeSettings(
            peerPort = getNextAvailablePort(baseNodeP2pPort),
            rpcPort = getNextAvailablePort(baseNodeRpcPort),
            httpPort = getNextAvailablePort(baseNodeHttpPort),
            baseDir = baseDir,
            index = nodes.size,
            progpowTime = progpowStartupTime,
            network = "regtest"
        )

        val nc = TestNode(ncSettings, args, jvmArgs)
        nodes.add(nc)
//        logger.info { "Setting up ${nc.name} with network=${ncSettings.network}" }
        return nc
    }

    fun addAPM(node: TestNode, btcaltchains: List<BtcPluginInterface> = emptyList(), version: String = "0.4.11-rc.1.dev.35"): TestAPM {
        val apmSettings = ApmSettings(
            index = apms.size,
            p2pPort = getNextAvailablePort(baseApmP2pPort),
            httpPort = getNextAvailablePort(baseApmHttpPort),
            nodecore = node,
            baseDir = baseDir,
            btcaltchains = btcaltchains
        )

        val apm = TestAPM(apmSettings, version)
        apms.add(apm)
//        logger.info { "Setting up ${apm.name} connected to ${node.name} and BTC plugins: ${btcaltchains.joinToString { it.name() }}" }
        return apm
    }

    fun addVBTC(version: String = "release.gamma-9e756b5"): TestVBTC {
        val settings = VBtcSettings(
            p2pPort = getNextAvailablePort(baseBtcP2pPort),
            rpcPort = getNextAvailablePort(baseBtcRpcPort),
            zmqPort = getNextAvailablePort(baseBtcZmqPort),
            index = vbtcs.size,
            baseDir = baseDir
        )

        val vbtc = TestVBTC(settings, version)
        vbtcs.add(vbtc)
//        logger.info { "Setting up ${vbtc.name}" }
        return vbtc
    }

    // entry point for every test
    suspend fun main() {
        try {
//            logger.info { "Setting base dir ${baseDir.absolutePath}" }

            Runtime.getRuntime().addShutdownHook(Thread {
                shutdown()
            })

            setup()
            runTest()
            status = TestStatus.PASSED
            willCleanup = shouldCleanup
            exitCode = 0
        } catch (e: ComparisonFailure) {
//            logger.error { "ASSERTION FAILED" }
            e.printStackTrace()
            status = TestStatus.FAILED
            exitCode = 1
        } catch (e: Exception) {
//            logger.error { "UNHANDLED EXCEPTION" }
            e.printStackTrace()
            status = TestStatus.FAILED
            exitCode = 1
        } finally {
            exitProcess(exitCode)
        }
    }

    private suspend fun setup() {
        setupNodes()
        setupNetwork()
    }

    private fun shutdown(): Int {
//        logger.info { "Test ${status.state}!" }
//        logger.info { "Logs are available in ${baseDir.absolutePath}" }
//        logger.info { "Shutting down environment..." }

        if (willCleanup) {
//            logger.info { "Cleaning up datadirs" }
            baseDir.deleteRecursively()
        } else {
//            logger.info { "Not cleaning up datadirs" }
        }

        runBlocking {
            apms.map { async { it.close() } } +
                nodes.map { async { it.close() } } +
                vbtcs.map { async { it.close() } }
                    .awaitAll()
        }

        nodes.clear()
        apms.clear()
        vbtcs.clear()

        return exitCode
    }

    suspend fun syncAll(nodes: List<TestNode>, timeout: Long = 60_000 /*ms*/) {
        var hashes: List<String> = emptyList()

        try {
            waitUntil(timeout = timeout) {
                hashes = nodes
                    .map { it.http.getInfo() }
                    .map { it.lastBlock.hash }

                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil hashes.toSet().size == 1
            }
        } catch (e: TimeoutCancellationException) {
//            logger.error { "syncAll failed: ${hashes.joinToString { "\n" }}" }
            throw e
        }
    }


}
