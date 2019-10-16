package com.jcs.overlay.websocket.messages.J2W;

public class SetBanIntentMessage {
    private final String championKey;
    private final short actorCellId;
    private final String messageType = "SetBanIntent";

    public SetBanIntentMessage(String championKey, long actorCellId) {
        this.championKey = championKey;
        this.actorCellId = (short) actorCellId;
    }
}
