package com.jcs.overlay.websocket.messages.champselect;

/**
 * Simple wrapper for the {@link PlayerSelection} class, adding summonerName.
 */
public class Player {
    private final PlayerSelection playerSelection;
    private String summonerName;

    public Player(PlayerSelection playerSelection) {
        this(playerSelection, null);
    }

    public Player(PlayerSelection playerSelection, String summonerName) {
        this.playerSelection = playerSelection;
        this.summonerName = summonerName;
    }

    public PlayerSelection getPlayerSelection() {
        return playerSelection;
    }

    public String getSummonerName() {
        return summonerName;
    }

    public void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }
}
