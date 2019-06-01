package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WSAutoReconnect implements Runnable {
    public void stop() {
        this.shouldStop = true;
    }

    private boolean shouldStop;
    private final Logger logger = LoggerFactory.getLogger(WSAutoReconnect.class);

    public WSAutoReconnect() {
        this.shouldStop = false;
    }

    @Override
    public void run() {
        App app = App.getApp();
        WebSocketClient client = app.getWsClient();
        while (client == null) {
            try {
                Thread.sleep(500);
                client = app.getWsClient();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int stopCount = 1;
        while (!shouldStop && stopCount <= 10) {
            try {
                if (!client.isOpen()) {
                    logger.info("Reconnexion en cours... (essai " + stopCount + "/10)");
                    client.reconnectBlocking();
                    if (client.isOpen()) {
                        stopCount = 1;
                    } else {
                        App.getApp().getLockfileMonitor().setLeagueStarted(false);
                        logger.error("Echec de la connexion, nouvel essai dans 5 secondes...");
                        stopCount++;
                        Thread.sleep(200);
                    }
                } else {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        if (stopCount == 11) {
            logger.error("La connexion a été perdue, arrêt de la reconnexion.");
        }
    }
}
