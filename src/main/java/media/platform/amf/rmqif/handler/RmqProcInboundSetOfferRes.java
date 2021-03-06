/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqProcInboundSetOfferRes.java
 * @author Tony Lim
 *
 */


package media.platform.amf.rmqif.handler;

import media.platform.amf.AppInstance;
import media.platform.amf.config.SdpConfig;
import media.platform.amf.core.sdp.*;
import media.platform.amf.oam.UaOamManager;
import media.platform.amf.oam.UaTraceMsg;
import media.platform.amf.rmqif.handler.base.RmqOutgoingMessage;
import media.platform.amf.rmqif.messages.InboundSetOfferRes;
import media.platform.amf.rmqif.types.RmqMessageType;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RmqProcInboundSetOfferRes extends RmqOutgoingMessage {

    private static final Logger logger = LoggerFactory.getLogger(RmqProcInboundSetOfferRes.class);

    public RmqProcInboundSetOfferRes(String sessionId, String transactionId) {
        super(sessionId, transactionId);
        setType(RmqMessageType.RMQ_MSG_STR_INBOUND_SET_OFFER_RES);
    }

    /**
     * Makes a response body and sends the message to MCUD
     * @return
     */
    public boolean send(String queueName) {

        SessionInfo sessionInfo = checkAndGetSession(getSessionId());
        if (sessionInfo == null) {
            return sendTo(queueName);
        }

        UaOamManager.sendTrace(sessionInfo.isCaller() ? UaTraceMsg.DIR_IN : UaTraceMsg.DIR_OUT,
                getHeader().getType(),
                null, queueName,
                sessionInfo.getFromNo(), sessionInfo.getToNo(),
                "");

        String sdpStr = makeSdp(sessionInfo);
        if (sdpStr == null) {
            logger.error("[{}] Cannot make SDP", getSessionId());

            setReason(RmqMessageType.RMQ_MSG_COMMON_REASON_CODE_FAILURE, "SDP FAILURE");
            return sendTo(queueName);
        }

        InboundSetOfferRes res = new InboundSetOfferRes();
        res.setSdp(sdpStr);
        res.setOutbound(sessionInfo.getOutbound());

        setBody(res, InboundSetOfferRes.class);

        return sendTo(queueName);
    }

    /**
     * Makes a SDP body from the remote SDP media attributes
     * @param sessionInfo
     * @return
     */
    private String makeSdp(SessionInfo sessionInfo) {

        if (sessionInfo == null) {
            logger.error("No session found");
            return null;
        }

        RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
        if (roomInfo == null) {
            logger.error("[{}] Room not found for cnfid=[{}]", sessionInfo.getSessionId(), sessionInfo.getConferenceId());
            return null;
        }

        //AmfConfig config = AppInstance.getInstance().getConfig();
        SdpConfig sdpConfig = AppInstance.getInstance().getUserConfig().getSdpConfig();

        SdpBuilder builder = new SdpBuilder();
        builder.setHost(sdpConfig.getLocalHost());

//        builder.setLocalIpAddress(config.getSurfIp());
        builder.setLocalIpAddress(sdpConfig.getLocalIpAddress());
        builder.setSessionName("-");      // TODO

        SdpAttribute attr = selectSdp(sessionInfo);
        if (attr != null) {

            int localPort = sessionInfo.getSrcLocalPort();
            builder.setLocalPort(localPort);

            builder.addRtpAttribute(attr.getPayloadId(), attr.getDescription());

            SdpAttribute dtmfAttr;
            if (sessionInfo.getSdpInfo() != null && sessionInfo.getSdpInfo().getPayload2833() > 0) {
                dtmfAttr = getTelephonyEvent(sessionInfo, sessionInfo.getSdpInfo().getPayload2833());
            }
            else {
                dtmfAttr = getTelephonyEvent(sessionInfo);
            }

            if (dtmfAttr != null) {
                builder.addRtpAttribute(dtmfAttr.getPayloadId(), dtmfAttr.getDescription());
                if (sessionInfo.getSdpInfo() != null &&
                        sessionInfo.getSdpInfo().getPayload2833() != dtmfAttr.getPayloadId()) {
                    logger.info("[{}] Update 2833 payload {} -> {}", sessionInfo.getSessionId(),
                            sessionInfo.getSdpInfo().getPayload2833(),  dtmfAttr.getPayloadId());

                    sessionInfo.getSdpInfo().setPayload2833(dtmfAttr.getPayloadId());
                }
            }

            logger.debug("[{}] Select SDP: payload {} local port {}", sessionInfo.getSessionId(),
                    attr.getPayloadId(), localPort);

            for (SdpAttribute sdpAttribute: sessionInfo.getSdpInfo().getAttributes()) {
                if (sdpAttribute.getName() == null) {
                    continue;
                }

                boolean isAppend = false;
                if (sdpAttribute.getName().equals(SdpAttribute.NAME_RTPMAP)) {
                    logger.debug("[{}] makeSDP: payload {} dtmf {} attr {}", sessionInfo.getSessionId(),
                            attr.getPayloadId(),
                            (dtmfAttr != null) ? dtmfAttr.getPayloadId() : "-",
                            sdpAttribute.getPayloadId());

                    if (dtmfAttr != null && sdpAttribute.getDescription() != null &&
                            sdpAttribute.getDescription().contains(String.valueOf(dtmfAttr.getPayloadId()))) {
                        isAppend = true;
                    }
//                    else if (sdpAttribute.getPayloadId() == attr.getPayloadId()) {
//                        isAppend = true;
//                    }
                }
                else if (sdpAttribute.getName().equals(SdpAttribute.NAME_SENDRECV)) {
                    isAppend = true;
                }
                else if (sdpAttribute.getName().equals(SdpAttribute.NAME_FMTP)) {
                    if (sdpAttribute.getDescription() != null) {
                        if (dtmfAttr != null &&
                                sdpAttribute.getDescription().contains(String.valueOf(dtmfAttr.getPayloadId()))) {
                            isAppend = true;
                        }
                        else if (sdpAttribute.getDescription().contains(String.valueOf(attr.getPayloadId()))) {
//                            sdpAttribute.setDescription(String.format("%d mode-set=8; octet-align=1", attr.getPayloadId()));
                            isAppend = true;
                        }

                    }
                }

                if (isAppend) {
                    builder.addGeneralAttribute( SdpUtil.getAttributeString( sdpAttribute));
                }
            }
        }
        else {  // Outbound case

//            int localPort = SurfChannelManager.getUdpPort(roomInfo.getGroupId(), SurfChannelManager.TOOL_ID_CD);
            int localPort = sessionInfo.getSrcLocalPort();
            builder.setLocalPort(localPort);

            List<String> mediaList = null;

            int priorityIndex = 0;
            String peerSessionId = roomInfo.getOtherSession(sessionInfo.getSessionId());
            if (peerSessionId != null) {
                SessionInfo peerSessionInfo = SessionManager.getInstance().getSession(peerSessionId);
                /*
                if ((peerSessionInfo != null) && (peerSessionInfo.getSdpInfo() != null) &&
                        (peerSessionInfo.getSdpInfo().getPriority() > 0)) {
                    // Not 1st priority codec
                    priorityIndex = peerSessionInfo.getSdpInfo().getPriority();
                    logger.info("[{}] Peer codec is not the 1st priority one [{}]", sessionInfo.getSessionId(), priorityIndex);
                }
                */

                //
                // TODO
                //
                if (peerSessionInfo.getSdpInfo().getMediaList() != null) {

                }

                if ((peerSessionInfo != null) && (peerSessionInfo.getSdpInfo() != null) &&
                        (peerSessionInfo.getSdpInfo().getMediaList() != null)) {
                    mediaList = peerSessionInfo.getSdpInfo().getMediaList();
                }
            }

            if (mediaList == null) {
                List<String> mediaPriorities = AppInstance.getInstance().getUserConfig().getMediaPriorities();

                if (mediaPriorities != null && mediaPriorities.size() > 0) {
                    mediaList = new ArrayList<>();

                    for (int i = priorityIndex; i < mediaPriorities.size(); i++) {
                        String priorityCodec = mediaPriorities.get(i);

                        if (priorityCodec != null) {
                            mediaList.add(priorityCodec);
                        }
                    }
                }
            }

            if (mediaList != null) {
                for (String priorityCodec: mediaList) {

                    if (priorityCodec == null) {
                        continue;
                    }

                    List<String> codecAttributes = sdpConfig.getCodecAttribute(priorityCodec);
                    if (codecAttributes == null) {
                        logger.debug("[{}] Build codec [{}] has no attribute", sessionInfo.getSessionId(), priorityCodec);

                        int payloadId = SdpCodec.getPayloadId(SdpCodec.getCodecId(priorityCodec));
                        builder.addRtpAttribute(payloadId, null);
                        continue;
                    }

                    logger.debug("[{}] Media priority codec [{}] attr [{}]", sessionInfo.getSessionId(), priorityCodec, codecAttributes.size());

                    for(String desc: codecAttributes) {
                        if (desc == null) {
                            logger.warn("[{}] Null desc in sdp config", sessionInfo.getSessionId());
                            continue;
                        }

                        attr = SdpUtil.parseAttribute(desc);
                        if (attr == null) {
                            logger.warn("[{}] Null attr in sdp config desc [{}]", sessionInfo.getSessionId(), desc);
                            continue;
                        }

                        logger.warn("[{}] Build codec [{}] sdp. payload [{}] desc [{}]", sessionInfo.getSessionId(), priorityCodec, attr.getPayloadId(), attr.getDescription());
                        if (attr.getPayloadId() != SdpAttribute.PAYLOADID_NONE) {
                            builder.addRtpAttribute(attr.getPayloadId(), attr.getDescription());
                        }
                        else {
                            builder.addGeneralAttribute(attr.getDescription());
                        }
                    }
                }
            }

            /*
            List<String> mediaPriorities = AppInstance.getInstance().getUserConfig().getMediaPriorities();

            if (mediaPriorities != null && mediaPriorities.size() > 0) {
                for (int i = priorityIndex; i < mediaPriorities.size(); i++) {
                    String priorityCodec = mediaPriorities.get(i);

                    if (priorityCodec == null) {
                        continue;
                    }

                    List<String> codecAttributes = sdpConfig.getCodecAttribute(priorityCodec);
                    if (codecAttributes == null) {
                        logger.debug("[{}] Build codec [{}] has no attribute", sessionInfo.getSessionId(), priorityCodec);

                        int payloadId = SdpCodec.getPayloadId(SdpCodec.getCodecId(priorityCodec));
                        builder.addRtpAttribute(payloadId, null);
                        continue;
                    }

                    logger.debug("[{}] Media priority idx [{}] attr [{}]", sessionInfo.getSessionId(), i, codecAttributes.size());

                    for(String desc: codecAttributes) {
                        if (desc == null) {
                            logger.warn("[{}] Null desc in sdp config", sessionInfo.getSessionId());
                            continue;
                        }

                        attr = SdpUtil.parseAttribute(desc);
                        if (attr == null) {
                            logger.warn("[{}] Null attr in sdp config desc [{}]", sessionInfo.getSessionId(), desc);
                            continue;
                        }

                        logger.warn("[{}] Build codec [{}] sdp. payload [{}] desc [{}]", sessionInfo.getSessionId(), priorityCodec, attr.getPayloadId(), attr.getDescription());
                        if (attr.getPayloadId() != SdpAttribute.PAYLOADID_NONE) {
                            builder.addRtpAttribute(attr.getPayloadId(), attr.getDescription());
                        }
                        else {
                            builder.addGeneralAttribute(attr.getDescription());
                        }
                    }
                }
            }
            */

            if (sdpConfig.getAttributes() != null) {
                for (String desc: sdpConfig.getAttributes()) {
                    if (desc == null) {
                        logger.warn("[{}] Null desc in sdp config", sessionInfo.getSessionId());
                        continue;
                    }

                    attr = SdpUtil.parseAttribute(desc);
                    if (attr == null) {
                        logger.warn("[{}] Null attr in sdp config desc [{}]", sessionInfo.getSessionId(), desc);
                        continue;
                    }

                    logger.warn("[{}] Build sdp. payload [{}] desc [{}]", sessionInfo.getSessionId(), attr.getPayloadId(), attr.getDescription());
                    if (attr.getPayloadId() != SdpAttribute.PAYLOADID_NONE) {
                        builder.addRtpAttribute(attr.getPayloadId(), attr.getDescription());
                    } else {
                        builder.addGeneralAttribute(attr.getDescription());
                    }
                }
            }
        }


        return builder.build();
    }

    /**
     * Selects a proper payload comparing to the config's priorities
     * @param sessionInfo
     * @return
     */
    private SdpAttribute selectSdp(SessionInfo sessionInfo) {
        // Compares the SDP media list with the local priorities which read from the config
        SdpAttribute attr = null;
        SdpInfo sdpInfo = sessionInfo.getSdpInfo();

        if (sdpInfo == null) {
            // Outbound case
            return null;
        }

//        SdpParser.selectAttribute(sdpInfo);

        boolean isFound = false;

        if (sdpInfo.getAttributes() != null) {

            List<String> mediaPriorities = AppInstance.getInstance().getUserConfig().getMediaPriorities();

            if (mediaPriorities != null && mediaPriorities.size() > 0) {
                for (String priorityCodec : mediaPriorities) {

                    int codecId = SdpCodec.getCodecId(priorityCodec);
                    if (codecId == SdpCodec.CODEC_UNKNOWN) {
                        continue;
                    }

                    attr = sdpInfo.getAttributeByCodec(codecId);
//                    logger.debug("[{}] Priority codec [{}] id [{}] attr [{}]", sessionInfo.getSessionId(), priorityCodec, codecId,
//                            (attr != null) ? "found" : "not found");

                    if (attr == null) {
                        attr = sdpInfo.getAttribute(SdpCodec.getPayloadId(codecId));
//                        logger.debug("[{}] Priority codec [{}] id [{}] attr [{}] payloadId [{}]", sessionInfo.getSessionId(), priorityCodec, codecId,
//                                (attr != null) ? "found" : "not found", SdpCodec.getPayloadId(codecId));
                    }

                    if (attr != null) {
                        logger.debug("[{}] Priority codec [{}] found", sessionInfo.getSessionId(), attr.getPayloadId());
                        isFound = true;
                        break;
                    }
                }
            }
            else {
                logger.warn("No media priority defined");
            }

        }

        if (!isFound) {
            // Select the 1st codec in the received sdp
            attr = sdpInfo.getAttributeByIndex(0);
            logger.debug("[{}] Priority codec not found. Get 1st codec [{}]", sessionInfo.getSessionId(),
                    (attr != null) ? attr.getPayloadId() : -1);
        }

        if (attr != null) {
            logger.debug("[{}] Select payload [{}] codec [{}] samplerate [{}]", sessionInfo.getSessionId(),
                    attr.getPayloadId(), attr.getCodec(), attr.getSampleRate());

            sdpInfo.setCodecStr(attr.getCodec());
            sdpInfo.setSampleRate(attr.getSampleRate());
            sdpInfo.setPayloadId(attr.getPayloadId());
        }

        return attr;
    }

    private SdpAttribute getTelephonyEvent(SessionInfo sessionInfo) {
        SdpAttribute attr = null;
        SdpInfo sdpInfo = sessionInfo.getSdpInfo();

        if (sdpInfo == null) {
            // Outbound case
            return null;
        }

        if (sdpInfo.getAttributes() != null) {
            List<SdpAttribute> attributes = sdpInfo.getAttributes();
            for (SdpAttribute attribute: attributes) {
                if (attribute.getDescription() != null &&
                        attribute.getDescription().startsWith(SdpAttribute.DESC_TELEPHONY_EVENT)) {
                    if (attribute.getPayloadId() > 0) {
                        attr = attribute;
                        break;
                    }
                }
            }
        }

        return attr;
    }

    private SdpAttribute getTelephonyEvent(SessionInfo sessionInfo, int defaultValue) {
        SdpAttribute attr = null;
        SdpInfo sdpInfo = sessionInfo.getSdpInfo();

        if (sdpInfo == null) {
            // Outbound case
            return null;
        }

        if (sdpInfo.getAttributes() != null) {
            List<SdpAttribute> attributes = sdpInfo.getAttributes();
            for (SdpAttribute attribute: attributes) {
                if (attribute.getDescription() != null &&
                        attribute.getDescription().startsWith(SdpAttribute.DESC_TELEPHONY_EVENT)) {
                    if (attribute.getPayloadId() > 0) {
                        logger.debug("[{}] 2833 payload [{}] found. default [{}]", sessionInfo.getSessionId(), attribute.getPayloadId(), defaultValue);
                        attr = attribute;
                        if (attribute.getPayloadId() == defaultValue) {
                            break;
                        }
                    }
                }
            }
        }

        return attr;
    }
}
