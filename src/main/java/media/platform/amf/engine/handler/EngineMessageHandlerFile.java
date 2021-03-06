package media.platform.amf.engine.handler;

import media.platform.amf.common.AppId;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.messages.FilePlayReq;
import media.platform.amf.engine.types.EngineMessageType;
import media.platform.amf.engine.types.EngineReportMessage;
import media.platform.amf.engine.types.EngineResponseMessage;
import media.platform.amf.engine.types.SentMessageInfo;
import media.platform.amf.oam.StatManager;
import media.platform.amf.rmqif.handler.RmqProcMediaPlayDoneReq;
import media.platform.amf.rmqif.handler.RmqProcMediaStopRes;
import media.platform.amf.rmqif.types.RmqMessageType;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineMessageHandlerFile extends BaseEngineMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(EngineMessageHandlerFile.class);

    private int index;
    private EngineClient engineClient;

    public EngineMessageHandlerFile(int index) {
        this.index = index;
        engineClient = EngineClient.getInstance(index);
    }

    public void handle(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        String sessionId = AppId.getInstance().get(msg.getHeader().getAppId());
        if (sessionId == null) {
            logger.warn("No sessionId for appId=[{}]", msg.getHeader().getAppId());
            return;
        }

        printResponseHeader(index, sessionId, msg);

        if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_PLAY)) {
            procFilePlayRes(sessionId, msg);
        }
        else if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_STOP)) {
            procFileStopRes(sessionId, msg);
        }
        else {
            logger.warn("Unsupported cmd [{}]", msg.getHeader().getCmd());
        }
    }

    public void handle(EngineReportMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        String sessionId = AppId.getInstance().get(msg.getHeader().getAppId());
        if (sessionId == null) {
            logger.warn("No sessionId for appId=[{}]", msg.getHeader().getAppId());
            return;
        }

        printReportHeader(index, sessionId, msg);

        if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_PLAY)) {
            procFilePlayRpt(sessionId, msg);
        }
        else {
            logger.warn("Unsupported cmd [{}]", msg.getHeader().getCmd());
        }
    }

    private void procFilePlayRes(String sessionId, EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (msg.getHeader().getAppId() == null) {
            logger.warn("Null appId in response message");
            return;
        }

        SessionInfo sessionInfo = SessionManager.getInstance().getSession(sessionId);
        if (sessionInfo == null) {
            logger.warn("Cannot find session for appId=[{}]", msg.getHeader().getAppId());
            return;
        }

        if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_OK)) {
        }
        else if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_FAIL)) {

            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null && roomInfo.getAwfQueueName() != null) {

                if (engineClient == null) {
                    logger.error("[{}] Engine not found", sessionInfo.getSessionId());
                    return;
                }

                SentMessageInfo sentInfo = engineClient.getSentQueue(msg.getHeader().getAppId());
                if (sentInfo != null) {
                    int mediaType = ((FilePlayReq) sentInfo.getObj()).getType();

                    RmqProcMediaPlayDoneReq req = new RmqProcMediaPlayDoneReq((roomInfo.getAwfCallId() != null) ? roomInfo.getAwfCallId() : sessionInfo.getSessionId(),
                            AppId.newId());

                    req.setReasonCode(RmqMessageType.RMQ_MSG_COMMON_REASON_CODE_FILE_ERROR);
                    req.setReasonStr(msg.getHeader().getReason());

                    req.send(roomInfo.getAwfQueueName(), sessionInfo.isCaller() ? 1 : 2, (mediaType == 0) ? 1 : 2);

                    StatManager.getInstance().incCount(StatManager.SVC_PLAY_ERROR);
                }
            }
        }
        else {
            logger.warn("Undefined result [{}]", msg.getHeader().getResult());
        }

    }

    private void procFileStopRes(String sessionId, EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_OK)) {
        }
        else {
            logger.warn("Undefined result [{}]", msg.getHeader().getResult());
        }

        if (msg.getHeader().getAppId() == null) {
            logger.warn("Null appId in response message");
            return;
        }

        SessionInfo sessionInfo = SessionManager.getInstance().getSession(sessionId);
        if (sessionInfo == null) {
            logger.warn("Cannot find session for appId=[{}]", msg.getHeader().getAppId());
            return;
        }

        sessionInfo.setStopAppId(msg.getHeader().getAppId());

        if (engineClient == null) {
            logger.error("[{}] Engine not found", sessionInfo.getSessionId());
            return;
        }

        engineClient.removeSentQueue(msg.getHeader().getAppId());
    }

    private void procFilePlayRpt(String sessionId, EngineReportMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        boolean isSuccess = false;
        boolean isStopped = false;

        if (compareString(msg.getHeader().getEvent(), EngineMessageType.MSG_EVENT_DONE)) {
            isSuccess = true;
        }
        else if (compareString(msg.getHeader().getEvent(), EngineMessageType.MSG_EVENT_STOPPED)) {
            isSuccess = true;
            isStopped = true;
        }


        if (msg.getHeader().getAppId() == null) {
            logger.warn("Null appId in response message");
            return;
        }

        SessionInfo sessionInfo = SessionManager.getInstance().getSession(sessionId);
        if (sessionInfo == null) {
            logger.warn("Cannot find session for appId=[{}]", msg.getHeader().getAppId());
            return;
        }

        if (sessionInfo.getConferenceId() != null) {
            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null && roomInfo.getAwfQueueName() != null) {

                if (engineClient == null) {
                    logger.error("[{}] Engine not found", sessionInfo.getSessionId());
                    return;
                }

                SentMessageInfo sentInfo = engineClient.getSentQueue(msg.getHeader().getAppId());
                if (sentInfo != null) {

                    if (sessionInfo.getStopAppId() != null) {

                        RmqProcMediaStopRes res = new RmqProcMediaStopRes(sessionId, sessionInfo.getStopAppId());

                        if (!isSuccess) {
                            res.setReasonCode(RmqMessageType.RMQ_MSG_COMMON_REASON_CODE_PLAY_STOPPED);
                            res.setReasonStr(msg.getHeader().getValue());
                        }

                        if (res.send(roomInfo.getAwfQueueName()) == false) {
                            // TODO
                        }

                        StatManager.getInstance().incCount(StatManager.SVC_PLAY_STOP_RES);
                    }
                    else {
                        logger.warn("[{}] Invalid state. success [{}] stop [{}]", sessionId, isSuccess, isStopped);
                    }

                    int mediaType = ((FilePlayReq)sentInfo.getObj()).getType();

                    RmqProcMediaPlayDoneReq req = new RmqProcMediaPlayDoneReq(sessionInfo.getSessionId(), AppId.newId());
                    if (!isSuccess) {
                        req.setReasonCode(1);
                        req.setReasonStr(msg.getHeader().getValue());
                    }
                    req.send(roomInfo.getAwfQueueName(), sessionInfo.isCaller() ? 1 : 2, (mediaType == 0) ? 1 : 2);

                    StatManager.getInstance().incCount(StatManager.SVC_PLAY_OK);

                    if (engineClient == null) {
                        logger.error("[{}] Engine not found", sessionInfo.getSessionId());
                        return;
                    }

                    engineClient.removeSentQueue(msg.getHeader().getAppId());
                }
                else {
                    logger.warn("[{}] No sentInfo found. appId [{}]", sessionId, msg.getHeader().getAppId());
                }

            }
        }
        else {
            logger.warn("Undefined event [{}]", msg.getHeader().getEvent());
        }

    }
}
