package functional

import com.google.protobuf.ByteString
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import nodecore.api.grpc.RpcEvent
import nodecore.api.grpc.RpcLedgerProofReply
import nodecore.api.grpc.RpcLedgerProofReply.*
import nodecore.api.grpc.RpcLedgerProofRequest
import nodecore.api.grpc.utilities.ByteStringAddressUtility
import org.junit.jupiter.api.TestInstance
import testframework.*
import testframework.wrapper.nodecore.MiniNode
import kotlin.test.Test
import org.veriblock.extensions.ledger.LedgerProofWithContext
import org.veriblock.sdk.models.Address
import kotlin.test.fail

private class LedgerProofVerifier : MiniNode() {
    var reply: RpcLedgerProofReply? = null

    override suspend fun onEvent(e: RpcEvent) {
        if (e.resultsCase == RpcEvent.ResultsCase.LEDGER_PROOF_REPLY) {
            reply = e.ledgerProofReply
        }
    }
}

class LedgerProofTest : BaseIntegrationTest() {
    // exists, has VBK
    private val addr1 = randomAddress()

    // does not exist, has no VBK
    private val addr2 = randomAddress()
    private val badAddr = "Not An Address"

    private fun addr2bytes(addr: Address): ByteString {
        return ByteStringAddressUtility.createProperByteStringAutomatically(addr.toString())
    }

    override suspend fun setup() {
        val nc = addNodecore()
        nc.start()
    }

    private suspend fun ensureMiniNodeIsConnected() {
        val info = nodecores[0].http.getPeerInfo()
        if (info.connectedNodes.size != 1) {
            logger.error(info.toString())
            fail()
        }
    }

    override suspend fun runTest() {
        logger.info("Running LedgerProof test!")

        delay(5_000)
        val n = LedgerProofVerifier()
        n.connect(nodecores[0])
        ensureMiniNodeIsConnected()

        nodecores[0].http.generateBlocks(100, addr1.toString())

        ensureMiniNodeIsConnected()

        val req = RpcEvent.newBuilder()
            .setLedgerProofRequest(
                RpcLedgerProofRequest.newBuilder()
                    .addAddresses(addr2bytes(addr1))
                    .addAddresses(addr2bytes(addr2))
                    .addAddresses(ByteString.copyFrom(badAddr, "UTF-8"))
                    .build()
            )
            .build()

        n.sendEvent(req)

        ensureMiniNodeIsConnected()

        // wait until reply is received
        waitUntil(message = "Did not get reply for LedgerProofRequest") { n.reply != null }
        val reply = n.reply!!

        logger.debug(reply.toString())
        reply.proofsCount shouldBe 2
        val list = reply.proofsList

        // first must be valid addr, which has 100 blocks worth of VBK
        checkProofOfExistence(list[0])
        // second address does not exist
        checkProofOfNonExistence(list[1])
    }

    private fun checkProofOfExistence(e: LedgerProofResult) {
        e.result shouldBe Status.ADDRESS_EXISTS

        // throws if proof is invalid
        val proof = LedgerProofWithContext.parseFrom(
            e.ledgerProofWithContext
        )

        proof.ledgerAddress shouldBe addr1.toString()
    }

    private fun checkProofOfNonExistence(e: LedgerProofResult) {
        e.result shouldBe Status.ADDRESS_DOES_NOT_EXIST

        // throws if proof is invalid
        val proof = LedgerProofWithContext.parseFrom(
            e.ledgerProofWithContext
        )

        proof.ledgerAddress shouldBe addr2.toString()
    }

    @Test
    fun run(): Unit = runBlocking {
        LedgerProofTest().main()
    }
}
