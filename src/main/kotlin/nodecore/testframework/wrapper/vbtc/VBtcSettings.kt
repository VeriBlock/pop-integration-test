package nodecore.testframework.wrapper.vbtc

import java.io.File

class VBtcSettings(
    val p2pPort: Int,
    val rpcPort: Int,
    val zmqPort: Int,
    val index: Int,
    val baseDir: File,
    val vbtcNetwork: String = "regtest",
    val bitcoinNetwork: String = "regtest",
    val veriblockNetwork: String = "regtest",
    val username: String = "vbitcoin",
    val password: String = "vbitcoin"
)
