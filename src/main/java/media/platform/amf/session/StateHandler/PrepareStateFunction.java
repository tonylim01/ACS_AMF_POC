package media.platform.amf.session.StateHandler;

import media.platform.amf.common.AppId;
import media.platform.amf.core.sdp.SdpInfo;
import media.platform.amf.AppInstance;
import media.platform.amf.config.SdpConfig;
import media.platform.amf.core.socket.JitterSender;
import media.platform.amf.core.socket.packets.Vocoder;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.engine.handler.EngineProcAudioCreateReq;
import media.platform.amf.engine.handler.EngineProcMixerCreateReq;
import media.platform.amf.engine.handler.EngineProcParAddReq;
import media.platform.amf.engine.messages.AudioCreateReq;
import media.platform.amf.engine.messages.ParAddReq;
import media.platform.amf.engine.messages.SysConnectReq;
import media.platform.amf.rmqif.messages.FileData;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionState;
import media.platform.amf.session.SessionStateManager;

public class PrepareStateFunction implements StateFunction {
    private static final Logger logger = LoggerFactory.getLogger(PrepareStateFunction.class);

    @Override
    public void run(SessionInfo sessionInfo, Object arg) {
        if (sessionInfo == null) {
            return;
        }

        logger.debug("{} PREPARE state", sessionInfo.getSessionId());

        if (sessionInfo.getServiceState() != SessionState.PREPARE) {
            sessionInfo.setServiceState(SessionState.PREPARE);
        }

        sessionInfo.setRtpReceivedTime(System.currentTimeMillis());

        if (sessionInfo.getSdpDeviceInfo() != null) {
            logger.debug("{} SDP payload {}", sessionInfo.getSessionId(), sessionInfo.getSdpDeviceInfo().getPayloadId());
        }
        else if (sessionInfo.getSdpInfo() != null) {
            logger.debug("{} SDP payload {}", sessionInfo.getSessionId(), sessionInfo.getSdpInfo().getPayloadId());
        }


        if (sessionInfo.isCaller()) {
            openCallerRelayResource(sessionInfo);

            if (!AppInstance.getInstance().getConfig().isRelayMode()) {
                sendMixerCreateReq(sessionInfo);
            }
        }
        else {
            openCalleeRelayResource(sessionInfo);
        }

        if (!AppInstance.getInstance().getConfig().isRelayMode()) {
            //
            // TODO
            // Waiting for mixer done
            //
            if (sessionInfo.getConferenceId() != null) {
                RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
                if ((roomInfo != null) && (!roomInfo.isMixerAvailable())) {
                    logger.debug("[{}] Waiting for mixer ready", sessionInfo.getSessionId());

                    if (roomInfo.waitReady(1000)) {
                        logger.debug("[{}] Mixer ready", sessionInfo.getSessionId());
                    }
                    else {
                        logger.warn("[{}] Mixer timeout", sessionInfo.getSessionId());
                    }

                }
            }

            sendAudioCreateReq(sessionInfo);

            if (!sessionInfo.isAudioCreated()) {
                logger.debug("[{}] Waiting for audio created", sessionInfo.getSessionId());

                if (sessionInfo.waitAudioCreated(1000)) {
                    logger.debug("[{}] Audio Ready", sessionInfo.getSessionId());
                }
                else {
                    logger.warn("[{}] Audio timeout", sessionInfo.getSessionId());
                }
            }

            sendParAddReq(sessionInfo);
        }

        logger.debug("[{}] End of Prepare", sessionInfo.getSessionId());

        sessionInfo.setEndOfState(SessionState.PREPARE);
    }


