package com.jcs.overlay.websocket.messages.J2W;

public class SetBanPickMessage {
    private final String messageType = "SetBanPick";
    private final long cellId;
    private final boolean isPicking;
    private final boolean isBanning;

    public SetBanPickMessage(long adjustedCellId, boolean isPicking, boolean isBanning) {
        this.cellId = adjustedCellId;
        this.isPicking = isPicking;
        this.isBanning = isBanning;
    }
}
