package com.jcs.overlay.websocket.messages.J2W;

import java.util.List;

public class PreloadSplashImagesMessage {
    private final String messageType = "PreloadSplashImages";
    private final List<String> champions;

    public PreloadSplashImagesMessage(List<String> championKeys) {
        this.champions = championKeys;
    }
}
