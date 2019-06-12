package com.jcs.overlay.websocket.messages.champselect;

import java.util.Objects;

public class Timer {
    private long timeLeftInPhase;
    private long adjustedTimeLeftInPhase;
    private int timeLeftInPhaseInSec;
    private int adjustedTimeLeftInPhaseInSec;
    private long totalTimeInPhase;
    private Phase phase;
    private boolean isInfinite;
    private Long internalNowInEpochMs; // uint64

    public int getAdjustedTimeLeftInPhaseInSec() {
        return this.adjustedTimeLeftInPhaseInSec;
    }

    public long getTimeLeftInPhase() {
        return this.timeLeftInPhase;
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
            return this.timeLeftInPhase == aTimer.timeLeftInPhase
                    && this.adjustedTimeLeftInPhase == aTimer.adjustedTimeLeftInPhase
                    && this.timeLeftInPhaseInSec == aTimer.timeLeftInPhaseInSec
                    && this.adjustedTimeLeftInPhaseInSec == aTimer.adjustedTimeLeftInPhaseInSec
                    && this.totalTimeInPhase == aTimer.totalTimeInPhase
                    && this.phase.equals(aTimer.phase)
                    && this.isInfinite == aTimer.isInfinite
                    && this.internalNowInEpochMs.equals(aTimer.internalNowInEpochMs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.timeLeftInPhase, this.adjustedTimeLeftInPhase, this.timeLeftInPhaseInSec,
                this.adjustedTimeLeftInPhaseInSec, this.totalTimeInPhase, this.phase, this.isInfinite,
                this.internalNowInEpochMs);
    }
}
