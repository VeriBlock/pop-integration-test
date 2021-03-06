package nodecore.testframework

interface BtcPluginInterface {
    fun name(): String
    fun payoutAddress(): String
    fun username(): String
    fun password(): String
    fun id(): Long
    fun port(): Int
    fun network(): String
    fun payoutDelay(): Int
}
