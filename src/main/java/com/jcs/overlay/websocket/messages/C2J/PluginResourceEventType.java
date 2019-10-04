package com.jcs.overlay.websocket.messages.C2J;

import com.squareup.moshi.Json;

public enum PluginResourceEventType {
    @Json(name = "Create") CREATE,
    @Json(name = "Update") UPDATE,
    @Json(name = "Delete") DELETE
}
