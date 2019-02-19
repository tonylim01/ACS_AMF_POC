/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file LogInRes.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.messages;

import com.google.gson.annotations.SerializedName;

public class LogInRes {

    @SerializedName("amr_license_count")
    private int amrLicenseCount;
    @SerializedName("evs_license_count")
    private int evsLicenseCount;

    public int getAmrLicenseCount() {
        return amrLicenseCount;
    }

    public void setAmrLicenseCount(int amrLicenseCount) {
        this.amrLicenseCount = amrLicenseCount;
    }

    public int getEvsLicenseCount() {
        return evsLicenseCount;
    }

    public void setEvsLicenseCount(int evsLicenseCount) {
        this.evsLicenseCount = evsLicenseCount;
    }
}
