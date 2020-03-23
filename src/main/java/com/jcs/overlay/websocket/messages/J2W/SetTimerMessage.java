package com.jcs.overlay.websocket.messages.J2W;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class SetTimerMessage {
    private final String messageType = "SetTimer";
    private final Long internalNow;
    private final long adjustedTimeLeftInPhase;

    public SetTimerMessage(Long internalNow, long adjustedTimeLeftInPhase) {
        this.internalNow = internalNow;
        this.adjustedTimeLeftInPhase = adjustedTimeLeftInPhase;
    }
}
