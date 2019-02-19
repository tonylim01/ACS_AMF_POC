package media.platform.amf.redundant.handler;

import media.platform.amf.common.AppId;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.handler.EngineProcAudioBranchReq;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedunProcAiServiceRes implements RedunProcMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedunProcAiServiceRes.class);

    @Override
    public boolean handle(String body) {
        if (body == null) {
            logger.error("Null body");
            return false;
        }

        SessionInfo fromSessionInfo = (SessionInfo)new JsonMessage(SessionInfo.class).parse(body);
        if (fromSessionInfo == null) {
            logger.warn("Invalid sessionInfo message. [{}]", body);
            return false;
        }

        logger.debug("<- Redundant (AiServiceRes): sessionId [{}]", fromSessionInfo.getSessionId());

        RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(fromSessionInfo.getConferenceId());
        if (roomInfo == null) {
            logger.warn("[{}] Cannot find room", fromSessionInfo.getSessionId());
            return false;
        }

        SessionInfo sessionInfo = SessionManager.getInstance().getSession(fromSessionInfo.getSessionId());
        if (sessionInfo == null) {
            logger.warn("[{}] Cannot find session", fromSessionInfo.getSessionId());
            return false;
        }

        sessionInfo.setAiifIp(fromSessionInfo.getAiifIp());
        sessionInfo.setAiifPort(fromSessionInfo.getAiifPort());

        String appId = AppId.newId();

        EngineProcAudioBranchReq branchReq = new EngineProcAudioBranchReq(appId);
        branchReq.setData(sessionInfo, false);

        EngineClient engineClient = EngineClient.getInstance(sessionInfo.getEngineId());
        if (engineClient == null) {
            logger.error("[{}] Null engineClient", sessionInfo.getSessionId());
            return false;
        }

        if (sessionInfo.getSessionId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getSessionId());
        }

        if (!branchReq.send(sessionInfo.getEngineId())) {
            // ERROR
        }

        return true;
    }
}
