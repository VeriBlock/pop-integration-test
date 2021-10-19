package functional

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nodecore.contracts.BlockHeader
import nodecore.testframework.BaseIntegrationTest
import nodecore.testframework.randomKeypair
import nodecore.testframework.topUpApmWallet
import nodecore.testframework.waitUntil
import nodecore.testframework.wrapper.apm.MineRequest
import nodecore.testframework.wrapper.nodecore.TestNode
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import org.nodecore.vpmmock.mockmining.VeriBlockPopMinerMock
import org.veriblock.core.Context
import org.veriblock.core.params.defaultRegTestProgPoWParameters
import org.veriblock.core.utilities.Utility
import org.veriblock.core.utilities.debugError
import org.veriblock.core.utilities.extensions.asHexBytes
import org.veriblock.sdk.models.Address
import org.veriblock.sdk.services.SerializeDeserializeService
import java.security.Security

class PopMiningTest : BaseIntegrationTest(1) {
    init {
        // for VPM
        Context.set(defaultRegTestProgPoWParameters)
        Security.addProvider(BouncyCastleProvider())
    }

    val vpm = VeriBlockPopMinerMock()
    var keypair = randomKeypair()

    suspend fun endorseVbkTip(node: TestNode, btcBlocksNumber: Int = 3, address: Address) {
        // get header to endorse
        val lastBlock =node.http.getLastBlock().header.header.asHexBytes()
        val headerToEndorse = SerializeDeserializeService.parseVeriBlockBlock(lastBlock)
        // create endorsing btc tx and add it to BTC mempool
        val endorsingBtcTx = vpm.createBtcTx(SerializeDeserializeService.parseVeriBlockBlock(headerToEndorse.raw), address)
        vpm.bitcoinMempool.add(endorsingBtcTx)
        // mine it in btc blockchain
        val blockOfProof = vpm.mineBtcBlocks(1)!!
        if (btcBlocksNumber - 1 > 0) {
            // add context blocks if any
            vpm.mineBtcBlocks(btcBlocksNumber - 1)
        }

        // get last known btc block
        val lastKnownBtcBlockReply = node.http.getLastBitcoinBlock()
        val lastKnownBtcBlock = SerializeDeserializeService.parseBitcoinBlock(Utility.hexToBytes(lastKnownBtcBlockReply.header))
        val poptx = vpm.createVbkPopTx(blockOfProof.hash, 0, keypair, lastKnownBtcBlock)!!

        // send POP TX to node
        val reply = node.http.submitPop(poptx, address)
        reply.success shouldBe true
    }

    override suspend fun runTest() {
        logger.info { "Running PopMiningTest test!" }
        val vbtc = addVBTC()
        vbtc.start()

        logger.info { "Generating 10 vBTC blocks" }
        val vbtcAddr = vbtc.rpc.getNewAddress()
        vbtc.rpc.generateToAddress(10, vbtcAddr)

        val apm = addAPM(nodes[0], listOf(vbtc))
        apm.start()

        logger.info { "Sending VBK to APM address ${apm.vbkAddress}" }
        topUpApmWallet(apm, blocks = 10)

        // get nodecore default address
        val ncAddress = Address(nodes[0].http.getInfo().defaultAddress.address)

        logger.info { "Generating VTBs..." }
        val TOTAL_VTBS = 10
        for (i in 1..TOTAL_VTBS) {
            nodes[0].http.generateBlocks(1, ncAddress.toString())
            endorseVbkTip(nodes[0], address = ncAddress)
        }

        val operation = apm.http.mine(MineRequest(chainSymbol = vbtc.name, 10))

        logger.info { "waiting until APM submits endorsement TX" }
        waitUntil(delay = 5000L) {
            try {
                val op = apm.http.getOperation(operation.operationId)
                op.state == "Endorsement Transaction submitted"
            } catch (e: Exception) {
                logger.debugError(e) { "Got error during http call..." }
                false
            }
        }

        // generate containing block
        logger.info { "waiting until ATV is confirmed in VeriBlock..." }
        waitUntil(delay = 5000L) {
            nodes[0].http.generateBlocks(1, ncAddress.toString())
            val op = apm.http.getOperation(operation.operationId)
            return@waitUntil op.task == "Confirm ATV"
        }

        val lastBlockHeight = nodes[0].http.getInfo().lastBlock.number;

        logger.info { "waiting until APM sends all lacking VBK context blocks, $TOTAL_VTBS VTBs and 1 ATV" }
        waitUntil(delay = 5000L) {
            val popmp = vbtc.rpc.getRawPopMempool()
            // total number of VBK blocks in mempool must be `lastBlockHeight - 1`
            popmp.vbkblocks.size != lastBlockHeight - 1 /* genesis */ && popmp.vtbs.size >= TOTAL_VTBS &&  popmp.atvs.isNotEmpty()
        }

        val containingHash = vbtc.rpc.generateToAddress(1, address = vbtcAddr)[0]

        // all pop-payloads mined, mempool is empty
        val popmp = vbtc.rpc.getRawPopMempool()
        popmp.atvs.size shouldBe 0
        popmp.vtbs.size shouldBe 0
        popmp.vbkblocks.size shouldBe 0
    }

    @Test
    fun run() = runBlocking {
        PopMiningTest().main()
    }
}
