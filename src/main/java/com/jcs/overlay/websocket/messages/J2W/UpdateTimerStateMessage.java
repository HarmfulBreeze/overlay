package com.jcs.overlay.websocket.messages.J2W;

import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class UpdateTimerStateMessage extends WebappMessage {
    private final String messageType = "UpdateTimerState";
    private final Timer.Phase phase;
    private final Long internalNow;
    private final long adjustedTimeLeftInPhase;

    public UpdateTimerStateMessage(Timer.Phase phase, Long internalNow, long adjustedTimeLeftInPhase) {
        this.phase = phase;
        this.internalNow = internalNow;
        this.adjustedTimeLeftInPhase = adjustedTimeLeftInPhase;
    }

    @Override
    public String getMessageType() {
        return this.messageType;
    }
}
