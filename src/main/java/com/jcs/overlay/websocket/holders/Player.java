package com.jcs.overlay.websocket.holders;

import com.jcs.overlay.websocket.messages.C2J.champselect.PlayerSelection;

/**
 * Simple wrapper for the {@link PlayerSelection} class, adding summonerName and adjustedCellId.
 */
public class Player {
    private PlayerSelection playerSelection;
    private String summonerName;
    private Long adjustedCellId;

    public Player(PlayerSelection playerSelection) {
        this(playerSelection, null, null);
    }

    public Player(PlayerSelection playerSelection, String summonerName, Long adjustedCellId) {
        this.playerSelection = playerSelection;
        this.summonerName = summonerName;
        this.adjustedCellId = adjustedCellId;
    }

    public long getAdjustedCellId() {
        return this.adjustedCellId;
    }

    public void setAdjustedCellId(long adjustedCellId) {
        this.adjustedCellId = adjustedCellId;
    }

    public PlayerSelection getPlayerSelection() {
        return this.playerSelection;
    }

    public void setPlayerSelection(PlayerSelection ps) {
        this.playerSelection = ps;
    }

    public String getSummonerName() {
        return this.summonerName;
    }

    public void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }
}
