package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.LockfileMonitor;
import com.jcs.overlay.utils.SettingsManager;
import com.jcs.overlay.utils.SettingsWatcher;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.Champions;
import com.merakianalytics.orianna.types.core.staticdata.SummonerSpells;
import org.cef.CefApp;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static App APP;
    private static boolean guiEnabled;
    private static boolean isClosed = false;
    private final SettingsWatcher settingsWatcher;
    private final LockfileMonitor lockfileMonitor;
    private final Thread lockfileMonitorThread;
    private final WebSocketServer wsServer;
    private WebSocketClient wsClient;
    private WSAutoReconnect autoReconnect;
    private Thread autoReconnectThread;
    private CefManager cefManager;
    private Thread settingsWatcherThread;

    private App() {
        this.lockfileMonitor = new LockfileMonitor();
        this.lockfileMonitorThread = new Thread(this.lockfileMonitor);

        InetSocketAddress address = new InetSocketAddress("localhost", 8887);
        this.wsServer = new WSServer(address);

        // Orianna
        Orianna.loadConfiguration("config.json");

        // Pre-caching
        Champions.get().load();
        SummonerSpells.get().load();
        if (Utils.checkForNewPatch()) {
            LOGGER.info("New patch detected! Updating webapp images.");
            Utils.updateWebappImages();
            Utils.updateLatestPatchFile();
        }

        this.lockfileMonitorThread.setName("Lockfile Monitor");

        this.settingsWatcher = new SettingsWatcher();
        this.settingsWatcherThread = new Thread(this.settingsWatcher);
        this.settingsWatcherThread.setName("Settings Watcher");
    }

    public static void main(String[] args) {
        guiEnabled = (args.length <= 0 || !args[0].equals("-nogui"))
                && !SettingsManager.getManager().getConfig().getBoolean("debug.nogui");
        APP = new App();
        APP.start();
    }

    public static App getApp() {
        return APP;
    }

    public WebSocketServer getWsServer() {
        return this.wsServer;
    }

    synchronized public void start() {
        this.lockfileMonitorThread.start();
        this.wsServer.start();
        this.settingsWatcherThread.start();
        this.cefManager = guiEnabled ? new CefManager() : null;
    }

    synchronized public void stop(boolean force) {
        if (isClosed) {
            return;
        }

        LOGGER.info("Shutting down...");

        if (CefApp.getState() != CefApp.CefAppState.TERMINATED) {
            CefApp.getInstance().dispose();
        }
        this.cefManager.getMainFrame().dispose();

        this.settingsWatcher.stop();
        LOGGER.debug("Stopping settings watcher...");
        try {
            this.settingsWatcherThread.join();
            LOGGER.debug("Settings watcher stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping settings watcher", e);
        }

        this.lockfileMonitor.stop();
        LOGGER.debug("Waiting for lockfile monitor to close...");
        try {
            this.lockfileMonitorThread.join();
            LOGGER.debug("Lockfile monitor stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping lockfile monitor", e);
        }

        if (this.wsClient != null) {
            LOGGER.debug("Stopping wsClient...");
            try {
                this.wsClient.closeBlocking();
                LOGGER.debug("wsClient stopped.");
            } catch (InterruptedException e) {
                LOGGER.error("Error stopping wsClient", e);
            }
        }

        LOGGER.debug("Stopping wsServer...");
        try {
            this.wsServer.stop();
            LOGGER.debug("wsServer stopped.");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error stopping wsServer", e);
        }

        if (this.autoReconnect != null) {
            LOGGER.debug("Stopping WSAutoReconnect...");
            this.autoReconnect.stop();
            try {
                this.autoReconnectThread.join();
                LOGGER.debug("WSAutoReconnect stopped.");
            } catch (InterruptedException e) {
                LOGGER.error("Error stopping WSAutoReconnect", e);
            }
        }

        isClosed = true;

        LOGGER.info("Successfully shut down. Bye!");

        if (force) {
            System.exit(0);
        }
    }

    public LockfileMonitor getLockfileMonitor() {
        return this.lockfileMonitor;
    }

    public WebSocketClient getWsClient() {
        return this.wsClient;
    }

    public void onLeagueStart(String lockfileContent) {
        LOGGER.debug("Client launched!");

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
            LOGGER.error("Exception caught:", e);
            Thread.currentThread().interrupt();
        }

        // On démarre le thread de reconnexion auto
        this.startAutoReconnect();
    }

    public void onLeagueStop() {
        if (this.autoReconnect != null) {
            this.stopAutoReconnect();
            LOGGER.debug("Client closed.");
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
            LOGGER.error("Exception caught: ", e);
        }

        this.autoReconnect = null;
        this.autoReconnectThread = null;
    }

}

