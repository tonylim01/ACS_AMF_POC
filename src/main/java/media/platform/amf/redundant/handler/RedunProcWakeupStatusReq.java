package media.platform.amf.redundant.handler;

import media.platform.amf.common.JsonMessage;
import media.platform.amf.redundant.messages.RoomSessionInfo;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import media.platform.amf.session.SessionState;
import media.platform.amf.session.SessionStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedunProcWakeupStatusReq implements RedunProcMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedunProcWakeupStatusReq.class);

    @Override
    public boolean handle(String body) {
        if (body == null) {
            logger.error("Null body");
            return false;
        }

        RoomSessionInfo roomSessionInfo = (RoomSessionInfo)new JsonMessage(RoomSessionInfo.class).parse(body);
        if (roomSessionInfo == null) {
            logger.warn("Invalid RoomSessionInfo message. [{}]", body);
            return false;
        }

        RoomInfo fromRoomInfo = roomSessionInfo.getRoomInfo();
        SessionInfo fromSessionInfo = roomSessionInfo.getSessionInfo();

        logger.debug("<- Redundant (WakeupStatusReq): sessionId [{}]", fromSessionInfo.getSessionId());

        if (fromRoomInfo == null) {
            logger.warn("Null roomInfo");
            return false;
        }

        if (fromSessionInfo == null) {
            logger.warn("Null sessionInfo");
            return false;
        }

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

        sessionInfo.setCallerWakeupStatus(fromSessionInfo.isCallerWakeupStatus());
        sessionInfo.setCalleeWakeupStatus(fromSessionInfo.isCalleeWakeupStatus());

        sessionInfo.setSuccessMedia(fromSessionInfo.getSuccessMedia());
        sessionInfo.setFailureMedia(fromSessionInfo.getFailureMedia());

        roomInfo.setWakeupStatus(fromRoomInfo.getWakeupStatus());

        SessionInfo otherSessionInfo = SessionManager.findOtherSession(sessionInfo);
        if (otherSessionInfo != null) {
            otherSessionInfo.setCallerWakeupStatus(fromSessionInfo.isCallerWakeupStatus());
            otherSessionInfo.setCalleeWakeupStatus(fromSessionInfo.isCalleeWakeupStatus());

            otherSessionInfo.setSuccessMedia(fromSessionInfo.getSuccessMedia());
            otherSessionInfo.setFailureMedia(fromSessionInfo.getFailureMedia());
        }

        roomInfo.setAwfCallId(fromRoomInfo.getAwfCallId());
        roomInfo.setLastTransactionId(fromRoomInfo.getLastTransactionId());
        roomInfo.setAwfQueueName(fromRoomInfo.getAwfQueueName());

        SessionStateManager.getInstance().setState(sessionInfo.getSessionId(), SessionState.START);

        return true;
    }
}
