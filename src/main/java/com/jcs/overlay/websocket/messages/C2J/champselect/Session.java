package com.jcs.overlay.websocket.messages.C2J.champselect;

import com.jcs.overlay.websocket.holders.Player;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class Session {
    List<List<Action>> actions;
    BannedChampions bans;
    List<Integer> benchChampionIds;
    boolean benchEnabled;
    long counter; // unk
    boolean hasSimultaneousBans;
    boolean hasSimultaneousPicks;
    boolean isCustomGame;
    boolean isSpectating;
    long localPlayerCellId;
    int lockedEventIndex; // unk
    List<Player> myTeam;
    long rerollsRemaining; // uint32
    List<Player> theirTeam;
    Timer timer;
    List<TradeContract> trades;

    public List<List<Action>> getActions() {
        return this.actions;
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

    public List<Player> getMyTeam() {
        return this.myTeam;
    }

    public List<Player> getTheirTeam() {
        return this.theirTeam;
    }

    public Timer getTimer() {
        return this.timer;
    }
}
