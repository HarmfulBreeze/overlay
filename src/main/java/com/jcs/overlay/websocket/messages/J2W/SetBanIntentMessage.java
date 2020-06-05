package com.jcs.overlay.websocket.messages.J2W;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class SetBanIntentMessage extends WebappMessage {
    private final String championKey;
    private final short actorCellId;
    private final String messageType = "SetBanIntent";

    public SetBanIntentMessage(String championKey, long actorCellId) {
        this.championKey = championKey;
        this.actorCellId = (short) actorCellId;
    }

    @Override
    public String getMessageType() {
        return this.messageType;
    }
}
