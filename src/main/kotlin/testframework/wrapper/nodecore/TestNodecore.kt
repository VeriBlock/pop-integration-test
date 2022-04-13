package testframework.wrapper.nodecore

import io.grpc.Deadline
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import nodecore.api.grpc.AdminGrpc
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategy
import testframework.KGenericContainer
import testframework.StdStreamLogger
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
) : Closeable, AutoCloseable {
    val name = "nodecore${settings.index}"
    private val logger = LoggerFactory.getLogger(name)
    val datadir = File(settings.baseDir, name)
    val stdlog = StdStreamLogger(datadir)
    val nodecoreProperties = File(datadir, "nodecore.properties")
    val rpcTimeout: Long = 30_1000 // ms

    val container = KGenericContainer("veriblock/nodecore:$version")
        .withNetworkAliases(name)
        .withFileSystemBind(datadir.absolutePath, "/data", BindMode.READ_WRITE)
        .withEnv("NODECORE_LOG_LEVEL", "DEBUG")
        .withEnv("NODECORE_CONSOLE_LOG_LEVEL", "DEBUG")
        .waitingFor(Wait.forLogMessage(".*NodeCore operating state is now: Running.*", 1))

    fun getAddress(): String {
        // we can take IP only on running containers
        assert(container.isRunning)
        return container
            .containerInfo
            .networkSettings
            .networks
            .entries
            .first()
            .value
            .ipAddress!!
    }

    // Accessor for Admin HTTP API
    lateinit var http: NodeHttpApi
    lateinit var rpc: AdminGrpc.AdminBlockingStub
    private lateinit var channel: ManagedChannel

    init {
        datadir.mkdirs()
        datadir.setReadable(true, false)
        datadir.setWritable(true, false)
        nodecoreProperties.createNewFile()
        nodecoreProperties.setReadable(true, false)
        nodecoreProperties.setWritable(true, false)

        // write nodecode.properties
        nodecoreProperties
            .writeText(
                """
                bfi.enabled=false
                network=${settings.network}
                peer.bootstrap.enabled=false
                peer.bind.address=0.0.0.0
                peer.bind.port=${settings.peerPort}
                peer.share.platform=true
                peer.share.myAddress=true
                rpc.bind.address=0.0.0.0
                rpc.bind.port=${settings.rpcPort}
                http.api.bind.address=0.0.0.0
                http.api.bind.port=${settings.httpPort}
                regtest.progpow.height=${settings.progpowHeight}
                regtest.progpow.start.time=${settings.progpowTime}
            """.trimIndent()
            )
    }

    suspend fun start() {
        container.start()
        container.followOutput(stdlog.forward(logger))
        logger.info("IP: ${getAddress()}")

        channel = ManagedChannelBuilder
            .forAddress(getAddress(), settings.rpcPort)
            .usePlaintext()
            .build()

        // setup RPC channel
        rpc = AdminGrpc
            .newBlockingStub(channel)
            .withMaxInboundMessageSize(20 * 1024 * 1024)
            .withMaxOutboundMessageSize(20 * 1024 * 1024)
            .withDeadline(Deadline.after(rpcTimeout, TimeUnit.MILLISECONDS))

        http = NodeHttpApi(name, getAddress(), settings.httpPort)

        waitForRpcConnection()
    }

    suspend fun restart() {
        stop()
        start()
    }

    fun stop() {
        // don't forget to shutdown channel
        if (!channel.isShutdown) {
            channel.shutdownNow()
        }
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
