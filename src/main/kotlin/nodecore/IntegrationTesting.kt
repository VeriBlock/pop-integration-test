
@file:JvmName("IntegrationTesting")

package nodecore

import kotlinx.coroutines.runBlocking
import nodecore.tests.testFeatureTxConfirmSend
//import org.veriblock.core.utilities.createLogger

//private val logger = createLogger {}

fun main() = runBlocking {
    //while (true) {
    //    val lastBlock = NodeCoreApi.getLastBlock()
    //    logger.info { "Last VBK block: $lastBlock" }
    //
    //    doStuff(lastBlock)
    //    delay(5_000)
    //}

    testFeatureTxConfirmSend()

    //val blocks = NodeCoreApi.getBlocksByHeight(1, listOf(757642))
    //println(blocks)
}
