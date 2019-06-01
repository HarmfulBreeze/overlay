package com.jcs.overlay.websocket.messages.champselect;

import com.jcs.overlay.websocket.messages.PluginResourceEvent;

public class SessionMessage extends PluginResourceEvent {
    public Session getData() {
        return data;
    }

    Session data;
}

