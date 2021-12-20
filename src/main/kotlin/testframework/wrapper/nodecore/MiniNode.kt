package testframework.wrapper.nodecore

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import nodecore.api.grpc.RpcAnnounce
import nodecore.api.grpc.RpcEvent
import nodecore.api.grpc.RpcNodeInfo
import testframework.buildMessage
import org.slf4j.LoggerFactory
import testframework.waitUntil
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger("MiniNode")

private val PEER_INPUT_POOL: ExecutorService = Executors.newFixedThreadPool(
    8,
    ThreadFactoryBuilder()
        .setNameFormat("peer-input-%d")
        .build()
)
private val coroutineDispatcher = PEER_INPUT_POOL.asCoroutineDispatcher()
private val coroutineScope = CoroutineScope(coroutineDispatcher)
private val selectorManager = ActorSelectorManager(coroutineDispatcher)

private class PeerSocket(
    val peer: TestNodecore,
    val socket: Socket,
    val onMsg: suspend (ByteArray) -> Unit,
) : Closeable, AutoCloseable {
    private val running = AtomicBoolean(false)
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel()
    private lateinit var inputJob: Job
    private lateinit var outputJob: Job
    private val writeQueue: Channel<RpcEvent> = Channel(1100)

    // formatter for node
    val peerName by lazy {
        "node${peer.settings.index}"
    }

    init {
        start()
    }

    fun write(message: RpcEvent, failOnError: Boolean = true) {
        logger.debug("$peerName <--p2p-- ${message.resultsCase.name}")
        try {
            if (!writeQueue.offer(message)) {
                logger.warn(
                    "Not writing event ${message.resultsCase.name} to peer $peerName because write queue is full."
                )
                if (failOnError) {
                    throw RuntimeException("$peerName: Write queue is full")
                }
            }
        } catch (e: InterruptedException) {
            logger.warn("Output stream thread shutting down for peer $peerName")
        }
    }

    fun isRunning(): Boolean {
        return running.get()
    }

    fun start() {
        running.set(true)
        inputJob = coroutineScope.launch { runInput() }
        outputJob = coroutineScope.launch { runOutput() }
        inputJob.invokeOnCompletion { close() }
        outputJob.invokeOnCompletion { close() }
    }

    @Synchronized
    override fun close() {
        if (isRunning()) {
            running.set(false)
            coroutineScope.cancel()
            writeQueue.close()
            if (!socket.isClosed) {
                try {
                    socket.close()
                } catch (e: IOException) {
                    logger.warn("[${peerName}] Exception closing socket", e)
                }
            }
        }
    }

    suspend fun runOutput() {
        while (isRunning()) {
            try {
                val event = writeQueue.receive()
                val message = event.toByteArray()
                writeChannel.writeInt(message.size)
                writeChannel.writeFully(message, 0, message.size)
                writeChannel.flush()
            } catch (e: InterruptedException) {
                logger.debug("[${peerName}] Output stream thread shutting down - Interrupted")
                break
            } catch (e: CancellationException) {
                logger.debug("[${peerName}] Output stream thread shutting down - Cancelled")
                break
            } catch (e: SocketException) {
                logger.debug("[${peerName}] Socket closed")
                break
            } catch (e: Exception) {
                logger.warn("[${peerName}] Error in output stream thread!", e)
                break
            }
        }
    }

    private suspend fun runInput() {
        while (isRunning()) {
            try {
                val nextMessageSize = readChannel.readInt()
                val raw = ByteArray(nextMessageSize)
                readChannel.readFully(raw, 0, nextMessageSize)
                onMsg(raw)
            } catch (e: SocketException) {
                logger.debug("[${peerName}] Attempted to read from a socket that has been closed.")
                break
            } catch (e: EOFException) {
                logger.debug("[$peerName] Disconnected from peer - EOF.")
                break
            } catch (e: ClosedReceiveChannelException) {
                logger.debug("[$peerName] Disconnected from peer - Closed.")
                break
            } catch (e: CancellationException) {
                logger.debug("[${peerName}] Input stream thread shutting down")
                break
            } catch (e: Exception) {
                logger.warn("[${peerName}] Socket error: ", e)
                break
            }
        }
    }
}


open class MiniNode : Closeable, AutoCloseable {
    // connected to this node
    private var socket: PeerSocket? = null
    val stats: HashMap<String, Long> = HashMap()
    val identity = AtomicLong(0)
    val metadata: NodeInfo = NodeInfo()

    private var nodeAnnouncedBack = AtomicBoolean(false)

    fun nextMessageId(): String {
        return identity.incrementAndGet().toString()
    }

    fun peerName(): String? {
        return socket?.peerName
    }

    suspend fun connect(p: TestNodecore) {
        if (socket != null) {
            logger.warn("Already connected to ${p.name}, disconnect first")
            return
        }

        waitUntil(attempts = 5, delay = 2_000L) {
            connectOnce(p)
        }
    }

    private suspend fun connectOnce(p: TestNodecore): Boolean {
        try {
            val address = NetworkAddress(p.getAddress(), p.settings.peerPort)
            logger.debug("Connecting to ${p.name}")

            val socket = aSocket(selectorManager)
                .tcp()
                .connect(address)

            val peer = PeerSocket(p, socket) {
                handleMessage(it)
            }

            val announceMsg = buildMessage(nextMessageId()) {
                announce = RpcAnnounce.newBuilder()
                    .setReply(false)
                    .setNodeInfo(metadata.toProto())
                    .build()
            }

            peer.write(announceMsg)

            // wait for ANNOUNCE from a node
            waitUntil(timeout = 5_000L) { nodeAnnouncedBack.get() }
            this.socket = peer
            return true
        } catch (e: Exception) {
            logger.error("Unable to connect to ${p.name}: $e")
            return false
        }
    }

    @Synchronized
    override fun close() {
        logger.debug("Disconnecting from ${peerName()}")

        socket?.close()
        socket = null
    }

    open suspend fun onEvent(e: RpcEvent) {
        // do nothing. users can inherit this
    }

    fun sendEvent(e: RpcEvent) {
        val socket = socket
        if (socket?.isRunning() != true) {
            throw Exception("MiniNode is not connected")
        }

        socket.write(e)
    }

    private suspend fun handleMessage(buf: ByteArray) {
        try {
            val event = RpcEvent.parseFrom(buf)
            logger.debug("${peerName()} --p2p--> ${event.resultsCase.name}")
            // store stats about received msgs
            val count = stats.getOrDefault(event.resultsCase.name, 0L)
            stats[event.resultsCase.name] = count + 1

            // if this is announce back, then release "connect"
            if (event.hasAnnounce()) {
                this.nodeAnnouncedBack.set(true)
            }

            // let user handle msg
            onEvent(event)
        } catch (e: Exception) {
            logger.error("${peerName()} misbehaved! Can't parse Event of size ${buf.size}.")
            close()
            return
        }
    }
}
