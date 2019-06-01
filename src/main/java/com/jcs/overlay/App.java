package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
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
    private WebSocketClient wsClient;
    private WebSocketServer wsServer;
    private WSAutoReconnect autoReconnect;
    private Thread autoReconnectThread;

    private App() {
        this.lockfileMonitor = new LockfileMonitor();
        this.lockfileMonitorThread = new Thread(this.lockfileMonitor);

        InetSocketAddress address = new InetSocketAddress("localhost", 8887);
        this.wsServer = new WSServer(address);
    }

    public static App getApp() {
        return app;
    }

    public static void main(String[] args) throws IOException {
        app = new App();
        app.start();
    }

    public WebSocketServer getWsServer() {
        return wsServer;
    }

    private void start() {
        this.lockfileMonitorThread.setName("Lockfile Monitor");
        this.lockfileMonitorThread.start();

        this.wsServer.start();

        new CefManager();
    }

    public LockfileMonitor getLockfileMonitor() {
        return lockfileMonitor;
    }

    public WebSocketClient getWsClient() {
        return wsClient;
    }

    void onLeagueStart(String lockfileContent) {
        logger.info("Le client est lancé !");

        String[] parts = Utils.parseLockfile(lockfileContent);
        String port = parts[2];
        String password = parts[3];

        // On ajoute le header d'authentification
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Authorization", Utils.fromPasswordToAuthToken(password));

        wsClient = new WSClient(URI.create("wss://127.0.0.1:" + port + "/"), httpHeaders);
        try {
            wsClient.connectBlocking(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }

        // On démarre le thread de reconnexion auto
        startAutoReconnect();
    }

    void onLeagueStop() {
        if (this.autoReconnect != null) {
            stopAutoReconnect();
            logger.info("Le client est fermé.");
        }
    }

    private void startAutoReconnect() {
        autoReconnect = new WSAutoReconnect();
        autoReconnectThread = new Thread(autoReconnect);
        autoReconnectThread.setName("WebSocket Auto Reconnect");
        autoReconnectThread.start();
    }

    private void stopAutoReconnect() {
        this.autoReconnect.stop();
        try {
            this.autoReconnectThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.autoReconnect = null;
        this.autoReconnectThread = null;
    }
}

