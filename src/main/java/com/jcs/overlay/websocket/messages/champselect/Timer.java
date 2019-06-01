package com.jcs.overlay.websocket.messages.champselect;

public class Timer {
    long timeLeftInPhase;
    long adjustedTimeLeftInPhase;
    int timeLeftInPhaseInSec;
    int adjustedTimeLeftInPhaseInSec;
    long totalTimeInPhase;
    String phase;
    boolean isInfinite;
    Long internalNowInEpochMs; // uint64
}
