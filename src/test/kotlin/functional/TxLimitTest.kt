package functional

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import testframework.BaseIntegrationTest
import testframework.connectNodes
import testframework.topUpApmWallet
import testframework.waitUntil
import testframework.wrapper.apm.MineRequest
import kotlin.test.Test

class TxLimitTest : BaseIntegrationTest() {

    class NoErrorException(message: String): Exception(message)

    override suspend fun setup() = coroutineScope {
        addNodecore()
        addNodecore()

        // start nodes in parallel
        nodecores.map {
            async { it.start() }
        }.awaitAll()

        for (i in 0 until nodecores.size - 1) {
            connectNodes(nodecores[i + 1], nodecores[i])
        }

        val vbtc = addBtcsq()
        vbtc.start()
        vbtc.mineUntilPopEnabled()

        val apm = addAPM(nodecores[0], listOf(vbtc))
        apm.start()
    }

    override suspend fun runTest() {
        logger.info("Running TxLimitTest test!")

        logger.info("Sending VBK to APM address ${apms[0].vbkAddress}")
        topUpApmWallet(apms[0], blocks = 10)

        val TX_LIMIT = 900
        logger.info("Create ${TX_LIMIT} transactions")
        for (i in 1..TX_LIMIT) {
            apms[0].http.mine(MineRequest(chainSymbol = btcsqs[0].name, 200))
        }

        waitUntil {
            val op = apms[0].http.getOperationsList()
            op.totalCount shouldBe TX_LIMIT
            op.operations.all { it.state == "Endorsement Transaction submitted" }
        }

        logger.info("Create 901 transaction")
        try {
            apms[0].http.mine(MineRequest(chainSymbol = btcsqs[0].name, 200))
            throw NoErrorException("Error was not returned")
        } catch(e: NoErrorException) {
            logger.error(e.message)
            throw e
        } catch (e: Exception) {
            logger.info("Catch an exception: ${e.message}")
        }
    }

    @Test
    fun run(): Unit = runBlocking {
        TxLimitTest().main()
    }
}