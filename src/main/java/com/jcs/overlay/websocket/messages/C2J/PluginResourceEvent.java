package com.jcs.overlay.websocket.messages.C2J;

import com.squareup.moshi.Json;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class PluginResourceEvent {
    private String uri;
    private PluginResourceEventType eventType;

    public String getUri() {
        return this.uri;
    }

    public PluginResourceEventType getEventType() {
        return this.eventType;
    }

    public enum PluginResourceEventType {
        @Json(name = "Create") CREATE,
        @Json(name = "Update") UPDATE,
        @Json(name = "Delete") DELETE
    }
}

