/*
 * Copyright (C) 2019. Uangel Corp. All rights reserved.
 *
 */

package media.platform.amf.oam;

import java.io.Serializable;

public class UaTraceMsg implements Serializable {
    private static final long serialVersionUID = -5673045115984299469L;

    public static final String DIR_IN = "IN";
    public static final String DIR_OUT = "OUT";

    private String type;
    private String msg;
    private String from;
    private String to;


    public UaTraceMsg(String type, String msg, String from, String to) {
        this.type = type;
        this.msg = msg;
        this.from = from;
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return type + '\'' +
                msg + '\'' +
                from + '\'' + "->" +
                to ;
    }


}
