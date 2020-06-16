package com.jcs.overlay.websocket.messages.C2J.champselect;

import java.util.Objects;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class Timer {
    private long adjustedTimeLeftInPhase;
    private long totalTimeInPhase;
    private Phase phase;
    private boolean isInfinite;
    private Long internalNowInEpochMs; // uint64

    public long getAdjustedTimeLeftInPhase() {
        return this.adjustedTimeLeftInPhase;
    }

    public long getTotalTimeInPhase() {
        return this.totalTimeInPhase;
    }

    public Phase getPhase() {
        return this.phase;
    }

    public Long getInternalNowInEpochMs() {
        return this.internalNowInEpochMs;
    }

    public boolean isInfinite() {
        return this.isInfinite;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Timer) {
            Timer aTimer = ((Timer) obj);
            return this.adjustedTimeLeftInPhase == aTimer.adjustedTimeLeftInPhase
                    && this.totalTimeInPhase == aTimer.totalTimeInPhase
                    && this.phase.equals(aTimer.phase)
                    && this.isInfinite == aTimer.isInfinite
                    && this.internalNowInEpochMs.equals(aTimer.internalNowInEpochMs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.adjustedTimeLeftInPhase, this.totalTimeInPhase, this.phase, this.isInfinite,
                this.internalNowInEpochMs);
    }

    public enum Phase {
        PLANNING, BAN_PICK, FINALIZATION, GAME_STARTING, UNKNOWN
    }
}
