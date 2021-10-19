package nodecore.testframework.wrapper.nodecore

import io.grpc.Deadline
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import nodecore.api.grpc.AdminGrpc
import nodecore.testframework.ProcessManager
import nodecore.testframework.buildNodecoreProcess
import java.io.File
import java.util.concurrent.TimeUnit

class NodeSettings(
    val peerPort: Int,
    val rpcPort: Int,
    val httpPort: Int,
    val baseDir: File,
    val index: Int,
    val progpowTime: Long,
    val network: String = "regtest",
    val progpowHeight: Int = 0,
)

class TestNode(
    val settings: NodeSettings,
    args: ArrayList<String> = ArrayList(),
    jvmArgs: ArrayList<String> = ArrayList(),
) : ProcessManager(
    name = "node${settings.index}",
    datadir = File(settings.baseDir, "node${settings.index}"), { datadir ->
    // specify datadir
    args.add("-d")
    args.add(datadir.absolutePath)
    buildNodecoreProcess(args, jvmArgs)
}) {
    val nodecoreProperties = File(datadir, "nodecore.properties")
    val rpcTimeout: Long = 30_1000 // ms

    // Accessor for Admin HTTP API
    val http = NodeHttpApi(name, "127.0.0.1", settings.httpPort)
    val rpc: AdminGrpc.AdminBlockingStub

    init {
        // setup RPC channel
        rpc = AdminGrpc
            .newBlockingStub(
                ManagedChannelBuilder
                    .forAddress("127.0.0.1", settings.rpcPort)
                    .usePlaintext()
                    .build()
            )
            .withMaxInboundMessageSize(20 * 1024 * 1024)
            .withMaxOutboundMessageSize(20 * 1024 * 1024)
            .withDeadline(Deadline.after(rpcTimeout, TimeUnit.MILLISECONDS))

        // write nodecode.properties
        nodecoreProperties
            .writeText(
                """
                network=${settings.network}
                peer.bootstrap.enabled=false
                peer.bind.address=127.0.0.1
                peer.bind.port=${settings.peerPort}
                peer.share.platform=true
                peer.share.myAddress=true
                rpc.bind.address=127.0.0.1
                rpc.bind.port=${settings.rpcPort}
                http.api.bind.address=127.0.0.1
                http.api.bind.port=${settings.httpPort}
                regtest.progpow.height=${settings.progpowHeight}
                regtest.progpow.start.time=${settings.progpowTime}
            """.trimIndent()
            )
    }

    suspend fun start() {
        this.start { waitForRpcConnection() }
    }

    suspend fun restart() {
        this.restart { waitForRpcConnection() }
    }

    private suspend fun waitForRpcConnection() {
        withTimeout(rpcTimeout) {
            while (true) {
                try {
                    http.getInfo()
                    // success! we were able to get a response
                    return@withTimeout
                } catch (e: Exception) {
                    // no connection yet
                    // repeat every half a second
//                    logger.debug { "$name waiting for rpc availability... $e" }
                    delay(2000L)
                }
            }
        }
    }
}
