package com.jcs.overlay.websocket.messages.J2W;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class ResetWebappMessage extends WebappMessage {
    private final String messageType = "ResetWebapp";

    @Override
    public String getMessageType() {
        return this.messageType;
    }
}
