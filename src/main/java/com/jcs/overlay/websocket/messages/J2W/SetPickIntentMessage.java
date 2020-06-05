package com.jcs.overlay.websocket.messages.J2W;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class SetPickIntentMessage extends WebappMessage {
    private final String championKey;
    private final long actorCellId;
    private final String messageType = "SetPickIntent";

    public SetPickIntentMessage(long adjustedCellId, String championKey) {
        this.actorCellId = adjustedCellId;
        this.championKey = championKey;
    }

    @Override
    public String getMessageType() {
        return this.messageType;
    }
}
