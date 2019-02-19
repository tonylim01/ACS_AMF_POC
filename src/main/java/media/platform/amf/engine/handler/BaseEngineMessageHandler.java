package media.platform.amf.engine.handler;

import media.platform.amf.engine.types.EngineReportMessage;
import media.platform.amf.engine.types.EngineResponseMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseEngineMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(BaseEngineMessageHandler.class);

    protected boolean compareString(String src, String dst) {
        return (src != null && dst != null && src.equals(dst)) ? true : false;
    }

    protected void printResponseHeader(int index, String sessionId, EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return;
        }

        logger.info("[{}] <- (Engine-{}) {}{}Res. result [{}] reason [{}]", sessionId, index,
                StringUtils.capitalize(msg.getHeader().getType()),
                StringUtils.capitalize(msg.getHeader().getCmd()),
                msg.getHeader().getResult(),
                msg.getHeader().getReason());
    }

    protected void printResponseHeader(int index, EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return;
        }

        logger.info("<- (Engine-{}) {}{}Res. result [{}] reason [{}]", index,
                StringUtils.capitalize(msg.getHeader().getType()),
                StringUtils.capitalize(msg.getHeader().getCmd()),
                msg.getHeader().getResult(),
                msg.getHeader().getReason());
    }

    protected void printReportHeader(int index, String sessionId, EngineReportMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return;
        }

        logger.info("[{}] <- (Engine-{}) {}{}Rpt. event [{}] value [{}]", sessionId, index,
                StringUtils.capitalize(msg.getHeader().getType()),
                StringUtils.capitalize(msg.getHeader().getCmd()),
                msg.getHeader().getEvent(),
                msg.getHeader().getValue());
    }

    protected void printReportHeader(int index, EngineReportMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return;
        }

        logger.info("<- (Engine-{}) {}{}Rpt. event [{}] value [{}]", index,
                StringUtils.capitalize(msg.getHeader().getType()),
                StringUtils.capitalize(msg.getHeader().getCmd()),
                msg.getHeader().getEvent(),
                msg.getHeader().getValue());
    }
}
