package media.platform.amf.oam;

import com.sun.jna.Pointer;
import com.uangel.jnauaoam.UalibContext;
import com.uangel.jnauaoam.mmc.MMCHandler;
import com.uangel.jnauaoam.mmc.ManMachineCommand;
import media.platform.amf.AppInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisAmfPacketLoss extends PacketLossWrapper implements ManMachineCommand {
    private static final Logger logger = LoggerFactory.getLogger(DisAmfPacketLoss.class);

    private MMCHandler mmcHandler;

    public void register( UalibContext ualibContext) {

        mmcHandler = new MMCHandler(ualibContext);
        mmcHandler.register("dis-amf-packet-loss", this);

        logger.info("Register mmc command 'dis-amf-packet-loss'");
    }

    @Override
    public boolean exec(Pointer mmlIn) {
        //int packetLoss = StatManager.getInstance().getPacketLossPercent();
        int packetLoss = AppInstance.getInstance().getOamConfig().getPacketLossThreshold();

        logger.info("Packet loss [{}]", packetLoss);

        try {
            this.onDisplay(mmlIn, packetLoss);
            this.sendSuccess(mmlIn);

        } catch(Exception e) {
            this.sendFail(mmlIn, e.getMessage());
        }
        return true;
    }
}
