package com.jcs.overlay.websocket.messages.J2W;

public class SetBanPickMessage {
    private final String messageType = "SetBanPick";
    private final short cellId;
    private final boolean isPicking;
    private final boolean isBanning;

    public SetBanPickMessage(long cellId, boolean isPicking, boolean isBanning) {
        this.cellId = (short) cellId;
        this.isPicking = isPicking;
        this.isBanning = isBanning;
    }
}
