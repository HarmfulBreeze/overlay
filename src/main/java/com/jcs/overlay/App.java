package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.*;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.Champions;
import com.merakianalytics.orianna.types.core.staticdata.SummonerSpells;
import org.cef.CefApp;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.cef.CefApp.CefAppState.NONE;
import static org.cef.CefApp.CefAppState.TERMINATED;

public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static App APP;
    private static boolean noGUI;
    private static boolean isClosed = false;
    private final Thread lockfileMonitorThread;
    private final Thread settingsWatcherThread;
    private WSClient wsClient;
    private WSAutoReconnect autoReconnect;
    private Thread autoReconnectThread;

    private App() {
        // Orianna
        Orianna.loadConfiguration("config.json");

        // Pre-caching
        Champions.get().load();
        SummonerSpells.get().load();

        // Assets updates
        AssetsUpdater.updateDDragonAssets();
        AssetsUpdater.updateCDragonAssets();

        // Create lockfile monitor thread
        this.lockfileMonitorThread = new Thread(LockfileMonitor.getInstance());
        this.lockfileMonitorThread.setName("Lockfile Monitor");

        // Create setttings watcher thread
        this.settingsWatcherThread = new Thread(SettingsWatcher.getInstance());
        this.settingsWatcherThread.setName("Settings Watcher");
    }

    public static boolean isNoGUI() {
        return noGUI;
    }

    public static void main(String[] args) {
        noGUI = (args.length > 0 && args[0].equals("-nogui"))
                || SettingsManager.getManager().getConfig().getBoolean("debug.nogui");
        APP = new App();
        APP.start();
    }

    public static App getApp() {
        return APP;
    }

    synchronized public void start() {
        this.lockfileMonitorThread.start();
        WSServer.getInstance().start();
        this.settingsWatcherThread.start();
        if (!noGUI) {
            //noinspection ResultOfMethodCallIgnored
            CefManager.getInstance(); // Initialize CEF singleton
        }
    }

    @Contract("_ -> fail") // Indicates that it WILL stop the application
    synchronized public void stop(boolean force) {
        if (isClosed) {
            return;
        }

        LOGGER.info("Shutting down...");

        CefApp.CefAppState cefAppState = CefApp.getState();
        // Dispose of CefApp only if it's currently running
        if (cefAppState != NONE && cefAppState != TERMINATED) {
            CefApp.getInstance().dispose();
        }
        // Dispose of the mainframe only if it has existed
        if (cefAppState != NONE) {
            CefManager.getInstance().getMainFrame().dispose();
        }

        SettingsWatcher.getInstance().stop();
        LOGGER.debug("Stopping settings watcher...");
        try {
            this.settingsWatcherThread.join();
            LOGGER.debug("Settings watcher stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping settings watcher", e);
        }

        LockfileMonitor.getInstance().stop();
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
            WSServer.getInstance().stop();
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

    public WSClient getWsClient() {
        return this.wsClient;
    }

    public void onLeagueStart(String lockfileContent) {
        LOGGER.debug("Client launched!");

        String[] parts = Utils.parseLockfile(lockfileContent);
        String port = parts[2];
        String password = parts[3];

        // Auth header gets added
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Authorization", Utils.fromPasswordToAuthToken(password));

        this.wsClient = new WSClient(URI.create("wss://127.0.0.1:" + port + "/"), httpHeaders);
        try {
            this.wsClient.connectBlocking(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Exception caught:", e);
            Thread.currentThread().interrupt();
        }

        // Start autoreconnect thread
        this.autoReconnect = new WSAutoReconnect();
        this.autoReconnectThread = new Thread(this.autoReconnect);
        this.autoReconnectThread.setName("WebSocket Auto Reconnect");
        this.autoReconnectThread.start();
    }

    public void onLeagueStop() {
        // Stop WSAutoReconnect
        if (this.autoReconnect != null) {
            this.autoReconnect.stop();
            try {
                this.autoReconnectThread.join();
            } catch (InterruptedException e) {
                LOGGER.error("Exception caught: ", e);
            }
            this.autoReconnect = null;
            this.autoReconnectThread = null;
            LOGGER.debug("Client closed.");
        }
    }
}

