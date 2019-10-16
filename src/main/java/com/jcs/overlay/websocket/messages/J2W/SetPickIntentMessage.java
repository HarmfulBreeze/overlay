package com.jcs.overlay.websocket.messages.J2W;

public class SetPickIntentMessage {
    private final String championKey;
    private final long actorCellId;
    private final String messageType = "SetPickIntent";

    public SetPickIntentMessage(long adjustedCellId, String championKey) {
        this.actorCellId = adjustedCellId;
        this.championKey = championKey;
    }
}
