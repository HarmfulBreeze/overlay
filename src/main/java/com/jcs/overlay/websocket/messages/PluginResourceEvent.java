package com.jcs.overlay.websocket.messages;

public class PluginResourceEvent {
    public String getUri() {
        return this.uri;
    }

    public PluginResourceEventType getEventType() {
        return this.eventType;
    }

    private String uri;
    private PluginResourceEventType eventType;
}

