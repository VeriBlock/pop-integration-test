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
import java.io.File
import kotlin.system.exitProcess

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
    val baseNodecoreRpcPort = 23300
    val baseNodecoreP2pPort = 23200
    val baseNodecoreHttpPort = 23100
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

    // override this function to define network services
    abstract suspend fun setup()

    fun addNodecore(version: String = "0.4.13-rc.2.dev.2"): TestNodecore {
        val ncSettings = NodecoreSettings(
            peerPort = getNextAvailablePort(baseNodecoreP2pPort),
            rpcPort = getNextAvailablePort(baseNodecoreRpcPort),
            httpPort = getNextAvailablePort(baseNodecoreHttpPort),
            baseDir = baseDir,
            index = nodecores.size,
            progpowTime = progpowStartupTime,
            network = "regtest"
        )

        val nc = TestNodecore(ncSettings, version)
        nodecores.add(nc)
        logger.info("Setting up ${nc.name} with network=${ncSettings.network}")
        return nc
    }

    fun addAPM(node: TestNodecore, btcaltchains: List<BtcPluginInterface> = emptyList(), version: String = "0.4.13-rc.2.dev.2"): TestAPM {
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
        logger.info("Setting up ${apm.name} connected to ${node.name} and BTC plugins: ${btcaltchains.joinToString { it.name() }}")
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

    suspend fun syncAll(nodes: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
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
            logger.error("syncAll failed: ${hashes.joinToString { "\n" }}")
            throw e
        }
    }


}
