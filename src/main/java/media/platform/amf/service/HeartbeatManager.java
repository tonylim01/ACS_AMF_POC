package media.platform.amf.service;

import media.platform.amf.AppInstance;
import media.platform.amf.common.AppUtil;
import media.platform.amf.config.AmfConfig;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.EngineManager;
import media.platform.amf.rmqif.handler.RmqProcHeartbeatReq;
import media.platform.amf.rmqif.handler.RmqProcLogInReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.Service;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HeartbeatManager {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatManager.class);

    private volatile static HeartbeatManager heartbeatManager = null;

    public static HeartbeatManager getInstance() {
        if (heartbeatManager == null) {
            heartbeatManager = new HeartbeatManager();
        }

        return heartbeatManager;
    }

    private String thisSessionId;
//    private ScheduledExecutorService scheduleService;
//    private ScheduledFuture<?> scheduleFuture;

    private Thread heartbeatThread;
    private boolean isQuit;

    public HeartbeatManager() {
        thisSessionId = UUID.randomUUID().toString();
//        scheduleService = Executors.newScheduledThreadPool(1);
    }

    /**
     * Starts the heartbeat scheduler
     */
    public void start() {
//        scheduleFuture = scheduleService.scheduleAtFixedRate(new SendHeartbeatRunnable(), 1, 1, TimeUnit.SECONDS);

        isQuit = false;

        heartbeatThread = new Thread(new HeartbeatRunnable());
        heartbeatThread.start();
    }

    /**
     * Stops the heartbeat scheduler
     */
    public void stop() {
//        scheduleFuture.cancel(true);
//        scheduleService.shutdown();

        isQuit = true;
    }

    private int tick = 0;
    /**
     * Runnable proc for the ScheduledExecutorService
     */
    class SendHeartbeatRunnable implements Runnable {
        @Override
        public void run() {
            UserConfig config = AppInstance.getInstance().getUserConfig();

            tick++;

            if (ServiceManager.getInstance().isRmqAlive()) {
                boolean isLoginSent = false;

                if (tick % 5 == 0) {
                    if ((EngineManager.getInstance().getAmrTotal() == 0) &&
                            (EngineManager.getInstance().getEvsTotal() == 0)) {

                        RmqProcLogInReq loginReq = new RmqProcLogInReq(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                        loginReq.send(config.getMcudName());

                        isLoginSent = true;
                    }
                }

                if (!isLoginSent) {
                    RmqProcHeartbeatReq req = new RmqProcHeartbeatReq( thisSessionId, UUID.randomUUID().toString());
                    req.send(config.getMcudName());
                }
            }
            else {
                if (tick % 60 == 0) {
                    logger.debug("Rmq not active");
                }
            }


            if (tick % 60 == 0) {
                tick = 0;
            }

        }
    }

    public class HeartbeatRunnable implements Runnable {

        private long startTimestamp = 0;
        private int tick = 0;

        public HeartbeatRunnable() {
            this.startTimestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (!isQuit) {

                UserConfig config = AppInstance.getInstance().getUserConfig();

                tick++;

                if (ServiceManager.getInstance().isRmqAlive()) {
                    boolean isLoginSent = false;

                    if (tick % 5 == 0) {
                        if ((EngineManager.getInstance().getAmrTotal() == 0) &&
                                (EngineManager.getInstance().getEvsTotal() == 0)) {

                            RmqProcLogInReq loginReq = new RmqProcLogInReq(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                            loginReq.send(config.getMcudName());

                            isLoginSent = true;
                        }
                    }

                    if (!isLoginSent) {
                        RmqProcHeartbeatReq req = new RmqProcHeartbeatReq(thisSessionId, UUID.randomUUID().toString());
                        req.send(config.getMcudName());
                    }
                }
                else {
                    if (tick % 60 == 0) {
                        logger.debug("Rmq not active");
                    }
                }

                boolean isCycled = false;

                if (tick >= 3600) {
                    startTimestamp += (tick - 1) * 1000;
                    tick = 1;
                    isCycled = true;

                }

                long timestamp = System.currentTimeMillis();

                AppUtil.trySleep(tick * 1000 - (int) (timestamp - startTimestamp));

                if (isCycled) {
                    tick = 0;
                }
            }

            logger.warn("Heartbeat thread end");
        }
    }

}
