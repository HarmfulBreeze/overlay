package com.jcs.overlay.websocket.messages.J2W;

public class SetPickIntentMessage {
    private final String championKey;
    private final short actorCellId;
    private final String messageType = "SetPickIntent";

    public SetPickIntentMessage(long actorCellId, String championKey) {
        this.actorCellId = (short) actorCellId; // can cast to short since this ID should always be between 0 and 9...
        this.championKey = championKey;
    }
}
