package com.jcs.overlay.websocket.messages.C2J.summoner;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class SummonerIdAndName {
    private Long summonerId;
    private String displayName;

    public Long getSummonerId() {
        return this.summonerId;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}
