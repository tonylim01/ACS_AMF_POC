/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcServiceStartReq.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.oam.StatManager;
import media.platform.amf.oam.UaOamManager;
import media.platform.amf.oam.UaTraceMsg;
import media.platform.amf.redundant.RedundantClient;
import media.platform.amf.redundant.RedundantMessage;
import media.platform.amf.redundant.messages.RoomSessionInfo;
import media.platform.amf.rmqif.handler.base.RmqIncomingMessageHandler;
import media.platform.amf.rmqif.messages.WakeupStatusReq;
import media.platform.amf.rmqif.module.RmqData;
import media.platform.amf.rmqif.types.RmqMessage;
import media.platform.amf.rmqif.types.RmqMessageType;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmqProcWakeupStatusReq extends RmqIncomingMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcWakeupStatusReq.class);

    @Override
    public boolean handle(RmqMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return false;
        }

        SessionInfo sessionInfo = validateSessionId(msg.getSessionId(), msg.getHeader().getTransactionId(), msg.getHeader().getMsgFrom());
        if (sessionInfo == null) {
            logger.error("[{}] Session not found", msg.getSessionId());
            return false;
        }

        UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                msg.getHeader().getType(),
                msg.getHeader().getMsgFrom(), null,
                sessionInfo.getFromNo(), sessionInfo.getToNo(),
                "");

        RmqData<WakeupStatusReq> data = new RmqData<>(WakeupStatusReq.class);
        WakeupStatusReq req = data.parse(msg);

        if (req == null) {
            logger.error("[{}] WakeupStatusReq: parsing failed", msg.getSessionId());
            sendResponse(msg.getSessionId(), msg.getHeader().getTransactionId(), msg.getHeader().getMsgFrom(),
                    RmqMessageType.RMQ_MSG_COMMON_REASON_CODE_FAILURE,
                    "PARSING FAILURE");
            return false;
        }

        logger.info("[{}] WakeupStatusReq. callerStatus [{}] calleeStatus [{}]", msg.getSessionId(),
                req.getCallerWakeupStatus(), req.getCalleeWakeupStatus());

        sessionInfo.setCallerWakeupStatus((req.getCallerWakeupStatus() == 1) ? true : false);
        sessionInfo.setCalleeWakeupStatus((req.getCalleeWakeupStatus() == 1) ? true : false);

        sessionInfo.setSuccessMedia((req.getSuccess() != null) ? req.getSuccess().getMediaFileInfo() : null);
        sessionInfo.setFailureMedia((req.getFail() != null) ? req.getFail().getMediaFileInfo() : null);

        RoomInfo roomInfo = null;
        if (sessionInfo.getConferenceId() != null) {
            roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null) {
                int wakeupStatus = roomInfo.getWakeupStatus();

                if ((sessionInfo.isCaller() && (((req.getCallerWakeupStatus() == 1) ? 0x8 : 0x0) != (wakeupStatus & 0xc))) ||
                    (!sessionInfo.isCaller() && (((req.getCalleeWakeupStatus() == 1) ? 0x2 : 0x0) != (wakeupStatus & 0x3)))) {
                    roomInfo.setWakeupStatus(sessionInfo.isCaller(), RoomInfo.WAKEUP_STATUS_PREPARE);
                }

//                if ((sessionInfo.isCaller() && (req.getCallerWakeupStatus() == 1)) ||
//                        (!sessionInfo.isCaller() && (req.getCalleeWakeupStatus() == 1))) {
//                    roomInfo.setWakeupStatus(sessionInfo.isCaller(), RoomInfo.WAKEUP_STATUS_PREPARE);
//                }

                SessionInfo otherSessionInfo = SessionManager.findOtherSession(sessionInfo);
                if (otherSessionInfo != null) {

                    otherSessionInfo.setCallerWakeupStatus((req.getCallerWakeupStatus() == 1) ? true : false);
                    otherSessionInfo.setCalleeWakeupStatus((req.getCalleeWakeupStatus() == 1) ? true : false);

                    otherSessionInfo.setSuccessMedia((req.getSuccess() != null) ? req.getSuccess().getMediaFileInfo() : null);
                    otherSessionInfo.setFailureMedia((req.getFail() != null) ? req.getFail().getMediaFileInfo() : null);

                    if ((otherSessionInfo.isCaller() && (((req.getCallerWakeupStatus() == 1) ? 0x8 : 0x0) != (wakeupStatus & 0xc))) ||
                            (!otherSessionInfo.isCaller() && (((req.getCalleeWakeupStatus() == 1) ? 0x2 : 0x0) != (wakeupStatus & 0x3)))) {
                        roomInfo.setWakeupStatus(otherSessionInfo.isCaller(), RoomInfo.WAKEUP_STATUS_PREPARE);
                    }
//                    if ((otherSessionInfo.isCaller() && (req.getCallerWakeupStatus() == 1)) ||
//                            (!otherSessionInfo.isCaller() && (req.getCalleeWakeupStatus() == 1))) {
//                        roomInfo.setWakeupStatus(otherSessionInfo.isCaller(), RoomInfo.WAKEUP_STATUS_PREPARE);
//                    }

                }

                roomInfo.setAwfCallId(msg.getHeader().getCallId());
                roomInfo.setLastTransactionId(msg.getHeader().getTransactionId());
                roomInfo.setAwfQueueName(msg.getHeader().getMsgFrom());
            }
        }

        SessionStateManager.getInstance().setState(msg.getSessionId(), SessionState.START);

        if (req.getCallerWakeupStatus() == 1) {
            StatManager.getInstance().incCount(StatManager.SVC_CG_WAKEUP_REQ);
        }
        if (req.getCalleeWakeupStatus() == 1) {
            StatManager.getInstance().incCount(StatManager.SVC_CD_WAKEUP_REQ);
        }

        //sendResponse(msg.getSessionId(), msg.getHeader().getTransactionId(), msg.getHeader().getMsgFrom());

        if (AppInstance.getInstance().getUserConfig().getRedundantConfig().isActive()) {

            RoomSessionInfo roomSessionInfo = new RoomSessionInfo();
            roomSessionInfo.setRoomInfo(roomInfo);
            roomSessionInfo.setSessionInfo(sessionInfo);

            String json = new JsonMessage(RoomSessionInfo.class).build(roomSessionInfo);
            logger.debug("[{}] JSON: {}", msg.getSessionId(), json);

            RedundantClient.getInstance().sendMessage(RedundantMessage.RMT_SN_WAKEUP_STATUS_REQ, json);
        }


        return false;
    }

    @Override
    public void sendResponse(String sessionId, String transactionId, String queueName, int reasonCode, String reasonStr) {

        RmqProcWakeupStatusRes res = new RmqProcWakeupStatusRes(sessionId, transactionId);

        res.setReasonCode(reasonCode);
        res.setReasonStr(reasonStr);

        //
        // TODO
        //
        if (res.send(queueName, true, true) == false) {
            // TODO
        }

    }
}