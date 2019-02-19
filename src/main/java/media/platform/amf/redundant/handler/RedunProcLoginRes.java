package media.platform.amf.redundant.handler;

import media.platform.amf.common.JsonMessage;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.rmqif.messages.LogInRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedunProcLoginRes implements RedunProcMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedunProcLoginRes.class);

    @Override
    public boolean handle(String body) {
        if (body == null) {
            logger.error("Null body");
            return false;
        }

        LogInRes loginRes = (LogInRes) new JsonMessage(LogInRes.class).parse(body);
        if (loginRes == null) {
            logger.warn("Invalid loginRes message. [{}]", body);
            return false;
        }

        logger.debug("<- Redundant (LoginRes): amr [{}] evs [{}]",
                loginRes.getAmrLicenseCount(), loginRes.getEvsLicenseCount());


        if (loginRes.getAmrLicenseCount() > 0) {
            EngineManager.getInstance().setAmrTotal(loginRes.getAmrLicenseCount());
        }

        if (loginRes.getEvsLicenseCount() > 0) {
            EngineManager.getInstance().setEvsTotal(loginRes.getEvsLicenseCount());
        }

        return true;
    }
}
