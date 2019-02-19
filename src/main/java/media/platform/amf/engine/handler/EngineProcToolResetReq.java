package media.platform.amf.engine.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.handler.base.EngineOutgoingMessage;
import media.platform.amf.engine.messages.ToolResetReq;
import media.platform.amf.engine.messages.common.NetIP4Address;
import media.platform.amf.engine.messages.common.StopCondition;
import media.platform.amf.session.SessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineProcToolResetReq extends EngineOutgoingMessage {
    private static final Logger logger = LoggerFactory.getLogger(EngineProcToolResetReq.class);

    private String appId;
    private ToolResetReq data;

    public EngineProcToolResetReq(String appId) {

        super("tool", "reset", appId);
        this.appId = appId;
    }

    public void setData(Integer[] ids) {

        data = new ToolResetReq();
        data.setIds(ids);

        setBody(data, ToolResetReq.class);
    }

    public Object getData() {
        return data;
    }

    public boolean send(int index) {

        return sendTo(index, false);
    }
}
