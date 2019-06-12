package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.Champions;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class App {
    private static App app;
    private final Logger logger = LoggerFactory.getLogger(App.class);
    private final LockfileMonitor lockfileMonitor;
    private final Thread lockfileMonitorThread;
    private final WebSocketServer wsServer;
    private WebSocketClient wsClient;
    private WSAutoReconnect autoReconnect;
    private Thread autoReconnectThread;

    private App() {
        this.lockfileMonitor = new LockfileMonitor();
        this.lockfileMonitorThread = new Thread(this.lockfileMonitor);

        InetSocketAddress address = new InetSocketAddress("localhost", 8887);
        this.wsServer = new WSServer(address);

        // Orianna
        Orianna.loadConfiguration("config.json");
        // Pre-caching
        Champions champions = Champions.get();
        champions.load();
    }

    public static void main(String[] args) {
        app = new App();
        app.start();
    }

    public static App getApp() {
        return app;
    }

    public WebSocketServer getWsServer() {
        return this.wsServer;
    }

    synchronized private void start() {
        this.lockfileMonitorThread.setName("Lockfile Monitor");
        this.lockfileMonitorThread.start();

        this.wsServer.start();

        new CefManager();
    }

    synchronized public void stop() {
        this.logger.info("Shutting down...");
        this.lockfileMonitor.stop();
        this.logger.debug("Waiting for lockfile monitor to close...");
        try {
            this.lockfileMonitorThread.join();
            this.logger.debug("Lockfile monitor stopped.");
        } catch (InterruptedException e) {
            this.logger.error("Error stopping lockfile monitor", e);
        }

        if (this.wsClient != null) {
            this.logger.debug("Stopping wsClient...");
            try {
                this.wsClient.closeBlocking();
                this.logger.debug("wsClient stopped.");
            } catch (InterruptedException e) {
                this.logger.error("Error stopping wsClient", e);
            }
        }

        this.logger.debug("Stopping wsServer...");
        try {
            this.wsServer.stop();
            this.logger.debug("wsServer stopped.");
        } catch (IOException | InterruptedException e) {
            this.logger.error("Error stopping wsServer", e);
        }

        if (this.autoReconnect != null) {
            this.logger.debug("Stopping WSAutoReconnect...");
            this.autoReconnect.stop();
            try {
                this.autoReconnectThread.join();
                this.logger.debug("WSAutoReconnect stopped.");
            } catch (InterruptedException e) {
                this.logger.error("Error stopping WSAutoReconnect", e);
            }
        }

        this.logger.info("Successfully shut down. Bye!");
    }

    public LockfileMonitor getLockfileMonitor() {
        return this.lockfileMonitor;
    }

    public WebSocketClient getWsClient() {
        return this.wsClient;
    }

    void onLeagueStart(String lockfileContent) {
        this.logger.debug("Client launched!");

        String[] parts = Utils.parseLockfile(lockfileContent);
        String port = parts[2];
        String password = parts[3];

        // On ajoute le header d'authentification
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Authorization", Utils.fromPasswordToAuthToken(password));

        this.wsClient = new WSClient(URI.create("wss://127.0.0.1:" + port + "/"), httpHeaders);
        try {
            this.wsClient.connectBlocking(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            this.logger.error("Exception caught:", e);
            Thread.currentThread().interrupt();
        }

        // On démarre le thread de reconnexion auto
        this.startAutoReconnect();
    }

    void onLeagueStop() {
        if (this.autoReconnect != null) {
            this.stopAutoReconnect();
            this.logger.debug("Client closed.");
        }
    }

    private void startAutoReconnect() {
        this.autoReconnect = new WSAutoReconnect();
        this.autoReconnectThread = new Thread(this.autoReconnect);
        this.autoReconnectThread.setName("WebSocket Auto Reconnect");
        this.autoReconnectThread.start();
    }

    private void stopAutoReconnect() {
        this.autoReconnect.stop();
        try {
            this.autoReconnectThread.join();
        } catch (InterruptedException e) {
            this.logger.error("Exception caught: ", e);
        }

        this.autoReconnect = null;
        this.autoReconnectThread = null;
    }
}

