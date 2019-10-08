package com.jcs.overlay.websocket.messages.J2W;

public class NewPick {
    private final String championName;
    private final short actorCellId;
    private final String messageType = "NewPick";

    public NewPick(String championName, long actorCellId) {
        this.championName = championName;
        this.actorCellId = (short) actorCellId; // can cast to short since this ID should always be between 0 and 9...
    }
}
