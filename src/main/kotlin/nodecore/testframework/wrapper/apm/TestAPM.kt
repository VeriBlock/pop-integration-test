package nodecore.testframework.wrapper.apm

import kotlinx.coroutines.runBlocking
import nodecore.testframework.BtcPluginInterface
import nodecore.testframework.KGenericContainer
import nodecore.testframework.waitUntil
import nodecore.testframework.wrapper.nodecore.TestNode
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import java.io.Closeable
import java.io.File

data class ApmSettings(
    // if there are multiple APMs, then this is its index in a list
    val index: Int,
    val p2pPort: Int,
    val httpPort: Int,
    val nodecore: TestNode,
    val btcaltchains: List<BtcPluginInterface>,
    val baseDir: File,
)

private val logger = LoggerFactory.getLogger("TestAPM")

class TestAPM(
    val settings: ApmSettings,
    val version: String
) : AutoCloseable, Closeable {
    val name = "apm${settings.index}"
    val datadir = File(settings.baseDir, name)
    val container = KGenericContainer("veriblock/altchain-pop-miner:$version")
        .withNetworkMode("host")
        .withNetworkAliases(name)
        .withFileSystemBind(datadir.absolutePath, "/data", BindMode.READ_WRITE)
        .withCreateContainerCmdModifier { it.withTty(true) }


    val applicationConf = File(datadir, "application.conf")
    val http = ApmHttpApi(name, container.host, settings.httpPort)
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
                host: "http://127.0.0.1:${it.port()}"
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
                port: ${settings.httpPort}
              }
              connectDirectlyTo: ["127.0.0.1:${settings.nodecore.settings.peerPort}"]
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
        waitUntil(timeout = 30_000L, delay = 2_000L) {
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
