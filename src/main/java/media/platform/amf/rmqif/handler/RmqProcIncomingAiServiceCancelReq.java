/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcAiServiceRes.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.common.AppId;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.handler.EngineProcAudioBranchReq;
import media.platform.amf.engine.messages.AudioBranchReq;
import media.platform.amf.oam.StatManager;
import media.platform.amf.oam.UaOamManager;
import media.platform.amf.oam.UaTraceMsg;
import media.platform.amf.redundant.RedundantClient;
import media.platform.amf.redundant.RedundantMessage;
import media.platform.amf.rmqif.handler.base.RmqIncomingMessageHandler;
import media.platform.amf.rmqif.messages.AiServiceCancelReq;
import media.platform.amf.rmqif.module.RmqData;
import media.platform.amf.rmqif.types.RmqMessage;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmqProcIncomingAiServiceCancelReq extends RmqIncomingMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcIncomingAiServiceCancelReq.class);

    @Override
    public boolean handle(RmqMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return false;
        }

        logger.info("[{}] AiServiceCancelReq", msg.getSessionId());

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

        RmqData<AiServiceCancelReq> data = new RmqData<>(AiServiceCancelReq.class);
        AiServiceCancelReq req = data.parse(msg);

        SessionInfo reqSessionInfo = RoomManager.getInstance().getProperSessionInfo(sessionInfo, (req.getDir() == 1));

        if (reqSessionInfo != null) {

            EngineClient engineClient = EngineClient.getInstance(reqSessionInfo.getEngineId());
            if (engineClient == null) {
                logger.error("[{}] Null engineClient", reqSessionInfo.getSessionId());
                return false;
            }
            String appId = AppId.newId();

            EngineProcAudioBranchReq branchReq = new EngineProcAudioBranchReq(appId);
            branchReq.setData(reqSessionInfo, true);

            engineClient.pushSentQueue(appId, AudioBranchReq.class, branchReq.getData());
            if (sessionInfo.getSessionId() != null) {
                AppId.getInstance().push(appId, sessionInfo.getSessionId());
            }

            if (!branchReq.send(reqSessionInfo.getEngineId())) {
                // ERROR
//                EngineClient.getInstance().removeSentQueue(appId);
            }

            logger.info("[{}] -> (Engine-{}) AudioBranchReq(Stop). toolId [{}]", sessionInfo.getSessionId(),
                    sessionInfo.getEngineId(), sessionInfo.getEngineToolId());

            if (AppInstance.getInstance().getUserConfig().getRedundantConfig().isActive()) {
                String json = new JsonMessage(SessionInfo.class).build(reqSessionInfo);
                logger.debug("[{}] JSON: {}", msg.getSessionId(), json);

                RedundantClient.getInstance().sendMessage(RedundantMessage.RMT_SN_AI_SERVICE_CANCEL_REQ, json);
            }

        }

        StatManager.getInstance().incCount(StatManager.SVC_IN_AI_CANCEL);

        return false;
    }

    @Override
    public void sendResponse(String sessionId, String transactionId, String queueName, int reasonCode, String reasonStr) {

    }
}

