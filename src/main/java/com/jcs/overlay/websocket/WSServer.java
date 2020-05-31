package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.J2W.NewBanMessage;
import com.jcs.overlay.websocket.messages.J2W.WebappMessage;
import com.squareup.moshi.JsonAdapter;
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
    private static final Moshi MOSHI = new Moshi.Builder().build();

    // Private constructor
    private WSServer() {
        super(new InetSocketAddress("localhost", 8887));
    }

    // Instance getter
    public static WSServer getInstance() {
        return WSServerHolder.INSTANCE;
    }

    // Singleton holder
    private static class WSServerHolder {
        private static final WSServer INSTANCE = new WSServer();
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

    /**
     * Sends {@link WebappMessage}s to all the clients connected to the WebSocket server, most likely the webapp.
     *
     * @param clazz    The {@link Class} of the message(s) you want to send.
     *                 For example, if you want to send a {@link NewBanMessage}, use {@code NewBanMessage.class}.
     *                 This class <i>must</i> extend {@link WebappMessage}.
     * @param messages One or more messages of type {@code <T>} you want to be sent.
     * @param <T>      Should match the {@code clazz} parameter.
     */
    @SafeVarargs
    public final <T extends WebappMessage> void broadcastWebappMessage(Class<T> clazz, T... messages) {
        JsonAdapter<T> adapter = MOSHI.adapter(clazz);
        for (T message : messages) {
            this.broadcast(adapter.toJson(message));
        }
    }
}
