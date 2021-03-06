/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqOutgoingMessage.java
 * @author Tony Lim
 *
 */

package media.platform.amf.engine.handler.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.EngineServiceManager;
import media.platform.amf.engine.types.EngineRequestHeader;
import media.platform.amf.engine.types.EngineRequestMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;

public class EngineOutgoingMessage implements EngineOutgoingMessageInterface {

    private static final Logger logger = LoggerFactory.getLogger(EngineOutgoingMessage.class);

    private EngineRequestHeader header;
    private JsonElement jsonElement = null;

    public EngineOutgoingMessage() {
    }

    public EngineOutgoingMessage(String type, String cmd, String appId) {
        this.header = new EngineRequestHeader(type, cmd, appId);
    }

    @Override
    public void setBody(Object obj, Type objType) {
        Gson gson = new GsonBuilder().create();
        jsonElement = gson.toJsonTree(obj, objType);
    }

    @Override
    public boolean sendTo(int engineId, boolean push) {
        boolean result = false;

        EngineRequestMessage msg = new EngineRequestMessage(header);
        if (jsonElement != null) {
            msg.setBody(jsonElement);
        }

        boolean isHeartbeat = msg.getHeader().getCmd().equals("heartbeat");

        /*
        if (!isHeartbeat) {
            logger.info("-> (Engine) {}{}Req",
                    StringUtils.capitalize(header.getType()), StringUtils.capitalize(header.getCmd()));
        }
        */

        try {
            Gson gson = new Gson();
            String json = gson.toJson(msg);

            if (json != null) {
                if (!push) {
                    EngineClient client = EngineClient.getInstance(engineId);
                    if (client != null) {
                        result = client.sendMessage(json, isHeartbeat ? false : true);
                    }
                }
                else {
//                    EngineServiceManager.getInstance().pushMessage(msg.getHeader().getAppId(), msg.getHeader().getCmd(), msg.getHeader().getType(), json);
                }
            }
            else {
                logger.error("json error: type [{}] cmd [{}] appId [{}]", header.getType(), header.getCmd(), header.getAppId());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Combines two functions: setReasonCode() and setReasonStr()
     * @param reasonCode
     * @param reasonStr
     */
    /*
    protected void setReason(int reasonCode, String reasonStr) {
        setReasonCode(reasonCode);
        setReasonStr(reasonStr);
    }

    */
}
