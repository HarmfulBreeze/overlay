package com.jcs.overlay.websocket.messages.C2J.champselect;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class TradeContract {
    long id;
    long cellId;
    TradeState state;

    @SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
    public enum TradeState {
        // INVALID does not seem to be in the lol-champ-select source code... might want to remove it?
        ACCEPTED, AVAILABLE, BUSY, CANCELLED, DECLINED, INVALID, RECEIVED, SENT
    }
}
