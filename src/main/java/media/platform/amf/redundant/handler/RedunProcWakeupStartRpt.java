package media.platform.amf.redundant.handler;

import media.platform.amf.common.AppId;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.handler.EngineProcWakeupStopReq;
import media.platform.amf.redundant.messages.RedundantInfoSimple;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedunProcWakeupStartRpt implements RedunProcMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedunProcWakeupStartRpt.class);

    @Override
    public boolean handle(String body) {
        if (body == null) {
            logger.error("Null body");
            return false;
        }

        RedundantInfoSimple redundantInfo = (RedundantInfoSimple)new JsonMessage(RedundantInfoSimple.class).parse(body);
        if (redundantInfo == null) {
            logger.warn("Invalid redundantInfo message. [{}]", body);
            return false;
        }

        logger.debug("<- Redundant (WakeupStartRpt): sessionId [{}]", redundantInfo.getSessionId());

        if (redundantInfo.getSessionId() == null) {
            logger.error("RedunWakeupStartRpt has null sessionId");
            return false;
        }

        SessionInfo sessionInfo = SessionManager.getInstance().getSession(redundantInfo.getSessionId());
        if (sessionInfo == null) {
            logger.error("[{}] RedunWakeupStartRpt. Cannot find sessionInfo", redundantInfo.getSessionId());
            return false;
        }

        String appId = AppId.newId();

        EngineProcWakeupStopReq wakeupStopReq = new EngineProcWakeupStopReq(appId);
        wakeupStopReq.setData(sessionInfo, sessionInfo.getEngineToolId());

        EngineClient engineClient = EngineClient.getInstance(sessionInfo.getEngineId());
        if (engineClient == null) {
            logger.error("[{}] Null engineClient", sessionInfo.getSessionId());
            return false;
        }

        if (sessionInfo.getSessionId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getSessionId());
        }

        if (!wakeupStopReq.send(sessionInfo.getEngineId(), false)) {
            // ERROR
        }

        return true;
    }
}
