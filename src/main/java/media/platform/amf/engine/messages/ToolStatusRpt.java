package media.platform.amf.engine.messages;

import java.util.Arrays;

public class ToolStatusRpt {
    private Integer[] active;

    public Integer[] getActive() {
        Integer[] ret = (active != null) ? Arrays.copyOf(active, active.length) : null;
        return ret;
    }

    public void setActive(Integer[] active) {
        this.active = (active != null) ? Arrays.copyOf(active, active.length) : null;
    }
}
