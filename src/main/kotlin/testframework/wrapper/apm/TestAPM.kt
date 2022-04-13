package testframework.wrapper.apm

import kotlinx.coroutines.runBlocking
import testframework.StdStreamLogger
import testframework.BtcPluginInterface
import testframework.KGenericContainer
import testframework.waitUntil
import testframework.wrapper.nodecore.TestNodecore
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.Wait
import java.io.Closeable
import java.io.File

data class ApmSettings(
    // if there are multiple APMs, then this is its index in a list
    val index: Int,
    val p2pPort: Int,
    val httpPort: Int,
    val nodecore: TestNodecore,
    val btcaltchains: List<BtcPluginInterface>,
    val baseDir: File,
)


class TestAPM(
    val settings: ApmSettings,
    version: String
) : AutoCloseable, Closeable {
    val name = "apm${settings.index}"
    private val logger = LoggerFactory.getLogger(name)
    val datadir = File(settings.baseDir, name)
    val stdlog = StdStreamLogger(datadir)
    val container = KGenericContainer("veriblock/altchain-pop-miner:$version")
        .withNetworkAliases(name)
        .withFileSystemBind(datadir.absolutePath, "/data", BindMode.READ_WRITE)
        .withCreateContainerCmdModifier { it.withTty(true) }
        .withEnv("APM_LOG_LEVEL", "DEBUG")
        .withEnv("APM_CONSOLE_LOG_LEVEL", "INFO")
        .waitingFor(Wait.forLogMessage(".*Starting miner.*", 1))


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

    val applicationConf = File(datadir, "application.conf")
    lateinit var http: ApmHttpApi
    val vbkAddress by lazy {
        runBlocking {
            http.getMinerInfo().vbkAddress
        }
    }

    init {
        val sisettings = settings.btcaltchains.joinToString(separator = "\n") {
            """
              ${it.name()}: {
                payoutAddress: "${it.payoutAddress()}"
                pluginKey: btc
                id: ${it.id()}
                name: "${it.name()}"
                host: "http://${it.host()}:${it.port()}"
                auth: {
                    username: "${it.username()}"
                    password: "${it.password()}"
                }
                network: "${it.network()}"
                payoutDelay: ${it.payoutDelay()}
              }
            """
        }

        datadir.mkdirs()
        datadir.setWritable(true, false)
        datadir.setReadable(true, false)
        applicationConf.createNewFile()
        applicationConf.setReadable(true, false)
        applicationConf.setWritable(true, false)
        applicationConf.writeText(
            """
            miner {
              feePerByte: 1000
              maxFee: 10000000
              api {
                host: 0.0.0.0
                port: ${settings.httpPort}
              }
              connectDirectlyTo: ["${settings.nodecore.getAddress()}:${settings.nodecore.settings.peerPort}"]
              network: ${settings.nodecore.settings.network}
              dataDir: /data
              progPowGenesis: true
            }
            
            securityInheriting {
              $sisettings
            }
        """.trimIndent()
        )
    }

    suspend fun start() {
        container.start()
        container.followOutput(stdlog.forward(logger, true))
        logger.info("IP: ${getAddress()}")
        http = ApmHttpApi(name, getAddress(), settings.httpPort)

        waitForHttpApiAvailability()
    }

    fun stop() {
        container.stop()
    }

    suspend fun restart() {
        stop()
        start()
    }

    private suspend fun waitForHttpApiAvailability() {
        waitUntil(timeout = 60_000L, delay = 5_000L) {
            try {
                http.getMinerInfo()
                return@waitUntil true
            } catch (e: Exception) {
                logger.debug("failed... $e")
                return@waitUntil false
            }
        }
    }

    override fun close() {
        stop()
    }
}
