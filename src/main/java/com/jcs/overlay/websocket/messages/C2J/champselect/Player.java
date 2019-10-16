package com.jcs.overlay.websocket.messages.C2J.champselect;

/**
 * Simple wrapper for the {@link PlayerSelection} class, adding summonerName.
 */
public class Player {
    private PlayerSelection playerSelection;
    private String summonerName;
    private long adjustedCellId;

    public Player(PlayerSelection playerSelection) {
        this(playerSelection, null);
    }

    public Player(PlayerSelection playerSelection, String summonerName) {
        this.playerSelection = playerSelection;
        this.summonerName = summonerName;
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
