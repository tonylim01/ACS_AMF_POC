/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RtpInboundHandler.java
 * @author Tony Lim
 *
 */
package media.platform.amf.rtpcore.core.rtp.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import media.platform.amf.AppInstance;
import media.platform.amf.core.socket.packets.Vocoder;
import media.platform.amf.engine.handler.EngineProcFilePlayReq;
import media.platform.amf.oam.StatManager;
import media.platform.amf.rmqif.handler.RmqProcDtmfDetectReq;
import media.platform.amf.room.RoomInfo;
import media.platform.amf.room.RoomManager;
import media.platform.amf.service.AudioFileReader;
import media.platform.amf.session.SessionManager;
import media.platform.amf.rtpcore.core.rtp.rtp.RTPInput;
import media.platform.amf.rtpcore.core.sdp.format.RTPFormats;
import media.platform.amf.rtpcore.core.spi.ConnectionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.rtpcore.core.rtp.rtp.RtpPacket;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetAddress;
import java.util.UUID;

public class RtpInboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger( RtpInboundHandler.class);

    private final RtpInboundHandlerGlobalContext context;
    private final RtpInboundHandlerFsm fsm;

    public RtpInboundHandler(RtpInboundHandlerGlobalContext context) {
        this.context = context;
        this.fsm = RtpInboundHandlerFsmBuilder.INSTANCE.build(context);

        this.isActive();
        if(!this.isActive()) {
            this.fsm.start();
        }
    }

    public void activate() {
        if(!this.isActive()) {
            this.fsm.start();
        }
    }

    public void deactivate() {
        if(this.isActive()) {
            this.fsm.fire(RtpInboundHandlerEvent.DEACTIVATE);
        }
    }
    
    public boolean isActive() {
        return RtpInboundHandlerState.ACTIVATED.equals(this.fsm.getCurrentState());
    }
    
    public void updateMode(ConnectionMode mode) {
        switch (mode) {
            case INACTIVE:
            case SEND_ONLY:
                this.context.setLoopable(false);
                this.context.setReceivable(false);
                break;

            case RECV_ONLY:
                this.context.setLoopable(false);
                this.context.setReceivable(true);
                break;

            case SEND_RECV:
            case CONFERENCE:
                this.context.setLoopable(false);
                this.context.setReceivable(true);
                break;

            case NETWORK_LOOPBACK:
                this.context.setLoopable(true);
                this.context.setReceivable(false);
                break;

            default:
                this.context.setLoopable(false);
                this.context.setReceivable(false);
                break;
        }
    }
    
    public void setFormatMap(RTPFormats formats) {
        this.context.setFormats(formats);
    }

    public void useJitterBuffer(boolean use) {
        this.context.getJitterBuffer().setInUse(use);
    }

    public RTPInput getRtpInput() {
        return context.getRtpInput();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {

        //InetAddress srcAddr = msg.sender().getAddress();
        ByteBuf buf = msg.content();

        int rcvPktLength = buf.readableBytes();
        byte[] rcvPktBuf = new byte[rcvPktLength];
        buf.readBytes(rcvPktBuf);

        //RtpPacket rtpPacket = new RtpPacket( RtpPacket.RTP_PACKET_MAX_SIZE, true);
        RtpPacket rtpPacket = new RtpPacket(rcvPktLength, true);
        rtpPacket.getBuffer().put(rcvPktBuf, 0, rcvPktLength);

        //logger.debug("<- ({}:{}) {}", srcAddr.toString(), msg.sender().getPort(), rtpPacket.toString());

        String adddress = ctx.channel().localAddress().toString();
        int port = adddress.lastIndexOf(":");
        String temp = adddress.substring(port + 1, adddress.length());

        SessionManager sessionManager = SessionManager.getInstance();
        SessionInfo sessionInfo = sessionManager.getSrcLocalPort(Integer.parseInt(temp));

        if (sessionInfo == null) {
            return;
        }

        sessionInfo.setRtpReceivedTime(System.currentTimeMillis());

        int version = rtpPacket.getVersion();
        if (version == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped RTP v0 packet.");
            }
            rtpPacket.getBuffer().clear();
            return;
        }

        // Check if channel can receive traffic
        boolean canReceive = (context.isReceivable() || context.isLoopable());

//        logger.debug("RTP canReceive " + canReceive + " packet.");

        if (!canReceive) {
            if (logger.isDebugEnabled()) {
                logger.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped packet because channel mode does not allow to receive traffic.");
            }
            rtpPacket.getBuffer().clear();
            return;
        }

//       logger.debug("getRtpInput : " + context.getStatistics());

        // Check if packet is not empty
        boolean hasData = (rtpPacket.getLength() > 0);
        if (!hasData) {
            if (logger.isDebugEnabled()) {
                logger.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped packet because payload was empty.");
            }
            rtpPacket.getBuffer().clear();
            return;
        }

//        logger.debug("[{}] rtp payload {} size {} ref {}", sessionInfo.getSessionId(), rtpPacket.getPayloadType(), rtpPacket.getLength(), sessionInfo.getPayload2833());

        // Detect 2833
        if (sessionInfo.getPayload2833() > 0 && rtpPacket.getPayloadType() == sessionInfo.getPayload2833()) {
            // 2833 detected
            // TODO

            handle2833Dtmf(sessionInfo, rtpPacket, rcvPktLength - rtpPacket.getPayloadLength());

            rtpPacket.getBuffer().clear();
            return;
        }

        /*
        // Process incoming packet
        RtpInboundHandlerPacketReceivedContext txContext = new RtpInboundHandlerPacketReceivedContext(rtpPacket);
        this.fsm.fire(RtpInboundHandlerEvent.PACKET_RECEIVED, txContext);
        */

        boolean toOtherSession = false;

        if (sessionInfo.getRtpSender() != null) {
            int statType = 0;
            switch (sessionInfo.getRtpSender().getVocoder()) {
                case Vocoder.VOCODER_AMR_WB:
                case Vocoder.VOCODER_AMR_NB:
                    statType = sessionInfo.isCaller() ? StatManager.RTP_AMR_IN : StatManager.RTP_AMR_OUT;
                    break;
                case Vocoder.VOCODER_EVS:
                    statType = sessionInfo.isCaller() ? StatManager.RTP_EVS_IN : StatManager.RTP_EVS_OUT;
                    break;
                default:
                    statType = sessionInfo.isCaller() ? StatManager.RTP_ETC_IN : StatManager.RTP_ETC_OUT;
                    break;
            }

            StatManager.getInstance().incCount(statType);
        }

        if (sessionInfo.getConferenceId() != null) {

            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null) {

                if (AppInstance.getInstance().getConfig().isRelayMode()) {
                    String otherSessionId = roomInfo.getOtherSession(sessionInfo.getSessionId());
                    if (otherSessionId != null) {
                        SessionInfo otherSession = SessionManager.findSession(otherSessionId);
                        if (otherSession != null) {

                            byte[] payload = new byte[rtpPacket.getPayloadLength()];
                            rtpPacket.readRegionToBuff(rcvPktLength - rtpPacket.getPayloadLength(), rtpPacket.getPayloadLength(), payload);

                            if (otherSession.getRtpSender() != null) {
                                otherSession.getRtpSender().put(rtpPacket.getSeqNumber(), payload);
                            }

                            payload = null;
                        }
                    }
                }
                else {  // Sends to engine
                    byte[] payload = new byte[rtpPacket.getPayloadLength()];
                    rtpPacket.readRegionToBuff(rcvPktLength - rtpPacket.getPayloadLength(), rtpPacket.getPayloadLength(), payload);

                    if (sessionInfo.getUdpSender() != null) {
                        sessionInfo.getUdpSender().put(rtpPacket.getSeqNumber(), payload);
                    }
                }
            }
        }

        /* TEST CODE: TO PLAY AN AUDIO FILE
        if (!toOtherSession && AppInstance.getInstance().getConfig().isTest() == false) {
            if (sessionInfo.getRtpPacket() == null) {

                RtpPacket sentPacket = new RtpPacket(rcvPktLength, true);
                sessionInfo.setRtpPacket(sentPacket);

                logger.info("Jitter vocoder {}", sessionInfo.getRtpSender().getVocoder());

                String audioFilename = AppInstance.getInstance().getPromptConfig().getWaitingPrompt(sessionInfo.getRtpSender().getVocoder());
                if (audioFilename == null) {
                    audioFilename = "test.alaw";
                }

                AudioFileReader fileReader = new AudioFileReader(audioFilename);
                fileReader.load();

                if (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_WB) {
                    byte[] header = new byte[9];    // #!AMR-WB\a
                    fileReader.get(header, header.length);
                    header = null;
                }
                else if (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_NB) {
                    byte[] header = new byte[6];    // #!AMR\a
                    fileReader.get(header, header.length);
                    header = null;
                }

                sessionInfo.setFileReader(fileReader);
            }
            else {

                byte[] payload = null;

                if (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_WB) {
                    payload = sessionInfo.getFileReader().getAMRWBPayload();
                }
                else if (sessionInfo.getRtpSender().getVocoder() == Vocoder.VOCODER_AMR_NB) {
                    payload = sessionInfo.getFileReader().getAMRNBPayload();
                }
                else {
                    payload = new byte[rtpPacket.getPayloadLength()];
                    sessionInfo.getFileReader().get(payload, rtpPacket.getPayloadLength());
                }


                if (payload != null) {
                    sessionInfo.getRtpSender().put(-1, payload);
                    payload = null;
                }
            }

        }
        END OF TEST CODE */


        // Relay RTP Packet
        //sessionInfo.rtpClient.send( rcvPktBuf );
        //sessionInfo.rtpClient.send(sessionInfo.getRtpPacket().getRawData());

        //logger.debug( "-> UDP ({}:{}) size={}", sessionInfo.getSdpDeviceInfo().getRemoteIp(), sessionInfo.getSdpDeviceInfo().getRemotePort(), rcvPktBuf.length);

        rtpPacket.getBuffer().clear();
        rtpPacket = null;
    }

    private void handle2833Dtmf(SessionInfo sessionInfo, RtpPacket rtpPacket, int offset) {
        if (sessionInfo == null || rtpPacket == null) {
            return;
        }

        byte[] payload = new byte[rtpPacket.getPayloadLength()];
        rtpPacket.readRegionToBuff(offset, rtpPacket.getPayloadLength(), payload);

        int dtmf = payload[0] & 0xff;
        boolean dtmfEnd = ((payload[1] & 0x80) > 0);

//        logger.info("[{}] 2833 detected. dtmf [{}] end [{}]", sessionInfo.getSessionId(), dtmf, dtmfEnd);
        boolean newDtmf;

        if (dtmf != sessionInfo.getLastDtmf()) {
            newDtmf = true;
            sessionInfo.setLastDtmfEnd(false);
        }
        else if (sessionInfo.isLastDtmfEnd() && !dtmfEnd) {
            newDtmf = true;
            sessionInfo.setLastDtmfEnd(false);
        }
        else {
            newDtmf = false;
            if (!sessionInfo.isLastDtmfEnd() && dtmfEnd) {
                sessionInfo.setLastDtmfEnd(true);
            }
        }

        if (!newDtmf) {
            return;
        }

        sessionInfo.setLastDtmf(dtmf);
        logger.info("[{}] 2833 detected. dtmf [{}]", sessionInfo.getSessionId(), dtmf);

        if (sessionInfo.getRemoteRmqName() == null) {
            logger.warn("[{}} 2833 ignored. No target queue", sessionInfo.getSessionId());
            return;
        }

        if (sessionInfo.getConferenceId() != null) {
            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null && roomInfo.getAwfQueueName() != null) {
                RmqProcDtmfDetectReq detectReq = new RmqProcDtmfDetectReq((roomInfo.getAwfCallId() != null) ? roomInfo.getAwfCallId() : sessionInfo.getSessionId(),
                        UUID.randomUUID().toString());
                detectReq.setDtmfInfo(sessionInfo.isCaller() ? 1 : 2, dtmf);
                detectReq.send(roomInfo.getAwfQueueName());
            }
        }
        //
        // TEST CODE
        //
        /*
        if (dtmf == 1) {
            EngineProcFilePlayReq filePlayReq = new EngineProcFilePlayReq(UUID.randomUUID().toString());

            String[] filenames = new String[1];
            filenames[0] = "/home/amf/prompts/music.pcm";
            int [] dstIds =  new int[1];
            dstIds[0] = sessionInfo.getEngineToolId();
            filePlayReq.setData(sessionInfo, sessionInfo.getEngineToolId(), 0, dstIds, false, filenames, 100, 20);
            filePlayReq.send();
        }
        else if ((dtmf == 2) || (dtmf == 3) || (dtmf == 4)) {

            int otherToolId = -1;
            int mixerId = -1;
            RoomInfo roomInfo = RoomManager.getInstance().getRoomInfo(sessionInfo.getConferenceId());
            if (roomInfo != null) {
                mixerId = roomInfo.getMixerId();
                String otherSessionId = roomInfo.getOtherSession(sessionInfo.getSessionId());
                if (otherSessionId != null) {
                    SessionInfo otherSession = SessionManager.findSession(otherSessionId);
                    if (otherSession != null) {
                        otherToolId = otherSession.getEngineToolId();
                    }
                }
            }
            else {
                mixerId = sessionInfo.getMixerToolId();
            }

            EngineProcFilePlayReq filePlayReq = new EngineProcFilePlayReq(UUID.randomUUID().toString());

            String[] filenames = new String[1];
            filenames[0] = "/home/amf/prompts/music.pcm";
            int mediaType = 0;
            int [] dstIds =  null;
            if (dtmf == 2) {
                dstIds = new int[2];
                dstIds[0] = sessionInfo.getEngineToolId();
                dstIds[1] = otherToolId;
                mediaType = 1;
            }
            else if (dtmf == 3) {
                dstIds = new int[1];
                dstIds[0] = sessionInfo.getEngineToolId();
                mediaType = 0;
            }
            else if (dtmf == 4) {
                dstIds = new int[1];
                dstIds[0] = otherToolId;
                mediaType = 0;
            }

            filePlayReq.setData(sessionInfo, mixerId, mediaType, dstIds, false, filenames, 100, 20);
            filePlayReq.send();

        }
        */
    }
}
