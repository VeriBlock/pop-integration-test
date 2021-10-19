package nodecore.testframework

import kotlinx.coroutines.runBlocking
//import org.veriblock.core.utilities.createLogger
import java.io.Closeable
import java.io.File
import java.lang.Exception

open class ProcessManager(
    val name: String,
    val datadir: File,
    builder: (datadir: File) -> ProcessBuilder
) : Closeable, AutoCloseable {
//    var logger = createLogger { }
    var process: Process? = null
    val processBuilder: ProcessBuilder
    val stdout = File(datadir, "stdout")
    val stderr = File(datadir, "stderr")

    init {
        if (!datadir.mkdirs()) {
            throw Exception("Can not create datadir: ${datadir.absolutePath}")
        }

        stdout.createNewFile()
        stderr.createNewFile()

        processBuilder = builder(datadir)
            .directory(datadir)
            .redirectError(ProcessBuilder.Redirect.appendTo(stderr))
            .redirectOutput(ProcessBuilder.Redirect.appendTo(stdout))
    }

    suspend fun start(waitForAvailability: suspend () -> Unit) {
//        logger.info { "$name is starting" }
        process = processBuilder.start()
        waitForAvailability()
//        logger.info { "$name started" }
    }

    fun isAlive(): Boolean {
        return process?.isAlive == true
    }

    fun stop() {
        process?.destroy()
        if (isAlive()) {
            process
                ?.destroyForcibly()
        }
        process = null
    }

    suspend fun restart(waitForAvailability: suspend () -> Unit) {
        stop()
        start(waitForAvailability)
    }

    override fun close() {
//        logger.info { "Stopping $name" }

        // stop this process
        runBlocking {
            stop()
        }

        // do not delete datadir
        // it is managed by BaseIntegrationTest
    }

    protected fun finalize() {
        close()
    }
}
