package com.jcs.overlay.websocket;

import com.squareup.moshi.Moshi;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LOGGER.error("An error has occurred.", ex);
    }

    @Override
    public void onStart() {
        LOGGER.info("WebSocket server started successfully.");
    }
}
