package com.jcs.overlay.websocket.messages.J2W;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class NewBanMessage extends WebappMessage {
    private final String messageType = "NewBan";
    private final String championKey;
    private final int banId;

    public NewBanMessage(String championKey, int banId) {
        this.championKey = championKey;
        this.banId = banId;
    }
}
