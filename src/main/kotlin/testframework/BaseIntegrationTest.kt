package testframework

import java.io.File
import kotlinx.coroutines.*
import testframework.wrapper.apm.ApmSettings
import testframework.wrapper.apm.TestAPM
import testframework.wrapper.btcsq.BtcsqSettings
import testframework.wrapper.nodecore.NodecoreSettings
import testframework.wrapper.nodecore.TestNodecore
import org.slf4j.LoggerFactory
import testframework.wrapper.btcsq.TestBtcsq

val nodecoreVersion = System.getenv("INT_NODECORE_VERSION") ?: "0.4.13-rc.11"
val apmVersion = System.getenv("INT_APM_VERSION") ?: "0.4.13-rc.11"
val btcsqVersion = System.getenv("INT_BTCSQ_VERSION") ?: "master-47363b0"

enum class TestStatus(val state: String) {
    PASSED("PASSED"),
    FAILED("FAILED")
}

abstract class BaseIntegrationTest {
    private var willCleanup = false
    private var exitCode = 0
    private val progpowStartupTime = System.currentTimeMillis() / 1000 - 1000

    // all instances of Altchain POP miners
    val apms = ArrayList<TestAPM>() /* empty by default */
    val nodecores = ArrayList<TestNodecore>() /* empty by default */
    val btcsqs = ArrayList<TestBtcsq>() /* empty by default */
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

    var status: TestStatus = TestStatus.FAILED

    // if true, and test passed, will cleanup
    // if false, and test passed, will not cleanup
    var shouldCleanup = true

    // override this function to define test body
    abstract suspend fun runTest()

    // override this function to define network services
    abstract suspend fun setup()

    fun addNodecore(version: String = nodecoreVersion): TestNodecore {
        val ncSettings =
            NodecoreSettings(
                peerPort = baseNodecoreP2pPort++,
                rpcPort = baseNodecoreRpcPort++,
                httpPort = baseNodecoreHttpPort++,
                baseDir = baseDir,
                index = nodecores.size,
                progpowTime = progpowStartupTime,
                network = "regtest"
            )

        val nc = TestNodecore(ncSettings, version)
        nodecores.add(nc)
        logger.info("Setting up ${nc.name}:${nodecoreVersion} with network=${ncSettings.network}")
        return nc
    }

    fun addAPM(
        node: TestNodecore,
        btcaltchains: List<BtcPluginInterface> = emptyList(),
        version: String = apmVersion
    ): TestAPM {
        val apmSettings =
            ApmSettings(
                index = apms.size,
                p2pPort = baseApmP2pPort++,
                httpPort = baseApmHttpPort++,
                nodecore = node,
                baseDir = baseDir,
                btcaltchains = btcaltchains
            )

        val apm = TestAPM(apmSettings, version)
        apms.add(apm)
        logger.info(
            "Setting up ${apm.name}:${apmVersion} connected to ${node.name} and BTC plugins: ${btcaltchains.joinToString { it.name() }}"
        )
        return apm
    }

    fun addBtcsq(version: String = btcsqVersion): TestBtcsq {
        val settings = BtcsqSettings(
            p2pPort = baseBtcP2pPort++,
            rpcPort = baseBtcRpcPort++,
            zmqPort = baseBtcZmqPort++,
            index = btcsqs.size,
            baseDir = baseDir
        )

        val node = TestBtcsq(settings, version)
        btcsqs.add(node)
        logger.info("Setting up ${node.name}:${btcsqVersion}")
        return node
    }

    // entry point for every test
    suspend fun main(): Int {
        try {
            logger.info("Setting base dir ${baseDir.absolutePath}")

            setup()
            runTest()
            status = TestStatus.PASSED
            willCleanup = shouldCleanup
            exitCode = 0
        } catch (e: AssertionError) {
            logger.error("ASSERTION FAILED")
            e.printStackTrace()
            fail(e)
        } catch (e: Exception) {
            logger.error("UNHANDLED EXCEPTION")
            fail(e)
        } finally {
            shutdown()
        }

        return 0
    }

    private fun fail(reason: Exception) {
        throw RuntimeException("Test failed: \n${reason}")
    }
    private fun fail(reason: Error) {
        throw RuntimeException("Test failed: \n${reason}")
    }

    private fun shutdown() {
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
                    btcsqs.map { async { it.close() } }.awaitAll()
        }

        nodecores.clear()
        apms.clear()
        btcsqs.clear()
    }

    suspend fun syncAllApms(apms: List<TestAPM>, timeout: Long = 60_000 /*ms*/) {
        var statuses: List<Boolean> = emptyList()

        try {
            waitUntil(timeout = timeout) {
                statuses = apms.map { it.http.getMinerInfo().status.isReady }

                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil statuses.all { it }
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("syncNodecoreBlocks failed: ${statuses.joinToString { "\n" }}")
            throw e
        }
    }

    suspend fun syncAllNodecores(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        syncNodecoreBlocks(nodecores, timeout)
        syncNodecoreMempools(nodecores, timeout)
    }

    suspend fun syncNodecoreBlocks(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        var hashes: List<String> = emptyList()

        try {
            waitUntil(timeout = timeout) {
                hashes = nodecores.map { it.http.getInfo().lastBlock.hash }

                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil hashes.toSet().size == 1
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("syncNodecoreBlocks failed: ${hashes.joinToString { "\n" }}")
            throw e
        }
    }

    suspend fun syncNodecoreMempools(nodecores: List<TestNodecore>, timeout: Long = 60_000 /*ms*/) {
        var transactions: List<List<String>> = emptyList()

        try {
            waitUntil(timeout = timeout) {
                transactions =
                    nodecores.map {
                        it.http.getPendingTransactions().transactions.map { it.txId }.sorted()
                    }

                // if all getInfo returned same block,
                // then we consider syncAll succeeded
                return@waitUntil transactions.toSet().size == 1
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("syncNodecoreMempools failed: ${transactions.joinToString { "\n" }}")
            throw e
        }
    }
}
