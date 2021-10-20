package functional

import kotlinx.coroutines.runBlocking
import nodecore.testframework.BaseIntegrationTest
import kotlin.test.Test

class ExampleApmTest : BaseIntegrationTest(1) {
    override suspend fun runTest() {
        logger.info("Running ExampleApmTest test!")
        val apm = addAPM(nodes[0])
        apm.start()

        val res = apm.http.getMinerInfo()
        logger.info("$res")
    }

    @Test
    fun run() = runBlocking {
        ExampleApmTest().main()
    }
}
