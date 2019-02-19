package media.platform.amf.engine.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import media.platform.amf.common.AppId;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.engine.messages.SysHeartbeatRes;
import media.platform.amf.engine.messages.ToolStatusReq;
import media.platform.amf.engine.types.EngineMessageType;
import media.platform.amf.engine.types.EngineResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineMessageHandlerSys extends BaseEngineMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(EngineMessageHandlerSys.class);

    private EngineClient engineClient;
    private int index;

    public EngineMessageHandlerSys(int index) {
        this.index = index;
        engineClient = EngineClient.getInstance(index);
    }

    public void handle(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.error("Invalid msg");
            return;
        }

        if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_CONNECT)) {

            printResponseHeader(index, msg);
            procSysConnectRes(msg);
        }
        else if (compareString(msg.getHeader().getCmd(), EngineMessageType.MSG_CMD_HEARTBEAT)) {

            procSysHeartbeatRes(msg);
        }

    }

    private void procSysConnectRes(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        if (compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_OK) ||
            compareString(msg.getHeader().getResult(), EngineMessageType.MSG_RESULT_SUCCESS)) {
            // Ok
            engineClient.setConnected(true);
        }
        else {
            // Error
            engineClient.setConnected(false);
        }

    }

    private void procSysHeartbeatRes(EngineResponseMessage msg) {
        if (msg == null || msg.getHeader() == null) {
            logger.warn("Null response message");
            return;
        }

        engineClient.checkHeartbeat(msg.getHeader().appId);

        Gson gson = new GsonBuilder().setLenient().create();

        SysHeartbeatRes heartbeatRes = gson.fromJson(msg.getBody(), SysHeartbeatRes.class);
        if (heartbeatRes != null && EngineManager.getInstance().isResourceChanged(index, heartbeatRes.getTotal(), heartbeatRes.getBusy(), heartbeatRes.getIdle())) {
            logger.debug("Engine [{}] Heart resource: total [{}] busy [{}] idle [{}]",
                    index, heartbeatRes.getTotal(), heartbeatRes.getBusy(), heartbeatRes.getIdle());

            int resourceBusy = EngineManager.getInstance().getBusy(index);
            EngineManager.getInstance().setResourceCount(index, heartbeatRes.getTotal(), heartbeatRes.getBusy(), heartbeatRes.getIdle());

            if (engineClient.isFirst()) {
                engineClient.setFirst(false);

               if (heartbeatRes.getBusy() > 0) {
                   String appId = AppId.newId();

                   EngineProcToolStatusReq toolStatusReq = new EngineProcToolStatusReq(appId);
                   toolStatusReq.setData(null);

                   if (engineClient == null) {
                       logger.error("Engine not found");
                       return;
                   }

                   engineClient.pushSentQueue(appId, ToolStatusReq.class, toolStatusReq.getData());

                   engineClient.setReset(true);
                   if (!toolStatusReq.send(index)) {
                       // ERROR
                       engineClient.removeSentQueue(appId);
                       engineClient.setReset(false);
                   }
               }
            }
            else if ((heartbeatRes.getBusy() == 0) && (resourceBusy > 0)) {
                int count = EngineManager.getInstance().clearTool(index);
                logger.warn("Force cleared tools. engine [{}] count [{}]", index, count);
            }

        }
    }
}
