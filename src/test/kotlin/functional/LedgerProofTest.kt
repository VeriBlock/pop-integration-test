package functional

import com.google.protobuf.ByteString
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import nodecore.api.grpc.RpcEvent
import nodecore.api.grpc.RpcLedgerProofReply
import nodecore.api.grpc.RpcLedgerProofReply.*
import nodecore.api.grpc.RpcLedgerProofRequest
import nodecore.api.grpc.utilities.ByteStringAddressUtility
import nodecore.testframework.*
import nodecore.testframework.wrapper.nodecore.MiniNode
import kotlin.test.Test
import org.veriblock.extensions.ledger.LedgerProofWithContext
import org.veriblock.sdk.models.Address

class LedgerProofVerifier : MiniNode() {
    var reply: RpcLedgerProofReply? = null

    override suspend fun onEvent(e: RpcEvent) {
        if (e.resultsCase == RpcEvent.ResultsCase.LEDGER_PROOF_REPLY) {
            reply = e.ledgerProofReply
        }
    }
}

class LedgerProofTest : BaseIntegrationTest() {
    // exists, has VBK
    val addr1 = randomAddress()

    // does not exist, has no VBK
    val addr2 = randomAddress()
    val badAddr = "Not An Address"

    fun addr2bytes(addr: Address): ByteString {
        return ByteStringAddressUtility.createProperByteStringAutomatically(addr.toString())
    }

    override suspend fun setup() {
        val nc = addNodecore()
        nc.start()
    }

    override suspend fun runTest() {
        logger.info("Running LedgerProof test!")

        val n = LedgerProofVerifier()
        n.connect(nodecores[0])

        nodecores[0].http.generateBlocks(100, addr1.toString())

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
        // wait until reply is received
        waitUntil { n.reply != null }
        val reply = n.reply!!

        logger.debug(reply.toString())

        reply.proofsCount shouldBe 3
        val list = reply.proofsList

        // first must be valid addr, which has 100 blocks worth of VBK
        checkProofOfExistence(list[0])
        // second address does not exist
        checkProofOfNonExistence(list[1])
        // third address is invalid
        checkInvalidAddr(list[2])
    }

    fun checkProofOfExistence(e: LedgerProofResult) {
        e.result shouldBe Status.ADDRESS_EXISTS

        // throws is proof is invalid
        val proof = LedgerProofWithContext.parseFrom(
            e.ledgerProofWithContext
        )

        proof.ledgerAddress shouldBe addr1.toString()
    }

    fun checkProofOfNonExistence(e: LedgerProofResult) {
        e.result shouldBe Status.ADDRESS_DOES_NOT_EXIST

        // throws if proof is invalid
        val proof = LedgerProofWithContext.parseFrom(
            e.ledgerProofWithContext
        )

        proof.ledgerAddress shouldBe addr2.toString()
    }

    fun checkInvalidAddr(e: LedgerProofResult) {
        e.address.toString("UTF-8") shouldBe badAddr
        e.result shouldBe Status.ADDRESS_IS_INVALID
    }

    @Test
    fun run() = runBlocking {
        LedgerProofTest().main()
    }
}
