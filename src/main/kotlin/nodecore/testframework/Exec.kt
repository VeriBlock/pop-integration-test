package nodecore.testframework

import java.io.File
import java.io.IOException

// https://lankydan.dev/running-a-kotlin-class-as-a-subprocess
@Throws(IOException::class, InterruptedException::class)
private fun exec(className: String, args: List<String> = emptyList(), jvmArgs: List<String> = emptyList()): ProcessBuilder {
    val javaHome = System.getProperty("java.home")
    val javaBin = javaHome + File.separator + "bin" + File.separator + "java"
    val classpath = System.getProperty("java.class.path")

    val command = ArrayList<String>()
    command.add(javaBin)
    command.addAll(jvmArgs)
    command.add("-cp")
    command.add(classpath)
    command.add(className)
    command.addAll(args)

    return ProcessBuilder(command)
}

fun buildNodecoreProcess(args: List<String> = emptyList(), jvmArgs: List<String> = emptyList()): ProcessBuilder {
    return exec("nodecore.NodeCore", args, jvmArgs)
}

fun buildApmProcess(args: List<String> = emptyList(), jvmArgs: List<String> = emptyList()): ProcessBuilder {
    return exec("org.veriblock.miners.pop.AltchainPoPMiner", args, jvmArgs)
}
