package nodecore.tests;

import nodecore.api.BlockHeaderContainer;
import nodecore.api.BtcBlockData;
import nodecore.api.SyncNodeCoreApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaExample {
    private static final Logger logger = LoggerFactory.getLogger(JavaExample.class);

    public static void doStuff(BlockHeaderContainer block) {
        BtcBlockData lastBtcBlock = SyncNodeCoreApi.INSTANCE.getLastBitcoinBlockAtVeriBlockBlock(block.getHeader().getHash());
        logger.info("Last BTC block at last VBK block: " + lastBtcBlock);
    }
}
