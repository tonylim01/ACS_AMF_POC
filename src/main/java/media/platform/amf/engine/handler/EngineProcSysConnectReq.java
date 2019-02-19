package media.platform.amf.engine.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.config.AmfConfig;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.handler.base.EngineOutgoingMessage;
import media.platform.amf.engine.messages.SysConnectReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineProcSysConnectReq extends EngineOutgoingMessage {
    private static final Logger logger = LoggerFactory.getLogger(EngineProcSysConnectReq.class);

    private String appId;
    private SysConnectReq data;

    public EngineProcSysConnectReq(String appId, int index) {

        super("sys", "connect", appId);
        this.appId = appId;
        setData(index);
    }

    public void setData(int index) {

        UserConfig config = AppInstance.getInstance().getUserConfig();
        if (config == null) {
            return;
        }

        data = new SysConnectReq();
        data.setPort(config.getEngineLocalPort(index));

        setBody(data, SysConnectReq.class);

    }

    public Object getData() {
        return data;
    }

    public boolean send(int index) {

        return sendTo(index, false);
    }
}
