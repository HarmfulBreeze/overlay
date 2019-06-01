package com.jcs.overlay.websocket.messages;

public class PluginResourceEvent {
    public String getUri() {
        return uri;
    }

    public PluginResourceEventType getEventType() {
        return eventType;
    }

    private String uri;
    private PluginResourceEventType eventType;
}

