package com.jcs.overlay.websocket.holders;

import com.jcs.overlay.websocket.messages.C2J.champselect.PlayerSelection;

/**
 * Simple wrapper for the {@link PlayerSelection} class, adding summonerName and adjustedCellId.
 */
public class Player extends PlayerSelection {
    private String summonerName;
    private Long adjustedCellId;

    public long getAdjustedCellId() {
        return this.adjustedCellId;
    }

    public void setAdjustedCellId(long adjustedCellId) {
        this.adjustedCellId = adjustedCellId;
    }

    public String getSummonerName() {
        return this.summonerName;
    }

    public void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }
}
