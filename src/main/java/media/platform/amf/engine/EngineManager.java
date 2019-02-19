package media.platform.amf.engine;

import media.platform.amf.AppInstance;
import media.platform.amf.config.UserConfig;
import media.platform.amf.engine.types.EngineToolInfo;
import media.platform.amf.engine.types.EngineToolState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EngineManager {
    private static final Logger logger = LoggerFactory.getLogger(EngineManager.class);

    private static final int NUM_OF_TOOLIDS = 8192;

    private volatile static EngineManager engineManager = null;

    public static EngineManager getInstance() {
        if (engineManager == null) {
            engineManager = new EngineManager();
        }

        return engineManager;
    }

//    private EngineToolInfo toolInfoRefs[] = null;
//    private int lastToolId = 1;     // Starts from 1

    public EngineManager() {
        UserConfig config = AppInstance.getInstance().getUserConfig();
        if (config == null) {
            return;
        }

        if (config.getEngineCount() > 0) {
            engineResources = new EngineResourceInfo[config.getEngineCount()];
            for (int i = 0; i < config.getEngineCount(); i++) {
                engineResources[i] = new EngineResourceInfo();
            }
        }

        lastEngineIndex = -1;

        amrTotal = evsTotal = 0;
    }

    public int getIdleToolId(int engineId) {
        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return -1;
        }

        return engineResources[engineId].getIdleToolId();
    }

    public void freeTool(int engineId, int toolId) {
        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return;
        }

        engineResources[engineId].freeTool(toolId);
    }

//    private int resourceTotal = 0;
//    private int resourceBusy = 0;
//    private int resourceIdle = 0;

    /*
    public void setResourceCount(int total, int busy, int idle) {
        if (total >= 0) {
            resourceTotal = total;
        }

        if (busy >= 0) {
            resourceBusy = busy;
        }

        if (idle >= 0) {
            resourceIdle = idle;
        }
    }

    public boolean isResourceChanged(int total, int busy, int idle) {
        return (resourceTotal != total || resourceBusy != busy || resourceIdle != idle) ? true : false;
    }
    */

    private EngineResourceInfo[] engineResources = null;
    private volatile int lastEngineIndex;

    public int getIdleEngineIndex() {
        if (engineResources == null) {
            return -1;
        }

        lastEngineIndex++;
        if (lastEngineIndex >= engineResources.length) {
            lastEngineIndex = 0;
        }

        int maxIdle = 0;
        int index = -1;
        for (int i = lastEngineIndex; i < engineResources.length; i++) {
            if (maxIdle < engineResources[i].getIdle()) {
                maxIdle = engineResources[i].getIdle();
                index = i;
            }
        }

        if (index == -1) {
            for (int i = 0; i < lastEngineIndex; i++) {
                if (maxIdle < engineResources[i].getIdle()) {
                    maxIdle = engineResources[i].getIdle();
                    index = i;
                }
            }

            if (index == -1) {
                index = 0;
            }
        }

        return index;
    }

    public void setResourceCount(int engineId, int total, int busy, int idle) {

        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return;
        }

        if (total >= 0) {
            engineResources[engineId].setTotal(total);
        }

        if (busy >= 0) {
            engineResources[engineId].setBusy(busy);
        }

        if (idle >= 0) {
            engineResources[engineId].setIdle(idle);
        }
    }

    public boolean isResourceChanged(int engineId, int total, int busy, int idle) {
        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return false;
        }

        return (engineResources[engineId].getTotal() != total ||
                engineResources[engineId].getBusy() != busy ||
                engineResources[engineId].getIdle() != idle) ? true : false;
    }


    public int getBusy(int engineId) {
        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return -1;
        }

        return engineResources[engineId].getBusy();
    }

    public int clearTool(int engineId) {
        if ((engineResources == null) || (engineId < 0) || (engineId >= engineResources.length)) {
            return -1;
        }

        return engineResources[engineId].clearTool();
    }

    private int evsTotal, evsUse;
    private int amrTotal, amrUse;

    public int getEvsTotal() {
        return evsTotal;
    }

    public void setEvsTotal(int evsTotal) {
        this.evsTotal = evsTotal;
    }

    public int getEvsUse() {
        return evsUse;
    }

    public void setEvsUse(int dv) {
        this.evsUse += dv;
        if (this.evsUse < 0) {
            this.evsUse = 0;
        }
    }

    public int getAmrTotal() {
        return amrTotal;
    }

    public void setAmrTotal(int amrTotal) {
        this.amrTotal = amrTotal;
    }

    public int getAmrUse() {
        return amrUse;
    }

    public void setAmrUse(int dv) {
        this.amrUse += dv;
        if (this.amrUse < 0) {
            this.amrUse = 0;
        }
    }

    public int getAmrIdle() {
        return (amrTotal - amrUse);
    }

    public int getEvsIdle() {
        return (evsTotal - evsUse);
    }
}

