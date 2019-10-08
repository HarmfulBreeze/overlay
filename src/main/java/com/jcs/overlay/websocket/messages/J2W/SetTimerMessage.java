package com.jcs.overlay.websocket.messages.J2W;

public class SetTimerMessage {
    private final String messageType = "SetTimer";
    private final Long internalNow;
    private final long timeLeftInPhase;

    public SetTimerMessage(Long internalNow, long timeLeftInPhase) {
        this.internalNow = internalNow;
        this.timeLeftInPhase = timeLeftInPhase;
    }
}
