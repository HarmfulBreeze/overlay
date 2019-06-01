package com.jcs.overlay.websocket.messages.champselect;

import java.util.List;

public class Session {
    public List<List<Action>> getActions() {
        return actions;
    }

    public BannedChampions getBans() {
        return bans;
    }

    public long getCounter() {
        return counter;
    }

    public boolean isSpectating() {
        return isSpectating;
    }

    public long getLocalPlayerCellId() {
        return localPlayerCellId;
    }

    public List<PlayerSelection> getMyTeam() {
        return myTeam;
    }

    public List<PlayerSelection> getTheirTeam() {
        return theirTeam;
    }

    public Timer getTimer() {
        return timer;
    }

    public List<TradeContract> getTrades() {
        return trades;
    }

    List<List<Action>> actions;
    boolean allowBattleBoost;
    boolean allowDuplicatePicks;
    boolean allowLockedEvents;
    boolean allowRerolling;
    boolean allowSkinSelection;
    BannedChampions bans;
    List<Integer> benchChampionIds;
    boolean benchEnabled;
    int boostableSkinCount;
    ChatRoomDetails chatDetails;
    long counter;
    EntitledFeatureState entitledFeatureState;
    boolean isSpectating;
    long localPlayerCellId;
    int lockedEventIndex;
    List<PlayerSelection> myTeam; // correspond à la blue team
    long rerollsRemaining; // uint32
    List<PlayerSelection> theirTeam;
    Timer timer;
    List<TradeContract> trades;
}
