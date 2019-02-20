package media.platform.amf.redundant.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.config.SdpConfig;
import media.platform.amf.redundant.messages.RoomSessionInfo;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import media.platform.amf.session.SessionState;
import media.platform.amf.session.SessionStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedunProcInboundSetOfferReq implements RedunProcMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedunProcInboundSetOfferReq.class);

    @Override
    public boolean handle(String body) {
        if (body == null) {
            logger.error("Null body");
            return false;
        }

//        SessionInfo fromSessionInfo = (SessionInfo)new JsonMessage(SessionInfo.class).parse(body);
        RoomSessionInfo roomSessionInfo = (RoomSessionInfo)new JsonMessage(RoomSessionInfo.class).parse(body);
        if (roomSessionInfo == null) {
            logger.warn("Invalid RoomSessionInfo message. [{}]", body);
            return false;
        }

        RoomInfo fromRoomInfo = roomSessionInfo.getRoomInfo();
        SessionInfo fromSessionInfo = roomSessionInfo.getSessionInfo();

        logger.debug("<- Redundant (InboundSetOfferReq): sessionId [{}]", fromSessionInfo.getSessionId());

        int parCount = 0;

        RoomManager roomManager = RoomManager.getInstance();
        if (roomManager.hasSession(fromSessionInfo.getConferenceId(), fromSessionInfo.getSessionId())) {
            logger.warn("[{}] Already existed in room [{}]", fromSessionInfo.getSessionId(), fromSessionInfo.getConferenceId());
        }
        else {
            parCount = roomManager.addSession(fromSessionInfo.getConferenceId(), fromSessionInfo.getSessionId());
            logger.debug("[{}] Room addSession [{}] size [{}]", fromSessionInfo.getConferenceId(), fromSessionInfo.getSessionId(), parCount);
        }

        if (parCount == 0) {
            return false;
        }

        RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(fromSessionInfo.getConferenceId());
        if (roomInfo == null) {
            logger.warn("[{}] Cannot create room", fromSessionInfo.getSessionId());
            return false;
        }

        if (roomInfo.getEngineId() != fromRoomInfo.getEngineId()) {
            roomInfo.setEngineId(fromRoomInfo.getEngineId());
        }

        // Creates a sessionInfo and set things following with the offerReq
        SessionManager sessionManager = SessionManager.getInstance();
        SessionInfo sessionInfo = sessionManager.createSession(fromSessionInfo.getSessionId());

        if (sessionInfo == null) {
            logger.warn("[{}] Cannot create session", fromSessionInfo.getSessionId());
            RoomManager.getInstance().removeSession(fromSessionInfo.getConferenceId(), fromSessionInfo.getSessionId());

            return false;
        }

        sessionInfo.setSdpInfo(fromSessionInfo.getSdpInfo());
        sessionInfo.setSdpDeviceInfo(fromSessionInfo.getSdpDeviceInfo());
        sessionInfo.setConferenceId(fromSessionInfo.getConferenceId());
        sessionInfo.setFromNo(fromSessionInfo.getFromNo());
        sessionInfo.setToNo(fromSessionInfo.getToNo());
        sessionInfo.setCaller(fromSessionInfo.isCaller());
        sessionInfo.setOutbound(fromSessionInfo.getOutbound());
        sessionInfo.setRemoteRmqName(fromSessionInfo.getRemoteRmqName());
        sessionInfo.setEngineId(fromSessionInfo.getEngineId());

        sessionInfo.setSrcLocalPort(fromSessionInfo.getSrcLocalPort());

        SdpConfig sdpConfig = AppInstance.getInstance().getUserConfig().getSdpConfig();

        try {
            //sessionInfo.rtpChannel = AppInstance.getInstance().getNettyRTPServer().addBindPort(sdpConfig.getLocalIpAddress(), sessionInfo.getSrcLocalPort());
            sessionInfo.rtpChannel = AppInstance.getInstance().getNettyRTPServer().addBindPort("0.0.0.0", sessionInfo.getSrcLocalPort());
        } catch (Exception e) {
            logger.error("Exception rtp channel [{}] [{}] port [{}]", e.getClass(), e.getMessage(), sessionInfo.getSrcLocalPort());
        }

        sessionInfo.setDstLocalPort(fromSessionInfo.getDstLocalPort());
        sessionInfo.setEnginePort(fromSessionInfo.getEnginePort());

        try {
            sessionInfo.udpChannel = AppInstance.getInstance().getNettyUDPServer().addBindPort("127.0.0.1", sessionInfo.getDstLocalPort());
        } catch (Exception e) {
            logger.error("Exception udp channel [{}] [{}] port [{}]", e.getClass(), e.getMessage(), sessionInfo.getDstLocalPort());
        }

        logger.debug("[{}] Local port: src [{}] dst [{}]", sessionInfo.getSessionId(),
                sessionInfo.getSrcLocalPort(), sessionInfo.getDstLocalPort());
        logger.debug("[{}] Participant count {}", sessionInfo.getSessionId(), parCount);

        SessionStateManager.getInstance().setState(sessionInfo.getSessionId(), SessionState.OFFER);

        return true;
    }
}
