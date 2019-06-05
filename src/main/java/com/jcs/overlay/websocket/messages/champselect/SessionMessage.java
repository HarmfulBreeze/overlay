package com.jcs.overlay.websocket.messages.champselect;

import com.jcs.overlay.websocket.messages.PluginResourceEvent;
import com.squareup.moshi.Json;

public class SessionMessage extends PluginResourceEvent {
    @Json(name = "data")
    Session session;

    public Session getSession() {
        return this.session;
    }
}

