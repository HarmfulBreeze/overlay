package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WSAutoReconnect implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(WSAutoReconnect.class);
    private boolean shouldStop;
    public WSAutoReconnect() {
        this.shouldStop = false;
    }

    public void stop() {
        this.shouldStop = true;
    }

    @Override
    public void run() {
        WebSocketClient client = App.getApp().getWsClient();
        while (client == null) {
            try {
                Thread.sleep(500);
                client = App.getApp().getWsClient();
            } catch (InterruptedException e) {
                this.logger.error("Exception caught: ", e);
            }
        }

        int stopCount = 1;
        while (!this.shouldStop && stopCount <= 10) {
            try {
                if (!client.isOpen()) {
                    this.logger.info("Trying to reconnect... (" + stopCount + "/10)");
                    client.reconnectBlocking();
                    if (client.isOpen()) {
                        stopCount = 1;
                    } else {
                        App.getApp().getLockfileMonitor().setLeagueStarted(false);
                        this.logger.error("Could not connect, next retry in 5 seconds...");
                        stopCount++;
                        Thread.sleep(5000);
                    }
                } else {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                this.logger.error("Exception caught: ", e);
                Thread.currentThread().interrupt();
            }
        }
        if (stopCount == 11) {
            this.logger.error("Connection lost, we will not try to reconnect anymore. Please restart the client.");
        }
    }
}
