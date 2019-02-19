package media.platform.amf.service;

import media.platform.amf.AppInstance;
import media.platform.amf.PackageVer;
import media.platform.amf.common.AppUtil;
import media.platform.amf.common.NetUtil;
import media.platform.amf.config.AmfConfig;
import media.platform.amf.config.OamConfig;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.EngineClient;
import media.platform.amf.engine.EngineServer;
import media.platform.amf.engine.EngineServiceManager;
import media.platform.amf.oam.HAStatus;
import media.platform.amf.oam.UaOamManager;
import media.platform.amf.redundant.RedundantServer;
import media.platform.amf.rmqif.handler.RmqProcLogInReq;
import media.platform.amf.rmqif.module.RmqClient;
import media.platform.amf.rmqif.module.RmqServer;
import media.platform.amf.room.RoomManager;
import media.platform.amf.rtpcore.Process.NettyRTPServer;
import media.platform.amf.rtpcore.Process.NettyUDPServer;
import media.platform.amf.session.SessionInfo;
import media.platform.amf.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

    private static final boolean USE_PING = false;

    private static ServiceManager serviceManager = null;

    public static ServiceManager getInstance() {
        if (serviceManager == null) {
            serviceManager = new ServiceManager();
        }

        return serviceManager;
    }

    private RmqServer rmqServer;
    private RmqServer awfServer;
    private SessionManager sessionManager;
    private HeartbeatManager heartbeatManager;
    private RedundantServer redundantServer;
//    private EngineServer engineServer;
    private EngineServer[] engineServers;
