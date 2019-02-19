package media.platform.amf.config;

import org.ini4j.Ini;
import org.ini4j.Wini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class OamConfig {

    private static final Logger logger = LoggerFactory.getLogger(OamConfig.class);

    private static final String STR_SECTION_OAM ="Oam";
    private static final String STR_PACKET_LOSS_THRESHOLD = "PACKET_LOSS_THRESHOLD";

    private int packetLossThreshold = 0;
    private String configPath;

    public OamConfig(String configPath) {
        this.configPath = configPath;
        loadConfig();
    }

    private void loadConfig() {
        logger.info("Load oam config [{}]", configPath);

        File file = new File(configPath);
        if (!file.exists()) {
            return;
        }

        try {
            Ini ini = new Ini(file);

            packetLossThreshold = Integer.parseInt(ini.get(STR_SECTION_OAM, STR_PACKET_LOSS_THRESHOLD));
            logger.info("Initial oam config. packetLossThreshold [{}]", packetLossThreshold);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getPacketLossThreshold() {
        return packetLossThreshold;
    }

    public void setPacketLossThreshold(int packetLossThreshold) {
        this.packetLossThreshold = packetLossThreshold;

        File file = new File(configPath);

        try {
            if (!file.exists()) {
                file.createNewFile();
            }

            Wini ini = new Wini(file);
            ini.put(STR_SECTION_OAM, STR_PACKET_LOSS_THRESHOLD, packetLossThreshold);
            ini.store();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
