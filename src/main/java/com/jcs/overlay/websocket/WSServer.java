package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.squareup.moshi.Moshi;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.net.InetSocketAddress;

public class WSServer extends WebSocketServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WSServer.class);
    private final Moshi moshi = new Moshi.Builder().build();

    public WSServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.info("Connected to the CEF page!");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        LOGGER.info("Disconnected from the CEF page.");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        LOGGER.info("Received message: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (ex instanceof BindException) {
            LOGGER.error("The exception above means that overlay is already open or that something on your computer uses port 8887.");
            LOGGER.error("Make sure to close the other instance and check for conflicting processes.");
            App.getApp().stop(true);
        } else {
            LOGGER.error("An error has occurred.", ex);
        }
    }

    @Override
    public void onStart() {
        LOGGER.info("WebSocket server started successfully.");
    }
}
