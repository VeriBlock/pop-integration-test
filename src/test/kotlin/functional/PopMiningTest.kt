package functional

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import nodecore.testframework.*
import nodecore.testframework.wrapper.apm.MineRequest
import nodecore.testframework.wrapper.nodecore.TestNodecore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlin.test.Test
import org.nodecore.vpmmock.mockmining.VeriBlockPopMinerMock
import org.veriblock.core.Context
import org.veriblock.core.params.defaultRegTestProgPoWParameters
import org.veriblock.core.utilities.Utility
import org.veriblock.core.utilities.extensions.asHexBytes
import org.veriblock.sdk.models.Address
import org.veriblock.sdk.services.SerializeDeserializeService
import java.security.Security

class PopMiningTest : BaseIntegrationTest() {
    init {
        // for VPM
        Context.set(defaultRegTestProgPoWParameters)
        Security.addProvider(BouncyCastleProvider())
    }

    val vpm = VeriBlockPopMinerMock()
    var keypair = randomKeypair()

    suspend fun endorseVbkTip(node: TestNodecore, btcBlocksNumber: Int = 3, address: Address) {
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

        val vbtc = addVBTC()
        vbtc.start()
        vbtc.mineUntilPopEnabled()

        val apm = addAPM(nodecores[0], listOf(vbtc))
        apm.start()
    }

    override suspend fun runTest() {
        logger.info("Running PopMiningTest test!")

        logger.info("Generating 10 vBTC blocks")
        val vbtcAddr = vbtcs[0].rpc.getNewAddress()
        vbtcs[0].rpc.generateToAddress(10, vbtcAddr)

        logger.info("Sending VBK to APM address ${apms[0].vbkAddress}")
        topUpApmWallet(apms[0], blocks = 10)

        // get nodecore default address
        val ncAddress = Address(nodecores[0].http.getInfo().defaultAddress.address)

        logger.info("Generating VTBs...")
        val TOTAL_VTBS = 10
        for (i in 1..TOTAL_VTBS) {
            nodecores[0].http.generateBlocks(1, ncAddress.toString())
            endorseVbkTip(nodecores[0], address = ncAddress)
        }

        syncAllApms(apms)
        val operation = apms[0].http.mine(MineRequest(chainSymbol = vbtcs[0].name, 210)) // TODO: height = popActvationHeight + mined vbtc blocks

        logger.info("waiting until APM submits endorsement TX")
        waitUntil(delay = 5000L) {
            try {
                val op = apms[0].http.getOperation(operation.operationId)
                op.state == "Endorsement Transaction submitted"
            } catch (e: Exception) {
                logger.error("Got error during http call... ${e.message}")
                false
            }
        }

        // generate containing block
        logger.info("waiting until ATV is confirmed in VeriBlock...")
        waitUntil(timeout = 240_000L, delay = 5000L) {
            nodecores[0].http.generateBlocks(1, ncAddress.toString())
            syncAllApms(apms)
            val op = apms[0].http.getOperation(operation.operationId)
            return@waitUntil op.state == "VBK Publications submitted"
        }

        val lastBlockHeight = nodecores[0].http.getInfo().lastBlock.number;

        logger.info("waiting until APM sends all lacking VBK context blocks, $TOTAL_VTBS VTBs and 1 ATV")
        waitUntil(timeout = 120_000L, delay = 5000L) {
            val popmp = vbtcs[0].rpc.getRawPopMempool()
            // total number of VBK blocks in mempool must be `lastBlockHeight - 1`
            popmp.vbkblocks.size != lastBlockHeight - 1 /* genesis */ && popmp.vtbs.size >= TOTAL_VTBS &&  popmp.atvs.isNotEmpty()
        }

        vbtcs[0].rpc.generateToAddress(1, address = vbtcAddr)[0]

        // all pop-payloads mined, mempool is empty
        val popmp = vbtcs[0].rpc.getRawPopMempool()
        popmp.atvs.size shouldBe 0
        popmp.vtbs.size shouldBe 0
        popmp.vbkblocks.size shouldBe 0
    }

    @Test
    fun run(): Unit = runBlocking {
        PopMiningTest().main()
    }
}
