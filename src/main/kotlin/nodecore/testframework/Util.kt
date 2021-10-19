package nodecore.testframework

import com.google.protobuf.ByteString
import kotlinx.coroutines.delay
import nodecore.api.grpc.RpcEvent
import nodecore.api.grpc.utilities.ByteStringUtility
import nodecore.testframework.wrapper.apm.TestAPM
import nodecore.testframework.wrapper.nodecore.BitcoinBlockHeader
import nodecore.testframework.wrapper.nodecore.Endpoint
import nodecore.testframework.wrapper.nodecore.SubmitPopRequest
import nodecore.testframework.wrapper.nodecore.TestNode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import org.veriblock.core.utilities.Utility
import org.veriblock.core.wallet.AddressKeyGenerator
import org.veriblock.sdk.models.Address
import org.veriblock.sdk.models.VeriBlockPopTransaction
import java.net.ServerSocket
import java.security.KeyPair
import java.util.concurrent.TimeoutException


fun isPortAvailable(port: Int): Boolean {
    try {
        ServerSocket(port).use {
            return true;
        }
    } catch (e: Exception) {
        // do nothing
    }

    return false
}

fun getNextAvailablePort(basePort: Int): Int {
    var port = basePort
    while (!isPortAvailable(port++)) {
    }
    return port
}

suspend fun connectNodes(a: TestNode, b: TestNode) {
    a.http.addNode(
        listOf(
            Endpoint(
                address = "127.0.0.1",
                port = b.settings.peerPort
            )
        )
    )
}

fun randomKeypair(): KeyPair {
    return AddressKeyGenerator.generate()
}

fun randomAddress(): Address {
    val pair = randomKeypair()
    return Address.fromPublicKey(pair.public.encoded)
}

fun buildMessage(
    id: String,
    acknowledge: Boolean = false,
    buildBlock: RpcEvent.Builder.() -> Unit
): RpcEvent = RpcEvent.newBuilder()
    .setId(id)
    .setAcknowledge(acknowledge)
    .apply(buildBlock)
    .build()

// sleep until the predicate resolves to be True
suspend fun waitUntil(attempts: Long = Long.MAX_VALUE, timeout: Long = 60_000L, delay: Long = 1_000L, predicate: suspend () -> Boolean) {
    var attempt = 0
    val timeEnd = System.currentTimeMillis() + timeout
    while (attempt++ < attempts && System.currentTimeMillis() < timeEnd) {
        if (predicate()) {
            return
        }

        delay(delay)
    }

    // print the cause of failure
    val s: StringBuilder = StringBuilder().append("waitUntil failed! ")
    if (attempt >= attempts) {
        s.append("Predicate not true after $attempt attempts")
    } else if (System.currentTimeMillis() >= timeEnd) {
        s.append("Predicate not true after $timeout ms")
    }

    throw TimeoutException(s.toString())
}

// add some (10-blocks worth of payouts) balance to APM wallet
suspend fun topUpApmWallet(apm: TestAPM, blocks: Int = 10, timeout: Long = 120_000L) {
    val miner = apm.http.getMinerInfo()
    val addr = miner.vbkAddress
    val balance = miner.vbkBalance
    val node = apm.settings.nodecore
    node.http.generateBlocks(blocks, addr)

    // wait until our balance changes
    waitUntil(delay = 2_000L, timeout = timeout) {
        try {
            // if queried balance increased, then we exit the loop
            val ret = apm.http.getMinerInfo()
            return@waitUntil ret.vbkBalance > balance
        } catch (e: Exception) {
            return@waitUntil false
        }
    }
}

fun ByteArray.toHex(): String = this.toHex()
fun ByteArray.toByteString(): ByteString = ByteStringUtility.bytesToByteString(this)
fun String.toByteString(): ByteString = ByteStringUtility.bytesToByteString(this.toByteArray())

fun VeriBlockPopTransaction.toRequest(address: Address) = SubmitPopRequest(
    endorsedBlockHeader = this.publishedBlock.raw.toHex(),
    bitcoinTransaction = this.bitcoinTransaction.rawBytes.toHex(),
    bitcoinMerklePathToRoot = this.merklePath.toCompactString().toByteArray().toHex(),
    bitcoinBlockHeaderOfProof = BitcoinBlockHeader(this.blockOfProof.raw.toHex()),
    contextBitcoinBlockHeaders = this.blockOfProofContext.map { BitcoinBlockHeader(it.raw.toHex()) },
    address = address.toString()
)

// https://github.com/testcontainers/testcontainers-java/issues/318#issuecomment-290692749
open class KGenericContainer(image: String) : GenericContainer<KGenericContainer>(DockerImageName.parse(image))
