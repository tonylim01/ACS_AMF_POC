package media.platform.amf.redundant.messages;

import media.platform.amf.room.RoomInfo;
import media.platform.amf.session.SessionInfo;

public class RoomSessionInfo {
    private RoomInfo roomInfo;
    private SessionInfo sessionInfo;

    public RoomInfo getRoomInfo() {
        return roomInfo;
    }

    public void setRoomInfo(RoomInfo roomInfo) {
        this.roomInfo = roomInfo;
    }

    public SessionInfo getSessionInfo() {
        return sessionInfo;
    }

    public void setSessionInfo(SessionInfo sessionInfo) {
        this.sessionInfo = sessionInfo;
    }
}
