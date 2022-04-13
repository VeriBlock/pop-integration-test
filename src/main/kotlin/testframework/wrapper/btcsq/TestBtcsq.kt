package testframework.wrapper.btcsq

import kotlinx.coroutines.runBlocking
import testframework.StdStreamLogger
import testframework.BtcPluginInterface
import testframework.KGenericContainer
import testframework.waitUntil
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.Wait
import java.io.Closeable
import java.io.File

class TestBtcsq(
    val settings: BtcsqSettings,
    version: String
): BtcPluginInterface, Closeable, AutoCloseable {

    val name = "btcsq${settings.index}"
    val datadir = File(settings.baseDir, name)
    private val logger = LoggerFactory.getLogger(name)
    val stdlog = StdStreamLogger(datadir)
    val container = KGenericContainer("veriblock/btcsq:$version")
        .withNetworkAliases(name)
        .withFileSystemBind(datadir.absolutePath, "/home/btcsq/.btcsq", BindMode.READ_WRITE)
        .withCommand("btcsqd")
        .waitingFor(Wait.forLogMessage(".*tree best height =.*", 3))

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

    val conf =  File(datadir, "btcsq.conf")

    lateinit var rpc: BtcsqApi


    init {
        datadir.mkdirs()
        datadir.setReadable(true, false)
        datadir.setWritable(true, false)
        conf.createNewFile()
        conf.setReadable(true, false)
        conf.setWritable(true, false)
        conf.writeText(
            """
                ${settings.vbtcNetwork}=1
                dnsseed=0
                upnp=0
                debug=0

                fallbackfee=0.0001

                zmqpubrawblock=tcp://0.0.0.0:${settings.zmqPort}
                zmqpubhashblock=tcp://0.0.0.0:${settings.zmqPort}

                rpcworkqueue=256

                server=1
                txindex=1
                reindex=0
                listen=1
                rpcallowip=0.0.0.0/0

                rpcuser=${settings.username}
                rpcpassword=${settings.password}

                poplogverbosity=info
                debug=1
                debugexclude=leveldb
                debugexclude=libevent
                
                [regtest]
                port=${settings.p2pPort}
                rpcport=${settings.rpcPort}
                rpcbind=0.0.0.0
            """.trimIndent()
        )
    }

    suspend fun start() {
        container.start()
        container.followOutput(stdlog.forward(logger))
        logger.info("IP: ${getAddress()}")

        rpc = BtcsqApi(
            name,
            getAddress(),
            settings.rpcPort,
            settings.username,
            settings.password,
            60000
        )

        waitForRpcAvailability()
    }

    fun stop() {
        container.stop()
    }

    suspend fun restart() {
        stop()
        start()
    }

    fun isAlive(): Boolean {
        return container.isRunning
    }

    suspend fun waitForRpcAvailability() {
        waitUntil(delay=4_000L) {
            try {
                rpc.getBlockchainInfo()
                return@waitUntil true
            } catch(e: Exception) {
                logger.debug("failed... $e")
                return@waitUntil false
            }
        }
    }

    override fun name(): String = name
    override fun payoutAddress(): String {
        check(isAlive()) {
            "VBTC must be started before getting payout address"
        }

        return runBlocking { rpc.getNewAddress() }
    }
    override fun username(): String = settings.username
    override fun password(): String = settings.password
    override fun id(): Long = 0x3ae6ca26ff
    override fun host(): String = getAddress()
    override fun port(): Int = settings.rpcPort
    override fun network(): String = settings.bitcoinNetwork
    override fun payoutDelay(): Int = 150
    override fun close() {
        stop()
    }

    suspend fun mineUntilPopEnabled() {
        check(isAlive()) {
            "VBTC must be started before getting payout address"
        }
        val popActivationHeight = rpc.getPopParams().popActivationHeight
        rpc.generateToAddress(popActivationHeight, payoutAddress())
    }
}