//    private EngineServiceManager engineServiceManager;

    private boolean isQuit = false;

    private UaOamManager oamManager;


    /**
     * Reads a config file in the constructor
     */
    public ServiceManager() {
        AppInstance instance = AppInstance.getInstance();

        AmfConfig amfConfig = new AmfConfig(instance.getInstanceId(), instance.getConfigFile());
        UserConfig userConfig = new UserConfig(instance.getInstanceId(), amfConfig.getMediaConfPath());

        OamConfig oamConfig = new OamConfig(
                instance.getConfigFile().substring(
                        0, instance.getConfigFile().lastIndexOf("/") + 1) + "amf_oam.conf");

        instance.setConfig(amfConfig);
        instance.setUserConfig(userConfig);
        instance.setOamConfig(oamConfig);

        instance.loadPromptConfig();

        if (userConfig.getLogPath() != null && userConfig.getLogTime() > 0) {
            org.apache.log4j.xml.DOMConfigurator.configureAndWatch(userConfig.getLogPath(), userConfig.getLogTime());
        }
    }

    /**
     * Main loop
     */
    public void loop() {

        if (USE_PING && !pingRmqServer(AppInstance.getInstance().getUserConfig().getRmqHost())) {
            return;
        }

        startService();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            logger.warn("Process is about to quit (Ctrl+C)");
            isQuit = true;

            stopService();
            }));

        while (!isQuit) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Process End");
    }

    /**
     * Returns a ping result to a rabbitmq server
     * @param host
     * @return
     */
    private boolean pingRmqServer(String host) {
        logger.info("Checking RMQ target [{}]", host);
        boolean rmqAvailable = NetUtil.ping( host, 1000);
        logger.info("Host [{}] is {}", host, rmqAvailable ? "reachable" : "NOT reachable");

        return rmqAvailable;
    }

    /**
     * Initializes pre-process
     * @return
     */
    private boolean startService() {

        UserConfig config = AppInstance.getInstance().getUserConfig();

        if (config == null) {
            return false;
        }

//        rmqServer = new RmqServer(config.getRmqHost(), config.getRmqUser(), config.getRmqPass(), config.getLocalName());
//        rmqServer.start();
//
//        awfServer = new RmqServer(config.getAwfRmqHost(), config.getAwfRmqUser(), config.getAwfRmqPass(), config.getLocalName());
//        awfServer.start();

        sessionManager = SessionManager.getInstance();
        sessionManager.start();

        if (config.getRedundantConfig().getLocalPort() > 0) {
            redundantServer = new RedundantServer(config.getRedundantConfig().getLocalPort());
            redundantServer.start();
        }

        EngineClient.init(config.getEngineCount());

        if (config.getEngineCount() > 0) {
            engineServers = new EngineServer[config.getEngineCount()];

            for (int i = 0; i < config.getEngineCount(); i++) {
                if (config.getEngineLocalPort(i) > 0) {
                    engineServers[i] = new EngineServer(i, config.getEngineLocalPort(i));
                    engineServers[i].start();
                }
                else {
                    logger.error("Engine [{}] local port not defined", i);
                }
            }
        }
        else if (config.getEngineLocalPort() > 0) {
            engineServers = new EngineServer[1];

            if (config.getEngineLocalPort() > 0) {
                engineServers[0] = new EngineServer(0, config.getEngineLocalPort());
                engineServers[0].start();
            }
            else {
                logger.error("Engine local port not defined");
            }
        }

        if(AppInstance.getInstance().getConfig().getHeartbeat() == true) {
            heartbeatManager = HeartbeatManager.getInstance();
            heartbeatManager.start();
        }

        this.amfLoginToA2S();

        try {
            NettyRTPServer nettyRTPServer = new NettyRTPServer();
            nettyRTPServer.run();

            AppInstance.getInstance().setNettyRTPServer(nettyRTPServer);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            NettyUDPServer nettyUDPServer = new NettyUDPServer();
            nettyUDPServer.run();

            AppInstance.getInstance().setNettyUDPServer(nettyUDPServer);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        engineServiceManager = EngineServiceManager.getInstance();
//        engineServiceManager.start();

        if (AppInstance.getInstance().getConfig().isOamEnabled()) {
            oamManager = UaOamManager.getInstance();
            oamManager.start();

            if (oamManager.getHaStatus() == HAStatus.ACTIVE) {
                startRmqServer();
            }
            else {
                logger.info("HA status not active. Skip starting RMQ");
            }
        }
        else {
            logger.info("OAM not enabled");
            startRmqServer();
        }


        PackageVer.write();

        return true;
    }

    /**
     * Finalizes all the resources
     */
    private void stopService() {

        if (oamManager != null) {
            oamManager.stop();
        }

        stopRmqServer();

        if (redundantServer != null) {
            redundantServer.stop();
        }

//        if (engineServiceManager != null) {
//            engineServiceManager.stop();
//        }

        if (engineServers != null) {
            for (EngineServer engineServer: engineServers) {
                engineServer.stop();
            }
        }

//        heartbeatManager.stop();
        sessionManager.stop();

        UserConfig config = AppInstance.getInstance().getUserConfig();

        if (RmqClient.hasInstance(config.getMcudName())) {
            RmqClient.getInstance(config.getMcudName()).closeSender();
        }
    }

    public void startRmqServer() {
        logger.info("Start Rmq server");

        UserConfig config = AppInstance.getInstance().getUserConfig();

        if (config == null) {
            return;
        }

        if (rmqServer != null || awfServer != null) {
            stopRmqServer();
        }

        rmqServer = new RmqServer(config.getRmqHost(), config.getRmqUser(), config.getRmqPass(), config.getLocalName());
        rmqServer.start();

        awfServer = new RmqServer(config.getAwfRmqHost(), config.getAwfRmqUser(), config.getAwfRmqPass(), config.getLocalName());
        awfServer.start();
    }

    public void stopRmqServer() {

        logger.warn("Stop Rmq server");

        if (rmqServer != null) {
            rmqServer.stop();
            rmqServer = null;
        }

        if (awfServer != null) {
            awfServer.stop();
            awfServer = null;
        }
    }

    public boolean releaseResource(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        if (sessionManager == null) {
            return false;
        }

        SessionInfo sessionInfo = sessionManager.getSession(sessionId);

        if (sessionInfo == null) {
            logger.warn("[{}] No session found", sessionId);
            return false;
        }

        if (sessionInfo.getRtpSender() != null) {
            sessionInfo.getRtpSender().stop();
        }

        if (sessionInfo.getUdpSender() != null) {
            sessionInfo.getUdpSender().stop();
        }

        if(sessionInfo.rtpChannel != null) {
            sessionInfo.rtpChannel.close();
        }

        if(sessionInfo.udpChannel != null) {
            sessionInfo.udpChannel.close();
        }

        logger.warn("Netty session Close : [{}]", sessionId);

        if(sessionInfo.rtpClient != null) {
            sessionInfo.rtpClient.close();
        }

        if(sessionInfo.udpClient != null) {
            sessionInfo.udpClient.close();
        }


        logger.warn("Netty UDP session Close : [{}]", sessionId);

        if (sessionInfo.getConferenceId() != null) {
            RoomManager.getInstance().removeSession(sessionInfo.getConferenceId(), sessionInfo.getSessionId());
        }

        sessionManager.deleteSession(sessionId);


        return true;
    }

    private void amfLoginToA2S() {
        UserConfig config = AppInstance.getInstance().getUserConfig();

        String thisSessionId = UUID.randomUUID().toString();

        RmqProcLogInReq req = new RmqProcLogInReq( thisSessionId, UUID.randomUUID().toString());

        req.send(config.getMcudName());
    }

    public boolean isRmqAlive() {
        return (rmqServer != null);
    }
}
