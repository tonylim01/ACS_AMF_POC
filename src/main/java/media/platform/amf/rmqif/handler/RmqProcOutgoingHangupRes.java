/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcOutgoingHangupRes.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.oam.UaOamManager;
import media.platform.amf.oam.UaTraceMsg;
import media.platform.amf.redundant.RedundantClient;
import media.platform.amf.redundant.RedundantMessage;
import media.platform.amf.rmqif.handler.base.RmqIncomingMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import media.platform.amf.rmqif.types.RmqMessage;
import media.platform.amf.service.ServiceManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionState;
import media.platform.amf.session.SessionStateManager;

public class RmqProcOutgoingHangupRes extends RmqIncomingMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(RmqProcOutgoingHangupRes.class);

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

//        ServiceManager.getInstance().releaseResource(msg.getSessionId());

        SessionStateManager.getInstance().setState(msg.getSessionId(), SessionState.IDLE);

        UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                msg.getHeader().getType(),
                msg.getHeader().getMsgFrom(), null,
                sessionInfo.getFromNo(), sessionInfo.getToNo(),
                "");

        if (AppInstance.getInstance().getUserConfig().getRedundantConfig().isActive()) {
            RedundantClient.getInstance().sendMessageSimple(RedundantMessage.RMT_SN_HANGUP_REQ, msg.getSessionId());
        }

        return true;
    }

    @Override
    public void sendResponse(String sessionId, String transactionId, String queueName, int reasonCode, String reasonStr) {

    }
}