    /**
     * (Caller) -> (Relay) --> (Callee)
     *
     * @param sessionInfo
     * @return
     */
    private boolean openCallerRelayResource(SessionInfo sessionInfo) {

        if (sessionInfo == null) {
            return false;
        }

        logger.debug("[{}] Open caller relay resources", sessionInfo.getSessionId());

        SdpInfo sdpInfo = sessionInfo.getSdpDeviceInfo();
        if (sdpInfo == null) {
            if (sessionInfo.getSdpInfo() != null) {
                sdpInfo = sessionInfo.getSdpInfo();
            }
            else {
                return false;
            }
        }

        logger.debug("[{}] Open caller relay resources: device [{}:{}] -> [{}] -> engine [{}]", sessionInfo.getSessionId(),
                sdpInfo.getRemoteIp(), sdpInfo.getRemotePort(), sessionInfo.getSrcLocalPort(), sessionInfo.getEnginePort());

        sessionInfo.rtpClient = AppInstance.getInstance().getNettyRTPServer().addConnectPort(sdpInfo.getRemoteIp(), sdpInfo.getRemotePort());

        if (!AppInstance.getInstance().getConfig().isRelayMode()) {
            sessionInfo.udpClient = AppInstance.getInstance().getNettyUDPServer().addConnectPort("127.0.0.1", sessionInfo.getEnginePort());
        }

        openJitterSender(sessionInfo);

        return true;
    }

    /**
     * (Callee) -> (Relay) --> (Caller)
     *
     * @param sessionInfo
     * @return
     */
    private boolean openCalleeRelayResource(SessionInfo sessionInfo) {

        if (sessionInfo == null) {
            return false;
        }

        logger.debug("[{}] Open callee relay resources", sessionInfo.getSessionId());

        RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo( sessionInfo.getConferenceId());
        if (roomInfo == null) {
            logger.error("[{}] No roomInfo found", sessionInfo.getSessionId());
            return false;
        }

        SdpInfo sdpInfo = sessionInfo.getSdpDeviceInfo();
        if (sdpInfo == null) {
            if (sessionInfo.getSdpInfo() != null) {
                sdpInfo = sessionInfo.getSdpInfo();
            }
            else {
                logger.error("[{}] sdpInfo null", sessionInfo.getSessionId());
                return false;
            }
        }

        logger.debug("[{}] Open callee relay resources: device [{}:{}] -> [{}] -> engine [{}]", sessionInfo.getSessionId(),
                sdpInfo.getRemoteIp(), sdpInfo.getRemotePort(), sessionInfo.getSrcLocalPort(), sessionInfo.getEnginePort());

        //sessionInfo.channel = AppInstance.getInstance().getNettyRTPServer().addBindPort( sdpConfig.getLocalIpAddress(), sessionInfo.getSrcLocalPort());
        sessionInfo.rtpClient = AppInstance.getInstance().getNettyRTPServer().addConnectPort(sdpInfo.getRemoteIp(), sdpInfo.getRemotePort());

        if (!AppInstance.getInstance().getConfig().isRelayMode()) {
            sessionInfo.udpClient = AppInstance.getInstance().getNettyUDPServer().addConnectPort("127.0.0.1", sessionInfo.getEnginePort());
        }

        openJitterSender(sessionInfo);


        return true;
    }

    private void openJitterSender(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            return;
        }

        int vocoder = 0;

        SdpInfo sdpInfo = sessionInfo.getSdpDeviceInfo();
        if (sdpInfo == null) {
            if (sessionInfo.getSdpInfo() != null) {
                sdpInfo = sessionInfo.getSdpInfo();
            } else {
                logger.error("[{}] Null sdpInfo", sessionInfo.getSessionId());
                return;
            }
        }

        if (sdpInfo.getCodecStr() != null) {
            if (sdpInfo.getCodecStr().equals("AMR-WB")) {
                vocoder = Vocoder.VOCODER_AMR_WB;
            } else if (sdpInfo.getCodecStr().equals("AMR-NB")) {
                vocoder = Vocoder.VOCODER_AMR_NB;
            } else if (sdpInfo.getCodecStr().equals("EVS")) {
                vocoder = Vocoder.VOCODER_EVS;
            }

            if ((vocoder == Vocoder.VOCODER_AMR_NB) || (vocoder == Vocoder.VOCODER_AMR_WB)) {
                EngineManager.getInstance().setAmrUse(1);
            }
            else if (vocoder == Vocoder.VOCODER_EVS) {
                EngineManager.getInstance().setEvsUse(1);
            }
        }

