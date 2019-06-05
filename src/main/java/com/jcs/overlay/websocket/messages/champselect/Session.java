package com.jcs.overlay.websocket.messages.champselect;

import java.util.List;

public class Session {
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

    public List<List<Action>> getActions() {
        return this.actions;
    }

    public void setActions(List<List<Action>> actions) {
        this.actions = actions;
    }

    public BannedChampions getBans() {
        return this.bans;
    }

    public long getCounter() {
        return this.counter;
    }

    public boolean isSpectating() {
        return this.isSpectating;
    }

    public long getLocalPlayerCellId() {
        return this.localPlayerCellId;
    }

    public List<PlayerSelection> getMyTeam() {
        return this.myTeam;
    }

    public List<PlayerSelection> getTheirTeam() {
        return this.theirTeam;
    }

    public Timer getTimer() {
        return this.timer;
    }

    public List<TradeContract> getTrades() {
        return this.trades;
    }
}
