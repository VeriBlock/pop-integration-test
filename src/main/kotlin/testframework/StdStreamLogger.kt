package testframework

import org.slf4j.Logger
import org.testcontainers.containers.output.OutputFrame
import java.io.File
import java.io.PrintWriter

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

    fun forward(logger: Logger, addEndl: Boolean = false): (OutputFrame) -> Unit = {
        when (it.type) {
            OutputFrame.OutputType.STDOUT -> {
                if (addEndl) {
                    logger.debug(it.utf8String)
                    stdoutwriter.println(it.utf8String)
                } else {
                    logger.debug(it.utf8String.dropLast(1))
                    stdoutwriter.print(it.utf8String)
                }

                stdoutwriter.flush()
            }
            OutputFrame.OutputType.STDERR -> {
                if (addEndl) {
                    logger.error(it.utf8String)
                    stdoutwriter.println(it.utf8String)
                } else {
                    logger.error(it.utf8String.dropLast(1))
                    stdoutwriter.print(it.utf8String)
                }

                stderrwriter.flush()
            }
            else -> Unit
        }
    }

}