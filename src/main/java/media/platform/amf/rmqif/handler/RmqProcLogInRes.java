/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcLogInRes.java
 * @author Tony Lim
 *
 */


package media.platform.amf.rmqif.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.common.JsonMessage;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.redundant.RedundantClient;
import media.platform.amf.redundant.RedundantMessage;
import media.platform.amf.redundant.messages.RoomSessionInfo;
import media.platform.amf.rmqif.handler.base.RmqIncomingMessageHandler;
import media.platform.amf.rmqif.messages.LogInRes;
import media.platform.amf.rmqif.module.RmqData;
import media.platform.amf.rmqif.types.RmqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmqProcLogInRes extends RmqIncomingMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcIncomingHangupReq.class);

    @Override
    public boolean handle(RmqMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            return false;
        }

        RmqData<LogInRes> data = new RmqData<>(LogInRes.class);
        LogInRes res = data.parse(msg);

        logger.info("[{}] LoginRes. amr [{}] evs [{}]", msg.getSessionId(),
                res.getAmrLicenseCount(), res.getEvsLicenseCount());

        if (res.getAmrLicenseCount() > 0) {
            EngineManager.getInstance().setAmrTotal(res.getAmrLicenseCount());
        }

        if (res.getEvsLicenseCount() > 0) {
            EngineManager.getInstance().setEvsTotal(res.getEvsLicenseCount());
        }

        if (AppInstance.getInstance().getUserConfig().getRedundantConfig().isActive()) {
            String json = new JsonMessage(LogInRes.class).build(res);
            logger.debug("[{}] JSON: {}", msg.getSessionId(), json);

            RedundantClient.getInstance().sendMessage(RedundantMessage.RMT_SN_LOGIN_RES, json);
        }

//        StatManager.getInstance().incCount(StatManager.SVC_IN_RELEASE);

        return false;
    }

    @Override
    public void sendResponse(String sessionId, String transactionId, String queueName, int reasonCode, String reasonStr) {

    }
}
