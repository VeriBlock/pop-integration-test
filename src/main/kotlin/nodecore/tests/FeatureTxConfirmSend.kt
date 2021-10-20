package nodecore.tests

import kotlinx.coroutines.delay
import nodecore.api.FaucetApi
import nodecore.api.NodeCoreApi
import nodecore.api.Output
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong

private val logger = LoggerFactory.getLogger("")

suspend fun testFeatureTxConfirmSend() {
    NodeCoreApi.checkConnection()
    NodeCoreApi.checkSyncStatus()

    logger.info("Generating a new address to use for receiving coins")
    val newAddressReply = NodeCoreApi.getNewAddress()
    logger.debug("getnewaddress reply: $newAddressReply")
    check(newAddressReply.success) {
        "Failed to get a new address from wallet"
    }

    val address = newAddressReply.address
    logger.info("Requesting 10 tVBK from faucet for $address")

    val faucetResponse = FaucetApi.getCoins(address)
    logger.debug("faucet reply: $faucetResponse")
    val txId = faucetResponse.txIds.first()

    // Get transaction
    while (true) {
        try {
            logger.info("Checking that our transaction has at least 1 confirmation...")
            val transactions = NodeCoreApi.getTransaction(txId)
            logger.debug("gettransactions reply: $transactions")
            val confirmations = transactions.transactions!!.first().confirmations
            check(confirmations >= 1)
            break
        } catch (e: Exception) {
            delay(30_000)
        }
    }

    // Get new address
    logger.info("Generating a second address to use for the send/receive test")
    val newAddressReply2 = NodeCoreApi.getNewAddress()
    logger.debug("getnewaddress reply: $newAddressReply")
    check(newAddressReply2.success) {
        "Failed to get a new address from wallet"
    }

    val address2 = newAddressReply2.address
    val info = NodeCoreApi.getInfo()
    val startingTip = info.lastBlock.number
    logger.info("Current Tip: $startingTip")

    // Create 10 transactions quickly
    val txIds = mutableListOf<String>()
    val source = address
    val dest = address2
    val amount = 0.1.toAtomic()
    while (txIds.size < 30) {
        val sendResponse = NodeCoreApi.sendCoins(source, listOf(Output(dest, amount)))
        logger.debug("sendcoins reply: $sendResponse")
        check(sendResponse.success) {
            "Failed to send coins"
        }
        txIds += sendResponse.txIds.first()
        delay(2_000)
    }

    logger.debug("Created transactions: ${txIds.joinToString()}}")

    logger.info("Wait until block ${startingTip + 6}")
    NodeCoreApi.waitUntilBlock(startingTip + 6)

    logger.info("Proceeding to look up transactions")
    val blockTxIds = mutableListOf<String>()
    repeat(6) {
        try {
            val height = startingTip + it
            logger.info("Starting block: $startingTip, checking block: $height")
            val blocksReply = NodeCoreApi.getBlocksByHeight(1, listOf(height))
            val transactions = blocksReply.blocks.first().regularTransactions
            logger.debug(transactions.joinToString())
            logger.info("${transactions.size} regular transactions found")
            for (transaction in transactions) {
                val blockTxId = transaction.signed!!.transaction.txId
                logger.debug("Transaction found in block: $blockTxId")
                if (blockTxId !in blockTxIds) {
                    blockTxIds += blockTxId
                }
            }
            logger.debug(blockTxIds.joinToString())
        } catch (e: Exception) {
            delay(10_000)
        }
    }

    logger.debug("Created Transactions: $txIds")
    logger.debug("Confirmed Transactions: $blockTxIds")

    val exist = txIds.intersect(blockTxIds)
    val doNotExist = txIds - blockTxIds

    logger.info("${exist.size} Confirmed Transactions:")
    for (tx in exist) {
        logger.info(tx)
    }

    logger.info("${doNotExist.size} Missing Transactions:")
    for (tx in doNotExist) {
        logger.info("\t$tx")
    }

    // Get pending transactions
    val pendingTransactionsResponse = NodeCoreApi.getPendingTransactions()
    logger.debug("Pending transactions: $pendingTransactionsResponse")
    val pendingTransactions = pendingTransactionsResponse.transactions.map {
        logger.debug("\t${it.txId}")
        it.txId
    }

    val stuckTransactions = txIds.intersect(pendingTransactions)
    logger.info("${stuckTransactions.size} Stuck Transactions:")
    for (tx in stuckTransactions) {
        logger.info("\t$tx")
    }
}

fun Long.fromAtomic() = this / 100000000.0
fun Double.toAtomic() = (this * 100000000).roundToLong()
