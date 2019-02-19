package media.platform.amf.session.StateHandler;

import media.platform.amf.AppInstance;
import media.platform.amf.common.AppId;
import media.platform.amf.core.socket.packets.Vocoder;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.engine.handler.EngineProcAudioCreateReq;
import media.platform.amf.engine.handler.EngineProcAudioDeleteReq;
import media.platform.amf.engine.handler.EngineProcMixerDeleteReq;
import media.platform.amf.engine.messages.AudioDeleteReq;
import media.platform.amf.engine.messages.SysConnectReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import media.platform.amf.service.ServiceManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionState;

import java.util.UUID;

public class IdleStateFunction implements StateFunction {
    private static final Logger logger = LoggerFactory.getLogger(IdleStateFunction.class);

    @Override
    public void run(SessionInfo sessionInfo, Object arg) {
        if (sessionInfo == null) {
            return;
        }

        logger.debug("[{}] IdleStateFunction", sessionInfo.getSessionId());

//        BiUdpRelayManager.getInstance().close(sessionInfo.getSessionId());

        if (sessionInfo.getServiceState() != SessionState.IDLE) {
            sessionInfo.setServiceState(SessionState.IDLE);
        }

        if (!AppInstance.getInstance().getConfig().isRelayMode()) {

            if (sessionInfo.getEngineToolId() > 0) {
                sendAudioDeleteReq(sessionInfo);
                EngineManager.getInstance().freeTool(sessionInfo.getEngineId(), sessionInfo.getEngineToolId());
            }
            if (sessionInfo.getMixerToolId() > 0) {
                EngineManager.getInstance().freeTool(sessionInfo.getEngineId(), sessionInfo.getMixerToolId());
            }

            if (sessionInfo.isCaller()) {
                sendMixerDeleteReq(sessionInfo);
            }
        }

        if (sessionInfo.getRtpSender() != null) {
            if ((sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_NB) ||
                    (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_WB)) {
                EngineManager.getInstance().setAmrUse(-1);
            } else if (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_EVS) {
                EngineManager.getInstance().setEvsUse(-1);
            }
        }

//        if (sessionInfo.rtpChannel != null) {
//            AppInstance.getInstance().getNettyRTPServer().removeBindPort(sessionInfo.rtpChannel);
//        }
//        if (sessionInfo.udpChannel != null) {
//            AppInstance.getInstance().getNettyUDPServer().removeBindPort(sessionInfo.udpChannel);
//        }

        ServiceManager.getInstance().releaseResource(sessionInfo.getSessionId());

        sessionInfo.setEndOfState(SessionState.IDLE);

    }

    private void sendAudioDeleteReq(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            logger.error("Null sessionInfo");
            return;
        }

        EngineClient engineClient = EngineClient.getInstance(sessionInfo.getEngineId());
        if (engineClient == null) {
            logger.error("[{}] Null engineClient", sessionInfo.getSessionId());
            return;
        }

        String appId = AppId.newId();
        EngineProcAudioDeleteReq audioDeleteReq = new EngineProcAudioDeleteReq(appId);
        audioDeleteReq.setData(sessionInfo);

        if (audioDeleteReq.send(sessionInfo.getEngineId())) {
            engineClient.pushSentQueue(appId, AudioDeleteReq.class, audioDeleteReq.getData());
        }

        logger.info("[{}] -> (Engine-{}) AudioDeleteReq. toolId [{}]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), sessionInfo.getEngineToolId());
    }

    private void sendMixerDeleteReq(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            logger.error("Null sessionInfo");
            return;
        }

        EngineClient engineClient = EngineClient.getInstance(sessionInfo.getEngineId());
        if (engineClient == null) {
            logger.error("[{}] Null engineClient", sessionInfo.getSessionId());
            return;
        }

        String appId = AppId.newId();
        EngineProcMixerDeleteReq mixerDeleteReq = new EngineProcMixerDeleteReq(appId);
        mixerDeleteReq.setData(sessionInfo);

        if (mixerDeleteReq.send(sessionInfo.getEngineId())) {
            engineClient.pushSentQueue(appId, SysConnectReq.class, mixerDeleteReq.getData());
        }

        logger.info("[{}] -> (Engine-{}) MixerDeleteReq. toolId [{}]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), sessionInfo.getMixerToolId());

    }
}