        if (vocoder == 0) {
            switch (sdpInfo.getPayloadId()) {
                case 0:
                    vocoder = Vocoder.VOCODER_ULAW;
                    break;
                case 8:
                    vocoder = Vocoder.VOCODER_ALAW;
                    break;
                default:
                    break;
            }
        }

        if (vocoder == 0) {
            vocoder = Vocoder.VOCODER_ALAW;
        }

        int payloadId = sdpInfo.getPayloadId();
        //int payloadSize = (vocoder == Vocoder.VOCODER_AMR_WB) ? 320 : 160;
        int payloadSize = Vocoder.getPayloadSize(vocoder);

        logger.debug("[{}] codec [{}] vocoder [{}] payloadId [{}] payloadSize [{}]", sessionInfo.getSessionId(),
                sdpInfo.getCodecStr(), vocoder, payloadId, payloadSize);

        JitterSender rtpSender = new JitterSender(vocoder, Vocoder.MODE_NA, payloadId, 20, 3, payloadSize);
        rtpSender.setUdpClient(sessionInfo.getRtpClient());
        rtpSender.setSessionId(sessionInfo.getSessionId());
        //rtpSender.setRelay(AppInstance.getInstance().getConfig().isRelayMode());
        rtpSender.setRelay(true);   // TODO: Needs to change the name. 'relay' do not have a proper meaning
        rtpSender.setCaller(sessionInfo.isCaller());
        rtpSender.start();

        sessionInfo.setRtpSender(rtpSender);

