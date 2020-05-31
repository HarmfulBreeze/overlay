package com.jcs.overlay.websocket.messages.J2W;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class PreloadSplashImagesMessage extends WebappMessage {
    private final String messageType = "PreloadSplashImages";
    private final List<String> champions;

    public PreloadSplashImagesMessage(List<String> championKeys) {
        this.champions = championKeys;
    }
}
