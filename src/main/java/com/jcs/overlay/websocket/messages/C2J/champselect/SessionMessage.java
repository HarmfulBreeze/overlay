package com.jcs.overlay.websocket.messages.C2J.champselect;

import com.jcs.overlay.websocket.messages.C2J.PluginResourceEvent;
import com.squareup.moshi.Json;

public class SessionMessage extends PluginResourceEvent {
    @Json(name = "data")
    Session session;

    public Session getSession() {
        return this.session;
    }
}

