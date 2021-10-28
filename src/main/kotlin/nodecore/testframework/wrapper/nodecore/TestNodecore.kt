package nodecore.testframework.wrapper.nodecore

import io.grpc.Deadline
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import nodecore.api.grpc.AdminGrpc
import nodecore.testframework.StdStreamLogger
import nodecore.testframework.KGenericContainer
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

class NodecoreSettings(
    val peerPort: Int,
    val rpcPort: Int,
    val httpPort: Int,
    val baseDir: File,
    val index: Int,
    val progpowTime: Long,
    val network: String = "regtest",
    val progpowHeight: Int = 0,
)

class TestNodecore(
    val settings: NodecoreSettings,
    version: String,
) : Closeable, AutoCloseable
{
    val name = "nodecore${settings.index}"
    private val logger = LoggerFactory.getLogger(name)
    val datadir = File(settings.baseDir, name)
    val stdlog = StdStreamLogger(datadir)
    val nodecoreProperties = File(datadir, "nodecore.properties")
    val rpcTimeout: Long = 30_1000 // ms

    val container = KGenericContainer("veriblock/nodecore:$version")
        .withNetworkAliases(name)
        .withNetworkMode("host")
        .withFileSystemBind(datadir.absolutePath, "/data", BindMode.READ_WRITE)

    // Accessor for Admin HTTP API
    val http = NodeHttpApi(name, "127.0.0.1", settings.httpPort)
    val rpc: AdminGrpc.AdminBlockingStub

    init {
        datadir.mkdirs()
        datadir.setReadable(true, false)
        datadir.setWritable(true, false)
        nodecoreProperties.createNewFile()
        nodecoreProperties.setReadable(true, false)
        nodecoreProperties.setWritable(true, false)

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
        container.start()
        container.followOutput(stdlog.forward())

        waitForRpcConnection()
    }

    suspend fun restart() {
        stop()
        start()
    }

    fun stop() {
        container.stop()
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
                    // repeat every 2 seconds
                    logger.debug("$name waiting for rpc availability... $e")
                    delay(2000L)
                }
            }
        }
    }

    override fun close() {
        stop()
    }

    protected fun finalize() {
        close()
    }
}
