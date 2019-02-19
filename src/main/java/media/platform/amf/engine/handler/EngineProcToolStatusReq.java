package media.platform.amf.engine.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.handler.base.EngineOutgoingMessage;
import media.platform.amf.engine.messages.ToolResetReq;
import media.platform.amf.engine.messages.ToolStatusReq;
import media.platform.amf.session.SessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineProcToolStatusReq extends EngineOutgoingMessage {
    private static final Logger logger = LoggerFactory.getLogger(EngineProcToolStatusReq.class);

    private String appId;
    private ToolStatusReq data;

    public EngineProcToolStatusReq(String appId) {

        super("tool", "status", appId);
        this.appId = appId;
    }

    public void setData(Integer[] ids) {

        data = new ToolStatusReq();
        data.setIds(ids);

        setBody(data, ToolStatusReq.class);
    }

    public Object getData() {
        return data;
    }

    public boolean send(int index) {

        return sendTo(index, false);
    }
}
