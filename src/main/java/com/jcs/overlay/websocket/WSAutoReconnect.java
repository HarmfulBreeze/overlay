package com.jcs.overlay.websocket;

import com.jcs.overlay.utils.LockfileMonitor;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WSAutoReconnect implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WSAutoReconnect.class);
    private boolean shouldStop = false;
    private final WebSocketClient wsClient;

    public WSAutoReconnect(WSClient wsClient) {
        this.wsClient = wsClient;
    }

    @Override
    public void run() {
        int stopCount = 1;
        while (!this.shouldStop && stopCount <= 10) {
            try {
                if (!this.wsClient.isOpen()) {
                    LOGGER.info("Trying to reconnect... (" + stopCount + "/10)");
                    this.wsClient.reconnectBlocking();
                    if (this.wsClient.isOpen()) {
                        stopCount = 1;
                    } else {
                        LockfileMonitor.getInstance().setLeagueStarted(false);
                        LOGGER.error("Could not connect, next retry in 5 seconds...");
                        stopCount++;
                        Thread.sleep(5000);
                    }
                } else {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                LOGGER.error("Exception caught: ", e);
                Thread.currentThread().interrupt();
            }
        }
        if (stopCount == 11) {
            LOGGER.error("Connection lost, we will not try to reconnect anymore. Please restart the client.");
        }
    }

    public void stop() {
        this.shouldStop = true;
    }
}
