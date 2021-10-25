package nodecore.testframework

import kotlinx.coroutines.*
import nodecore.testframework.wrapper.apm.ApmSettings
import nodecore.testframework.wrapper.apm.TestAPM
import nodecore.testframework.wrapper.nodecore.NodecoreSettings
import nodecore.testframework.wrapper.nodecore.TestNodecore
import nodecore.testframework.wrapper.vbtc.TestVBTC
import nodecore.testframework.wrapper.vbtc.VBtcSettings
import org.junit.ComparisonFailure
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Network
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import nodecore.api.grpc.utilities.extensions.toHex

enum class TestStatus(val state: String) {
    PASSED("PASSED"),
    FAILED("FAILED")
}

abstract class BaseIntegrationTest() {
    private var willCleanup = false
    private var exitCode = 0
    private val progpowStartupTime = System.currentTimeMillis() / 1000 - 1000

    // all instances of Altchain POP miners
    val apms = ArrayList<TestAPM>() /* empty by default */
    val nodecores = ArrayList<TestNodecore>() /* empty by default */
    val vbtcs = ArrayList<TestVBTC>() /* empty by default */
    val logger = LoggerFactory.getLogger("BaseIntegrationTest")
    var baseNodecoreRpcPort = (23300)
    var baseNodecoreP2pPort = (23200)
    var baseNodecoreHttpPort = (23100)
    var baseApmHttpPort = (24100)
    var baseApmP2pPort = (24200)
    var baseBtcP2pPort = (25100)
    var baseBtcRpcPort = (25200)
    var baseBtcZmqPort = (25300)
    val baseDir: File = createTempDir(
        prefix = "veriblock_${System.currentTimeMillis()}_",
    )
    val network: Network = Network.newNetwork()

    var status: TestStatus = TestStatus.FAILED
    var rpcTimeout = 60

    // if true, and test passed, will cleanup
    // if false, and test passed, will not cleanup
    var shouldCleanup = true

    // override this function to define test body
    abstract suspend fun runTest()

    // override this function to define network services
    abstract suspend fun setup()

    fun addNodecore(version: String = "0.4.13-rc.5"): TestNodecore {
        val ncSettings = NodecoreSettings(
            peerPort = baseNodecoreP2pPort++,
            rpcPort = baseNodecoreRpcPort++,
            httpPort = baseNodecoreHttpPort++,
            baseDir = baseDir,
            index = nodecores.size,
            progpowTime = progpowStartupTime,
            network = "regtest"
        )

        val nc = TestNodecore(ncSettings, version, network)
        nodecores.add(nc)
        logger.info("Setting up ${nc.name} with network=${ncSettings.network}")
        return nc
    }

    fun addAPM(node: TestNodecore, btcaltchains: List<BtcPluginInterface> = emptyList(), version: String = "0.4.13-rc.2.dev.2"): TestAPM {
        val apmSettings = ApmSettings(
            index = apms.size,
            p2pPort = baseApmP2pPort++,
            httpPort = baseApmHttpPort++,
            nodecore = node,
            baseDir = baseDir,
            btcaltchains = btcaltchains
        )

        val apm = TestAPM(apmSettings, version)
        apms.add(apm)
        logger.info("Setting up ${apm.name} connected to ${node.name} and BTC plugins: ${btcaltchains.joinToString { it.name() }}")
        return apm
    }

    fun addVBTC(version: String = "refs.pull.585.merge-d72afa0"): TestVBTC {
        val settings = VBtcSettings(
            p2pPort = baseBtcP2pPort++,
            rpcPort = baseBtcRpcPort++,
            zmqPort = baseBtcZmqPort++,
            index = vbtcs.size,
            baseDir = baseDir
        )

        val vbtc = TestVBTC(settings, version)
        vbtcs.add(vbtc)
        logger.info("Setting up ${vbtc.name}")
        return vbtc
    }

    // entry point for every test
    suspend fun main() {
        try {
            logger.info("Setting base dir ${baseDir.absolutePath}")

            Runtime.getRuntime().addShutdownHook(Thread {
                shutdown()
            })

            setup()
            runTest()
            status = TestStatus.PASSED
            willCleanup = shouldCleanup
            exitCode = 0
        } catch (e: ComparisonFailure) {
            logger.error("ASSERTION FAILED")
            e.printStackTrace()
            status = TestStatus.FAILED
            exitCode = 1
        } catch (e: Exception) {
            logger.error("UNHANDLED EXCEPTION")
            e.printStackTrace()
            status = TestStatus.FAILED
            exitCode = 1
        } finally {
            exitProcess(exitCode)
        }
    }

    private fun shutdown(): Int {
        logger.info("Test ${status.state}!")
        logger.info("Logs are available in ${baseDir.absolutePath}")
        logger.info("Shutting down environment...")

        if (willCleanup) {
            logger.info("Cleaning up datadirs")
            baseDir.deleteRecursively()
        } else {
            logger.info("Not cleaning up datadirs")
        }

        runBlocking {
            apms.map { async { it.close() } } +
                nodecores.map { async { it.close() } } +
                vbtcs.map { async { it.close() } }
                    .awaitAll()
        }

        nodecores.clear()
        apms.clear()
        vbtcs.clear()

        return exitCode
    }
    
    suspend fun syncAll(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        syncBlocks(nodecores, timeout)
        syncMempools(nodecores, timeout)
    }
    
    suspend fun syncBlocks(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        var hashes: List<String> = emptyList()
        
        try {
            waitUntil(timeout = timeout) {
                hashes = nodecores
                    .map { it.http.getInfo() }
                    .map { it.lastBlock.hash }
                
                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil hashes.toSet().size == 1
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("syncBlocks failed: ${hashes.joinToString { "\n" }}")
            throw e
        }
    }
    
    suspend fun syncMempools(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        var transactions: List<List<String>> = emptyList()
        
        try {
            waitUntil(timeout = timeout) {
                transactions = nodecores
                    .map { it.http.getPendingTransactions() }
                    .map { tx ->
                        tx.transactions.map { it.txId }
                    }
                
                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil transactions.toSet().size == 1
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("syncMempools failed: ${transactions.joinToString { "\n" }}")
            throw e
        }
    }
}
