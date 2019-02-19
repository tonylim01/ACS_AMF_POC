/*
 * Copyright (C) 2019. Uangel Corp. All rights reserved.
 *
 */

package media.platform.amf.oam;

import java.io.Serializable;

public class UaTraceInfo implements Serializable {
    private static final long serialVersionUID = -5139290676635651361L;
    private static UaTraceInfo uaTraceInfo = null;

    private String fromMdn;
    private String toMdn;
    private UaTraceMsg uaTraceMsg;
    private String response;


    public UaTraceInfo(String fromMdn, String toMdn, UaTraceMsg uaTraceMsg, String response) {
        this.fromMdn = fromMdn;
        this.toMdn = toMdn;
        this.uaTraceMsg = uaTraceMsg;
        this.response = response;
    }

    public String getFromMdn() {
        return fromMdn;
    }

    public void setFromMdn(String fromMdn) {
        this.fromMdn = fromMdn;
    }

    public String getToMdn() {
        return toMdn;
    }

    public void setToMdn(String toMdn) {
        this.toMdn = toMdn;
    }

    public UaTraceMsg getUaTraceMsg() {
        return uaTraceMsg;
    }

    public void setUaTraceMsg(UaTraceMsg uaTraceMsg) {
        this.uaTraceMsg = uaTraceMsg;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return fromMdn + '\'' +
                toMdn + '\'' +
                uaTraceMsg.toString() + '\'' +
                response ;
    }

}
