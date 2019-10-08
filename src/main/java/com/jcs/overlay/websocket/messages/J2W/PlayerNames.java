package com.jcs.overlay.websocket.messages.J2W;

import java.util.Map;

public class PlayerNames {
    private final Map<Integer, String> players;
    private final String messageType = "PlayerNames";

    public PlayerNames(Map<Integer, String> players) {
        this.players = players;
    }
}
