package testframework

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
    var useConsole = false

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

    fun forward(): (OutputFrame) -> Unit = {
        when (it.type) {
            OutputFrame.OutputType.STDOUT -> {
                if(useConsole) print(it.utf8String)
                stdoutwriter.print(it.utf8String)
                stdoutwriter.print('\n')
                stdoutwriter.flush()
            }
            OutputFrame.OutputType.STDERR -> {
                if(useConsole) System.err.print(it.utf8String)
                stderrwriter.print(it.utf8String)
                stderrwriter.flush()
            }
            else -> Unit
        }
    }

}