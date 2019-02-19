package media.platform.amf.engine;

import media.platform.amf.engine.types.EngineToolInfo;
import media.platform.amf.engine.types.EngineToolState;

public class EngineResourceInfo {

    private EngineToolInfo toolInfoRefs[] = null;
    private int lastToolId = 1;     // Starts from 1

    private int total;
    private int busy;
    private int idle;


    public EngineResourceInfo() {
        total = 0;
        busy = 0;
        idle = 0;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        if (total != this.total) {
            this.total = total;
            initToolInfo();
        }
    }

    public int getBusy() {
        return busy;
    }

    public void setBusy(int busy) {
        this.busy = busy;
    }

    public int getIdle() {
        return idle;
    }

    public void setIdle(int idle) {
        this.idle = idle;
    }

    private void initToolInfo() {
        if (total > 0) {
            toolInfoRefs = new EngineToolInfo[total];

            long timestamp = System.currentTimeMillis();

            for (int i = 0; i < toolInfoRefs.length; i++) {
                toolInfoRefs[i] = new EngineToolInfo();
                toolInfoRefs[i].setIdleTime(timestamp);
                toolInfoRefs[i].setState(EngineToolState.TOOL_IDLE);
            }
        }
        else {
            toolInfoRefs = null;
        }
    }

    public synchronized int getIdleToolId() {

        int toolId = -1;
        int i;

        for (i = lastToolId; i < toolInfoRefs.length; i++) {
            if (toolInfoRefs[i].getState() == EngineToolState.TOOL_IDLE) {
                toolId = i;
                break;
            }
        }

        if (toolId < 0) {

            for (i = 1; i < lastToolId; i++) {
                if (toolInfoRefs[i].getState() == EngineToolState.TOOL_IDLE) {
                    toolId = i;
                    break;
                }
            }
        }

        if (toolId > 0) {
            lastToolId += 1;

            if (lastToolId >= toolInfoRefs.length) {
                lastToolId = 1;
            }

            toolInfoRefs[toolId].setState(EngineToolState.TOOL_ALLOC);
        }

        return toolId;
    }

    public void freeTool(int toolId) {
        if (toolId < 0) {
            return;
        }

        synchronized (this) {

            toolInfoRefs[toolId].setState(EngineToolState.TOOL_IDLE);
            toolInfoRefs[toolId].setIdleTime(System.currentTimeMillis());
        }
    }

    public int clearTool() {

        int count = 0;
        synchronized (this) {

            for (int i = 0; i < toolInfoRefs.length; i++) {
                if (toolInfoRefs[i].getState() != EngineToolState.TOOL_IDLE) {

                    toolInfoRefs[i].setState(EngineToolState.TOOL_IDLE);
                    toolInfoRefs[i].setIdleTime(System.currentTimeMillis());

                    count++;
                }
            }
        }

        return count;
    }
}
