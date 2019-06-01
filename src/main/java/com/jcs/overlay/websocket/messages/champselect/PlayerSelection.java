package com.jcs.overlay.websocket.messages.champselect;

public class PlayerSelection {
    long cellId;
    int championId;
    int selectedSkinId;
    long wardSkinId;
    Long spell1Id; // uint64
    Long spell2Id; // uint64
    int team;
    String assignedPosition;
    int championPickIntent;
    String playerType;
    Long summonerId;
    String entitledFeatureType;

    public long getCellId() {
        return cellId;
    }

    public int getChampionId() {
        return championId;
    }

    public Long getSpell1Id() {
        return spell1Id;
    }

    public Long getSpell2Id() {
        return spell2Id;
    }

    public int getTeam() {
        return team;
    }

    public Long getSummonerId() {
        return summonerId;
    }
}
