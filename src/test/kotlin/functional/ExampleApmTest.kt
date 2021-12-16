package functional

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestInstance
import testframework.BaseIntegrationTest
import testframework.BtcPluginInterface
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ExampleApmTest : BaseIntegrationTest() {
    override suspend fun runTest() {
        logger.info("Running ExampleApmTest test!")

        val res = apms[0].http.getMinerInfo()
        logger.info("$res")
    }

    override suspend fun setup() = coroutineScope {
        val nodecore = addNodecore()
        nodecore.start()

        val vbtc = addBtcsq()
        vbtc.start()

        val apm = addAPM(nodecore, listOf(vbtc))
        apm.start()
    }

    @Test
    fun run(): Unit = runBlocking {
        ExampleApmTest().main()
    }
}