        if (!AppInstance.getInstance().getConfig().isRelayMode()) {
            JitterSender udpSender = new JitterSender(vocoder, Vocoder.MODE_NA, payloadId, 20, 3, payloadSize);
            udpSender.setUdpClient(sessionInfo.getUdpClient());
            udpSender.setSessionId(sessionInfo.getSessionId());
            udpSender.setRelay(AppInstance.getInstance().getConfig().isRelayMode());
            udpSender.setCaller(sessionInfo.isCaller());
            udpSender.start();

            sessionInfo.setUdpSender(udpSender);

        }
    }

    private boolean openMixerResource(RoomInfo roomInfo, String sessionId) {
        if (roomInfo == null) {
            return false;
        }


        int groupId = roomInfo.getGroupId();

        if (groupId < 0) {
            return false;
        }

        logger.debug("({}) Allocates mixer on group [{}]", roomInfo.getRoomId(), groupId);


        return true;
    }

    private boolean openCallerResource(SessionInfo sessionInfo, RoomInfo roomInfo) {
        //SdpConfig sdpConfig = AppInstance.getInstance().getUserConfig().getSdpConfig();

        if (sessionInfo == null) {
            return false;
        }

        logger.debug("[{}] Allocates caller DSP resources", sessionInfo.getSessionId());

        String json;
        int groupId = roomInfo.getGroupId();
        int mixerId = roomInfo.getMixerId();

        if (groupId < 0 || mixerId < 0) {
            return false;
        }

        return true;
    }

    private boolean openCalleeResource(SessionInfo sessionInfo, RoomInfo roomInfo) {
        if (sessionInfo == null) {
            return false;
        }

        logger.debug("{} Allocates callee DSP resources", sessionInfo.getSessionId());

        String json;
        int groupId = roomInfo.getGroupId();
        int mixerId = roomInfo.getMixerId();

        if (groupId < 0 || mixerId < 0) {
            return false;
        }

        //AmfConfig config = AppInstance.getInstance().getConfig();


        return true;
    }

    private boolean openPlayResource(SessionInfo sessionInfo, RoomInfo roomInfo) {
        if (sessionInfo == null) {
            return false;
        }

        logger.debug("{} Allocates file play DSP resources", sessionInfo.getSessionId());

        String json;
        int groupId = roomInfo.getGroupId();
        int mixerId = roomInfo.getMixerId();

        if (groupId < 0 || mixerId < 0) {
            return false;
        }


        return true;
    }

    private boolean playDemoAudio(SessionInfo sessionInfo, RoomInfo roomInfo) {
        if (sessionInfo == null) {
            return false;
        }

        logger.debug("[{}] Play demo audio", sessionInfo.getSessionId());

        int groupId = roomInfo.getGroupId();
        int mixerId = roomInfo.getMixerId();

        if (groupId < 0 || mixerId < 0) {
            return false;
        }

        FileData file = new FileData();
        file.setChannel(FileData.CHANNEL_BGM);
        file.setPlayFile("Heize_rain_and.wav");
        SessionStateManager.getInstance().setState(sessionInfo.getSessionId(), SessionState.PLAY_START, file);

        return true;
    }

    private void sendAudioCreateReq(SessionInfo sessionInfo) {
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
        EngineProcAudioCreateReq audioCreateReq = new EngineProcAudioCreateReq(appId);
        audioCreateReq.setData(sessionInfo);

        engineClient.pushSentQueue(appId, AudioCreateReq.class, audioCreateReq.getData());
        if (sessionInfo.getSessionId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getSessionId());
        }

        if (!audioCreateReq.send(sessionInfo.getEngineId())) {
            // ERROR
//            EngineClient.getInstance().removeSentQueue(appId);
        }

        logger.info("[{}] -> (Engine-{}) AudioCreateReq. toolId [{}] port A[{}]-E[{}]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), sessionInfo.getEngineToolId(),
                sessionInfo.getDstLocalPort(), sessionInfo.getEnginePort());

    }

    private void sendParAddReq(SessionInfo sessionInfo) {
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
        EngineProcParAddReq parAddReq = new EngineProcParAddReq(appId);
        parAddReq.setData(sessionInfo);

        engineClient.pushSentQueue(appId, ParAddReq.class, parAddReq.getData());
        if (sessionInfo.getSessionId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getSessionId());
        }
        if (!parAddReq.send(sessionInfo.getEngineId())) {
            // ERROR
//            EngineClient.getInstance().removeSentQueue(appId);
        }

        logger.info("[{}] -> (Engine-{}) ParAddReq. toolId [{}]]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), sessionInfo.getEngineToolId());
    }

    private void sendMixerCreateReq(SessionInfo sessionInfo) {
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

        int mixerId = EngineManager.getInstance().getIdleToolId(sessionInfo.getEngineId());
        if (mixerId < 0) {
            // Error
            logger.error("[{}] No available tools", sessionInfo.getSessionId());
            return;
        }

        if (sessionInfo.getConferenceId() != null) {

            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null) {
                roomInfo.setMixerId(mixerId);
            }
        }

        sessionInfo.setMixerToolId(mixerId);

        EngineProcMixerCreateReq mixerCreateReq = new EngineProcMixerCreateReq(appId);
        mixerCreateReq.setData(sessionInfo, mixerId, 2);

        engineClient.pushSentQueue(appId, SysConnectReq.class, mixerCreateReq.getData());
        if (sessionInfo.getConferenceId() != null) {
            AppId.getInstance().push(appId, sessionInfo.getConferenceId());
        }

        if (!mixerCreateReq.send(sessionInfo.getEngineId())) {
            // ERROR
//            EngineClient.getInstance().removeSentQueue(appId);
        }

        logger.info("[{}] -> (Engine-{}) MixerCreateReq. toolId [{}]", sessionInfo.getSessionId(),
                sessionInfo.getEngineId(), mixerId);
    }
 }
