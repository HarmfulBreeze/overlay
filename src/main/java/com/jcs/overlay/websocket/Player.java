package com.jcs.overlay.websocket;

import com.jcs.overlay.websocket.messages.C2J.champselect.PlayerSelection;

/**
 * Simple wrapper for the {@link PlayerSelection} class, adding summonerName and adjustedCellId.
 */
class Player {
    private PlayerSelection playerSelection;
    private String summonerName;
    private Long adjustedCellId;

    Player(PlayerSelection playerSelection) {
        this(playerSelection, null, null);
    }

    Player(PlayerSelection playerSelection, String summonerName, Long adjustedCellId) {
        this.playerSelection = playerSelection;
        this.summonerName = summonerName;
        this.adjustedCellId = adjustedCellId;
    }

    long getAdjustedCellId() {
        return this.adjustedCellId;
    }

    void setAdjustedCellId(long adjustedCellId) {
        this.adjustedCellId = adjustedCellId;
    }

    PlayerSelection getPlayerSelection() {
        return this.playerSelection;
    }

    void setPlayerSelection(PlayerSelection ps) {
        this.playerSelection = ps;
    }

    String getSummonerName() {
        return this.summonerName;
    }

    void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }
}
