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
import media.platform.amf.redundant.messages.RoomSessionInfo;
import media.platform.amf.rmqif.handler.base.RmqIncomingMessageHandler;
import media.platform.amf.rmqif.messages.AiServiceRes;
import media.platform.amf.rmqif.module.RmqData;
import media.platform.amf.rmqif.types.RmqMessage;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmqProcAiServiceRes extends RmqIncomingMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcAiServiceRes.class);

    @Override
    public boolean handle(RmqMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return false;
        }

        logger.info("[{}] AiServiceRes", msg.getSessionId());

        String sessionId = TransactionManager.getInstance().get(msg.getHeader().getTransactionId());
        if (sessionId == null) {
            logger.error("[{}] Invalid transactionId [{}]", msg.getSessionId(), msg.getHeader().getTransactionId());
            return false;
        }

        SessionInfo sessionInfo = validateSessionId(sessionId, msg.getHeader().getTransactionId(), msg.getHeader().getMsgFrom());
        if (sessionInfo == null) {
            logger.error("[{}] Session not found", msg.getSessionId());
            return false;
        }

        if (msg.getHeader().getReasonCode() > 0) {
            logger.error("[{}] AiServiceRes. code [{}] reason [{}]", msg.getSessionId(),
                    msg.getHeader().getReasonCode(), msg.getHeader().getReason());

            UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                    msg.getHeader().getType(),
                    msg.getHeader().getMsgFrom(), null,
                    sessionInfo.getFromNo(), sessionInfo.getToNo(),
                    String.format("Code [%d] reason [%s]", msg.getHeader().getReasonCode(), msg.getHeader().getReason()));

            return false;
        }

        UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                msg.getHeader().getType(),
                msg.getHeader().getMsgFrom(), null,
                sessionInfo.getFromNo(), sessionInfo.getToNo(),
                null);


        RmqData<AiServiceRes> data = new RmqData<>(AiServiceRes.class);
        AiServiceRes res = data.parse(msg);

        if ((res.getIp() == null) || (res.getPort() == 0)) {

            logger.error("[{}] No aiif port found");
            return false;
        }

        sessionInfo.setAiifIp(res.getIp());
        sessionInfo.setAiifPort(res.getPort());

        StatManager.getInstance().incCount(StatManager.SVC_AI_RES);

        String appId = AppId.newId();

        EngineProcAudioBranchReq branchReq = new EngineProcAudioBranchReq(appId);
        branchReq.setData(sessionInfo, false);

        EngineClient engineClient = EngineClient.getInstance(sessionInfo.getEngineId());
        if (engineClient == null) {
            logger.error("[{}] Null engineClient", sessionId);
            return false;
        }

        engineClient.pushSentQueue(appId, AudioBranchReq.class, branchReq.getData());
        if (sessionInfo.getSessionId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getSessionId());
        }

        if (!branchReq.send(sessionInfo.getEngineId())) {
            // ERROR
//            EngineClient.getInstance().removeSentQueue(appId);
        }

        logger.info("[{}] -> (Engine-{}) AudioBranchReq. toolId [{}] dest [{}:{}]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), sessionInfo.getEngineToolId(),
                res.getIp(), res.getPort());

        /*  20190220 not to sync this msg

        if (AppInstance.getInstance().getUserConfig().getRedundantConfig().isActive()) {
            String json = new JsonMessage(SessionInfo.class).build(sessionInfo);
            logger.debug("[{}] JSON: {}", msg.getSessionId(), json);

            RedundantClient.getInstance().sendMessage(RedundantMessage.RMT_SN_AI_SERVICE_RES, json);
        }
         */

        return false;
    }

    @Override
    public void sendResponse(String sessionId, String transactionId, String queueName, int reasonCode, String reasonStr) {

    }
}

