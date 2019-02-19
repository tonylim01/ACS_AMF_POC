package media.platform.amf.oam;

import com.sun.jna.Pointer;
import com.uangel.jnauaoam.NativeAPIWrapper;
import com.uangel.jnauaoam.Uahastatus;
import com.uangel.jnauaoam.UalibContext;
import com.uangel.jnauaoam.mmc.MMCHandler;
import com.uangel.jnauaoam.stat.Uastat;
import com.uangel.svc.oam.Level;
import media.platform.amf.AppInstance;
import media.platform.amf.common.AppUtil;
import media.platform.amf.config.UserConfig;
import media.platform.amf.rmqif.module.RmqClient;
import media.platform.amf.service.ServiceManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class UaOamManager {
    private static final Logger logger = LoggerFactory.getLogger(UaOamManager.class);

    private static final int STAT_DURATION_MSEC = 60000;
    private static final int HB_DURATION_MSEC = 1000;

    private static UaOamManager instance = null;

    private static UalibContext ualibContext = null;

    private boolean isQuit = false;
    private HAStatus haStatus;
    private Thread haStatusThread;
    private Thread statThread;
    private Thread mmcThread;
    private Thread alarmThread;

    private Uastat callStat;
    private Uastat svcStat;
    private Uastat reqStat;
    private Uastat cntStat;
    private Uastat playStat;
    private Uastat rtpStat;

    public static UaOamManager getInstance() {
        if (instance == null) {
            instance = new UaOamManager();
        }

        return instance;
    }

    public UaOamManager() {
        UserConfig config = AppInstance.getInstance().getUserConfig();

        ualibContext = new UalibContext(config.getProcessName());

        mmcHandler = new MMCHandler(ualibContext);
        ualibContext.initMessage();
    }

    public void start() {
        logger.info("UaOamManager start", haStatus);

        ualibContext.initHA();
        haStatus = getOamHAStatus();

        logger.info("Current HA status [{}]", haStatus);

        initStat();

        new DisAmfPacketLoss().register(ualibContext);
        new ChgAmfPacketLoss().register(ualibContext);

        haStatusThread = new Thread(new CheckHaStatusRunnable());
        haStatusThread.start();

        statThread = new Thread(new StatRunnable());
        statThread.start();

        mmcThread = new Thread(new MmcRunnable());
        mmcThread.start();

        alarmThread = new Thread(new AlarmRunnable());
        alarmThread.start();
    }

    public void stop() {
        isQuit = true;
    }

    public void trace(String key/* mdn */, String message) {
        Object lock=NativeAPIWrapper.UajnaLibrary.INSTANCE;
        synchronized (lock) {
            NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceSend(key, message.getBytes(),message.length());
        }
    }

    public boolean isTraceEnabled(String mdn) {
        Object lock=NativeAPIWrapper.UajnaLibrary.INSTANCE;
        synchronized (lock) {
            int ti = NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceGetTraceIndex(mdn);
            return (ti >= 0);
        }
    }

    public int getTraceLevel(String mdn) {
        Object lock=NativeAPIWrapper.UajnaLibrary.INSTANCE;
        synchronized (lock) {
            int ti = NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceGetTraceIndex(mdn);
            if (ti >= 0) {
                return NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceGetLevel(ti);
            } else {
                return 0;
            }
        }
    }

    public boolean isTraceLogOn(String mdn) {
        Object lock=NativeAPIWrapper.UajnaLibrary.INSTANCE;
        synchronized (lock) {
            int ti = NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceGetTraceIndex(mdn);
            if(ti >= 0) {
                return NativeAPIWrapper.TRACE_LOG_ON == NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_traceGetLogFlag(ti);
            } else {
                return false;
            }
        }
    }

    private void sendTrace(String[] mdn, String level1, String level2) {
        sendTrace(mdn, ()->level1, ()->level2);
    }

    private void sendTrace(String[] keys, Supplier<String>...messages) {
        if (keys == null || keys.length < 2|| keys[0] == null || keys[1] == null) {
            return;
        }

        boolean traceEnabled1 = isTraceEnabled(keys[0]);
        boolean traceEnabled2 = isTraceEnabled(keys[1]);

        logger.debug("Send trace. enabled [{}] [{}]", traceEnabled1, traceEnabled2);

        if (!traceEnabled1 && !traceEnabled2) {
            return;
        }

        int traceLevel1 = getTraceLevel(keys[0]);
        int traceLevel2 = getTraceLevel(keys[1]);

        logger.debug("Trace level [{}] [{}]", traceLevel1, traceLevel2);

        int level = Math.max(traceLevel1, traceLevel2);
        String[] array = new String[Math.min(level, messages.length)];
        for (int i = 0; i < array.length; i++) {
            array[i] = messages[i].get();
        }

        String msg = StringUtils.join(array, '\n');
        if (traceEnabled1) {
            trace(keys[0], msg);
        }
        if (traceEnabled2) {
            trace(keys[1], msg);
        }
    }

    /**
     * @param dir       : in/out
     * @param msgType   : msg name
     * @param fromQueue : AMF...
     * @param toQueue   : A2S...
     * @param fromMdn   : 010..
     * @param toMdn     : 010..
     * @param msg
     */
    public static void sendTrace(String dir, String msgType, String fromQueue, String toQueue, String fromMdn, String toMdn, String msg) {

        if (!AppInstance.getInstance().getConfig().isOamEnabled()) {
            return;
        }

        UaTraceMsg uaTraceMsg = new UaTraceMsg(dir, msgType,
                (fromQueue != null) ? fromQueue : AppInstance.getInstance().getUserConfig().getInstanceName(),
                (toQueue != null) ? toQueue : AppInstance.getInstance().getUserConfig().getInstanceName());

        String[] mdn = { fromMdn, toMdn };
        String level1Msg = fromMdn + '\'' + toMdn + '\'' + uaTraceMsg.toString() + '\'' + "";

        UaOamManager.getInstance().sendTrace(mdn, level1Msg, msg);
    }

    private void initStat() {
        callStat = new Uastat("AMFSTAT", "CALL_STAT");
        svcStat = new Uastat("AMFSTAT", "SERVICE_STAT");
        reqStat = new Uastat("AMFSTAT", "REQUEST_STAT");
        cntStat = new Uastat("AMFSTAT", "COUNT_STAT");
        playStat = new Uastat("AMFSTAT", "PLAY_STAT");
        rtpStat = new Uastat("AMFSTAT", "RTP_STAT");
    }

    private HAStatus getOamHAStatus() {

        // UNKNOWN(-1), STANDBY(0), ACTIVE(1), STANDALONE(2);
        Uahastatus ha = ualibContext.getHAStatus();

        HAStatus status;

        switch (ha.getCode()) {
            case 0:
                status = HAStatus.STANDBY;
                break;
            case 1:
                status = HAStatus.ACTIVE;
                break;
            case 2:
                status = HAStatus.STANDALONE;
                break;
            default:
                status = HAStatus.UNKNOWN;
                break;
        }

        return status;
    }

    public HAStatus getHaStatus() {
        return haStatus;
    }

    public class CheckHaStatusRunnable implements Runnable {

        private long startTimestamp = 0;
        private int tick = 0;

        public CheckHaStatusRunnable() {
            this.startTimestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (!isQuit) {
                HAStatus newStatus = getOamHAStatus();

                if (newStatus != haStatus) {
                    logger.warn("HA status changed: [{}] -> [{}]", haStatus, newStatus);

                    if (newStatus == HAStatus.ACTIVE) {
                        // Enable RMQ
                        ServiceManager.getInstance().startRmqServer();
                    }
                    else if ((haStatus == HAStatus.ACTIVE) && (newStatus != HAStatus.ACTIVE)) {
                        ServiceManager.getInstance().stopRmqServer();
                        RmqClient.closeAllClients();
                    }

                    haStatus = newStatus;
                }

                ualibContext.stamp();

                tick++;

                if (tick >= 10) {
                    startTimestamp += (tick - 1) * HB_DURATION_MSEC;
                    tick = 1;

                }

                long timestamp = System.currentTimeMillis();

                AppUtil.trySleep(tick * HB_DURATION_MSEC - (int) (timestamp - startTimestamp));
            }

            logger.warn("CheckHaStatus thread end");
        }
    }

    public class MmcRunnable implements Runnable {

        @Override
        public void run() {
            while (!isQuit) {
                AppUtil.trySleep(500);
//                logger.debug("mmcProcess()..");

                mmcProcess();
            }

            logger.warn("MMC thread end");
        }
    }

    public class AlarmRunnable implements Runnable {

        private long startTimestamp = 0;
        private int tick = 0;

        public AlarmRunnable() {
            this.startTimestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (!isQuit) {
                int threshold = AppInstance.getInstance().getOamConfig().getPacketLossThreshold();
                if (threshold > 0) {

                    int packetLoss = StatManager.getInstance().getPacketLossPercent();
                    if (packetLoss >= threshold) {
                        new AlarmHandler().onApplicationEvent("AMF_PACKET_LOSS_THREADHOLD_OVER", Level.NOR,
                                String.format("Packet loss [%d] exceeds [%d]", packetLoss, threshold));
                    }
                }

                tick++;

                if (tick >= 10) {
                    startTimestamp += (tick - 1) * 1000;
                    tick = 1;

                }

                long timestamp = System.currentTimeMillis();

                AppUtil.trySleep(tick * 1000 - (int) (timestamp - startTimestamp));
            }

            logger.warn("Alarm thread end");
        }
    }

    public class StatRunnable implements Runnable {
        private long lastTimestamp;
        private int tick;
        private UserConfig config;
        private StatManager statManager;

        public StatRunnable() {
            this.tick = 0;
            this.config = AppInstance.getInstance().getUserConfig();
            this.statManager = StatManager.getInstance();
        }

        @Override
        public void run() {
            // Sleep until the next exact time which a second is 0
            long current = System.currentTimeMillis();
            AppUtil.trySleep(60000 - (int)(current % 60000));

            this.lastTimestamp = System.currentTimeMillis();

            boolean isFirst = true;

            while (!isQuit) {

                long timestamp = System.currentTimeMillis();
                int millisec = (int)(timestamp % 60000);

                if (isFirst || ((millisec < 1000) && (timestamp - lastTimestamp > 50000)) || (timestamp - lastTimestamp > 61000)) {

                    lastTimestamp = timestamp;

                    if (isFirst) {
                        isFirst = false;
                    }

                    if (config == null || config.getInstanceName() == null) {
                        logger.error("Instance name not found. [{}]", (config == null) ? "null config" : "null instance");
                    }
                    else if (statManager == null) {
                        logger.error("Null statManager");
                    }
                    else {
                        logger.debug("Save stat");
                        saveCallStat();
                        saveSvcStat();
                        saveReqStat();
                        saveCntStat();
                        savePlayStat();
                        saveRtpStat();
                    }

                    tick = 0;
                }

                tick++;
                AppUtil.trySleep(1000 - (int)(System.currentTimeMillis() % 1000));
            }

            logger.warn("Stat thread end");
        }

        private void saveStatData(Uastat stat, String statStr, int statType) {
            if (stat == null) {
                return;
            }

            int[] values = statManager.getValues(statType);
            if (values == null) {
                return;
            }

            logger.debug("Stat [{}] type [{}] value [{}]", statStr, statType, values);
            if (stat.stat(statStr, values) == 0) {
                statManager.clearValues(statType, values);
            }
        }

        private void saveCallStat() {

            int result = callStat.open();
            if (result >= 0) {

                // Init call stat
                int[] initCallValues = {0};
                callStat.stat(StatManager.STR_INCOMING_OFFER, initCallValues);
                callStat.stat(StatManager.STR_OUTGOING_OFFER, initCallValues);
                callStat.stat(StatManager.STR_ANSWER, initCallValues);
                callStat.stat(StatManager.STR_INCOMING_RELEASE, initCallValues);
                callStat.stat(StatManager.STR_OUTGOING_RELEASE, initCallValues);
            }

            // Call stat
            saveStatData(callStat, StatManager.STR_INCOMING_OFFER, StatManager.SVC_IN_CALL);
            saveStatData(callStat, StatManager.STR_OUTGOING_OFFER, StatManager.SVC_OUT_CALL);
            saveStatData(callStat, StatManager.STR_ANSWER, StatManager.SVC_ANSWER);
            saveStatData(callStat, StatManager.STR_INCOMING_RELEASE, StatManager.SVC_IN_RELEASE);
            saveStatData(callStat, StatManager.STR_OUTGOING_RELEASE, StatManager.SVC_OUT_RELEASE);

            callStat.close();
        }

        private void saveSvcStat() {
            int result = svcStat.open();
            if (result >= 0) {

                // Init req/ok/fail stat
                int[] initSvcValues = {0, 0, 0};
                svcStat.stat(StatManager.STR_CALLER_WAKEUP, initSvcValues);
                svcStat.stat(StatManager.STR_CALLEE_WAKEUP, initSvcValues);
            }

            // Req/ok/fail stat
            saveStatData(svcStat, StatManager.STR_CALLER_WAKEUP, StatManager.SVC_CG_WAKEUP_REQ);
            saveStatData(svcStat, StatManager.STR_CALLEE_WAKEUP, StatManager.SVC_CD_WAKEUP_REQ);

            svcStat.close();
        }

        private void saveReqStat() {
            int result = reqStat.open();
            if (result >= 0) {
                // Init req/res stat
                int[] initFairValues = {0, 0};
                reqStat.stat(StatManager.STR_AI_SVC, initFairValues);
                reqStat.stat(StatManager.STR_MEDIA_STOP, initFairValues);
            }

            // Req/res stat
            saveStatData(reqStat, StatManager.STR_AI_SVC, StatManager.SVC_AI_REQ);
            saveStatData(reqStat, StatManager.STR_MEDIA_STOP, StatManager.SVC_PLAY_STOP_REQ);

            reqStat.close();
        }

        private void saveCntStat() {
            int result = cntStat.open();
            if (result >= 0) {
                // Init count stat
                int[] initCntValues = {0};
                cntStat.stat(StatManager.STR_INCOMING_AI_CANCEL, initCntValues);
                cntStat.stat(StatManager.STR_OUTGOING_AI_CANCEL, initCntValues);
                cntStat.stat(StatManager.STR_END_DETECT, initCntValues);
            }

            // Count stat
            saveStatData(cntStat, StatManager.STR_INCOMING_AI_CANCEL, StatManager.SVC_IN_AI_CANCEL);
            saveStatData(cntStat, StatManager.STR_OUTGOING_AI_CANCEL, StatManager.SVC_OUT_AI_CANCEL);
            saveStatData(cntStat, StatManager.STR_END_DETECT, StatManager.SVC_END_DETECT);

            cntStat.close();
        }

        private void savePlayStat() {
            int result = playStat.open();
            if (result >= 0) {

                // Init play stat
                int[] initPlayValues = {0, 0, 0, 0};
                playStat.stat(StatManager.STR_MEDIA_PLAY, initPlayValues);
            }

            // Play stat
            saveStatData(playStat, StatManager.STR_MEDIA_PLAY, StatManager.SVC_PLAY_REQ);

            playStat.close();
        }

        private void saveRtpStat() {
            int result = rtpStat.open();
            if (result > 0) {
                // Init rtp stat
                int[] initRtpValues = {0, 0, 0};
                rtpStat.stat(StatManager.STR_RTP_EVS_IN, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_EVS_OUT, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_AMR_IN, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_AMR_OUT, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_ETC_IN, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_ETC_OUT, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_TOTAL_IN, initRtpValues);
                rtpStat.stat(StatManager.STR_RTP_TOTAL_OUT, initRtpValues);
            }

            // Rtp stat
            saveStatData(rtpStat, StatManager.STR_RTP_EVS_IN, StatManager.RTP_EVS_IN);
            saveStatData(rtpStat, StatManager.STR_RTP_EVS_OUT, StatManager.RTP_EVS_OUT);
            saveStatData(rtpStat, StatManager.STR_RTP_AMR_IN, StatManager.RTP_AMR_IN);
            saveStatData(rtpStat, StatManager.STR_RTP_AMR_OUT, StatManager.RTP_AMR_OUT);
            saveStatData(rtpStat, StatManager.STR_RTP_ETC_IN, StatManager.RTP_ETC_IN);
            saveStatData(rtpStat, StatManager.STR_RTP_ETC_OUT, StatManager.RTP_ETC_OUT);
            saveStatData(rtpStat, StatManager.STR_RTP_TOTAL_IN, StatManager.RTP_TOTAL_IN);
            saveStatData(rtpStat, StatManager.STR_RTP_TOTAL_OUT, StatManager.RTP_TOTAL_OUT);

            rtpStat.close();
        }
    }

    private MMCHandler mmcHandler = null;

    private void mmcProcess() {
        Pointer p = ualibContext.getMessage();

        if (p != null) {
            String cmd = mmcHandler.getCommandName(p);
            logger.debug("MMC cmd [{}]", cmd);

            if (cmd.equals("dis-amf-packet-loss")) {
                mmcHandler.run(p);
                new DisAmfPacketLoss().exec(p);
            }
            else if (cmd.equals("chg-amf-packet-loss")) {
                mmcHandler.run(p);
                new ChgAmfPacketLoss().exec(p);
            }
        }
        else {
//            logger.debug("getMessage() null");
        }
    }

}
