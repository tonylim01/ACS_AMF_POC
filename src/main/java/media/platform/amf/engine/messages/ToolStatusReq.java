package media.platform.amf.engine.messages;

import java.util.Arrays;

public class ToolStatusReq {
    private Integer[] ids;

    public Integer[] getIds() {
        Integer[] ret = (ids != null) ? Arrays.copyOf(ids, ids.length) : null;
        return ret;
    }

    public void setIds(Integer[] ids) {
        this.ids = (ids != null) ? Arrays.copyOf(ids, ids.length) : null;
    }
}
