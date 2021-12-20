package testframework

import org.testcontainers.containers.output.OutputFrame
import java.io.File
import java.io.PrintWriter
import java.util.logging.Logger

class StdStreamLogger(
    datadir: File
) {
    private val stdout = File(datadir, "stdout")
    private val stderr = File(datadir, "stderr")
    private val stdoutwriter: PrintWriter
    private val stderrwriter: PrintWriter

    init {
        if (!datadir.exists()) {
            datadir.mkdirs()
            datadir.setReadable(true, false)
            datadir.setWritable(true, false)
        }

        stdout.createNewFile()
        stderr.createNewFile()

        stdoutwriter = stdout.printWriter()
        stderrwriter = stderr.printWriter()
    }

    fun forward(logger: org.slf4j.Logger): (OutputFrame) -> Unit = {
        when (it.type) {
            OutputFrame.OutputType.STDOUT -> {
                logger.debug(it.utf8String.dropLast(1))
                stdoutwriter.println(it.utf8String)
                stdoutwriter.flush()
            }
            OutputFrame.OutputType.STDERR -> {
                logger.error(it.utf8String.dropLast(1))
                stderrwriter.println(it.utf8String)
                stderrwriter.flush()
            }
            else -> Unit
        }
    }

}