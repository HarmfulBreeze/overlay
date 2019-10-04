package com.jcs.overlay.websocket.messages.J2O;

public class ChampSelectCreateMessage {
    private final TeamNames teamNames;
    private final String messageType = "ChampSelectCreate";

    public ChampSelectCreateMessage(TeamNames teamNames) {
        this.teamNames = teamNames;
    }
}
