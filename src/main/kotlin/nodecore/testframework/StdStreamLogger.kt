package nodecore.testframework

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
        stdout.createNewFile()
        stderr.createNewFile()

        stdoutwriter = stdout.printWriter()
        stderrwriter = stderr.printWriter()
    }

    fun forward(): (OutputFrame) -> Unit = {
        when (it.type) {
            OutputFrame.OutputType.STDOUT -> {
                stdoutwriter.print(it.utf8String)
                stdoutwriter.flush()
            }
            OutputFrame.OutputType.STDERR -> {
                stderrwriter.print(it.utf8String)
                stderrwriter.flush()
            }
            else -> Unit
        }
    }

}