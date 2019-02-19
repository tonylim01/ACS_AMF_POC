/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcOutgoingCommandRes.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.handler;

import media.platform.amf.oam.UaOamManager;
import media.platform.amf.oam.UaTraceMsg;
import media.platform.amf.rmqif.handler.base.RmqOutgoingMessage;
import media.platform.amf.session.SessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import media.platform.amf.rmqif.types.RmqMessageType;

public class RmqProcOutgoingCommandRes extends RmqOutgoingMessage {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcOutgoingCommandRes.class);

    public RmqProcOutgoingCommandRes(String sessionId, String transactionId) {
        super(sessionId, transactionId);
        setType(RmqMessageType.RMQ_MSG_STR_COMMAND_RES);
    }

    public boolean send(String queueName) {

        SessionInfo sessionInfo = checkAndGetSession(getSessionId());
        if (sessionInfo == null) {
            return sendTo(queueName);
        }

        UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                getHeader().getType(),
                null, queueName,
                sessionInfo.getFromNo(), sessionInfo.getToNo(),
                "");

        return sendTo(queueName);
    }

}
