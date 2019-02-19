package media.platform.amf.engine.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import media.platform.amf.common.AppId;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.messages.ToolResetReq;
import media.platform.amf.engine.messages.ToolStatusRpt;
import media.platform.amf.engine.types.EngineMessageType;
import media.platform.amf.engine.types.EngineReportMessage;
import media.platform.amf.engine.types.EngineResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineMessageHandlerTool extends BaseEngineMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(EngineMessageHandlerTool.class);

    private int index;
    private EngineClient engineClient;

    public EngineMessageHandlerTool(int index) {
        this.index = index;
        engineClient = EngineClient.getInstance(index);
    }

    public void handle(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        printResponseHeader(index, msg);

        if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_RESET)) {
            procToolResetRes(msg);
        }
        else if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_STATUS)) {
            procToolStatusRes(msg);
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

        printReportHeader(index, msg);

        if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_STATUS)) {
            procToolStatusRpt(msg);
        }
        else {
            logger.warn("Unsupported cmd [{}]", msg.getHeader().getCmd());
        }
    }


    private void procToolResetRes(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_OK)) {
            // Success
            if (msg.getHeader().getAppId() == null) {
                logger.warn("Null appId in response message");
                return;
            }

            //
            // Nothing to do
            //
        }
        else {
            logger.warn("Undefined result [{}]", msg.getHeader().getResult());
        }

    }

    private void procToolStatusRes(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_OK)) {
            // Success
            if (msg.getHeader().getAppId() == null) {
                logger.warn("Null appId in response message");
                return;
            }

            //
            // Nothing to do
        }
        else {
            logger.warn("Undefined result [{}]", msg.getHeader().getResult());
        }
    }

    private void procToolStatusRpt(EngineReportMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (msg.getHeader().getAppId() == null) {
            logger.warn("Null appId in response message");
            return;
        }

        int count = (msg.getHeader().getValue() != null) ? Integer.parseInt(msg.getHeader().getValue()) : 0;
        logger.debug("Tool status. event [{}] active [{}]", msg.getHeader().getEvent(), count);

        Gson gson = new GsonBuilder().setLenient().create();

        ToolStatusRpt toolStatusRpt = gson.fromJson(msg.getBody(), ToolStatusRpt.class);

        if (toolStatusRpt != null) {
            if ((count > 0) && engineClient.isReset()) {
                engineClient.setReset(false);

                Integer[] active = toolStatusRpt.getActive();
                if (active != null) {
                    String appId = AppId.newId();

                    EngineProcToolResetReq toolResetReq = new EngineProcToolResetReq(appId);
                    toolResetReq.setData(active);

                    if (engineClient == null) {
                        logger.error("Engine not found");
                        return;
                    }

                    engineClient.pushSentQueue(appId, ToolResetReq.class, toolResetReq.getData());

                    if (!toolResetReq.send(index)) {
                        // ERROR
                        engineClient.removeSentQueue(appId);
                    }
                }
            }
        }
    }

}
