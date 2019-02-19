package media.platform.amf.oam;

import com.sun.jna.Pointer;
import com.uangel.jnauaoam.NativeAPIWrapper;

public abstract class PacketLossWrapper {

    /**
     * dis/add/chg/del mandatory
     *
     * @param mmlIn
     * @return
     */
    int getPacketLossCtrl(Pointer mmlIn) {
        return NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcGetInt(mmlIn, "amf_packet_loss_set", -1);
    }


    void onDisplay(Pointer mmlIn, int packetloss) {
        NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcUaMmifInit((short) 2);
        NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcSetString("PACKET_LOSS_SET");
        NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcSetInt(packetloss);
    }


    void sendSuccess(Pointer mmlIn) {
        NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcSendSuccess(mmlIn);
    }

    void sendFail(Pointer mmlIn, String errorMessage) {
        NativeAPIWrapper.UajnaLibrary.INSTANCE.uajna_mmcSendError(mmlIn, errorMessage);
    }
}
