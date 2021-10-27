package functional

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import nodecore.testframework.BaseIntegrationTest
import nodecore.testframework.BtcPluginInterface
import kotlin.test.Test

internal class ExampleApmTest : BaseIntegrationTest() {
    override suspend fun runTest() {
        logger.info("Running ExampleApmTest test!")

        val res = apms[0].http.getMinerInfo()
        logger.info("$res")
    }

    override suspend fun setup() = coroutineScope {
        val nodecore = addNodecore()
        nodecore.start()

        val vbtc = addVBTC()
        vbtc.start()

        val apm = addAPM(nodecore, List<BtcPluginInterface>(1){vbtc})
        apm.start()
    }

    @Test
    fun run(): Unit = runBlocking {
        ExampleApmTest().main()
    }
}
