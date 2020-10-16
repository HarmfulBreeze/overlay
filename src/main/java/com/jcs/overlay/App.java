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
    public static boolean noGUI;
    private static boolean isStarted = false;
    private static boolean isClosed = false;
    private static Thread lockfileMonitorThread;
    private static Thread settingsWatcherThread;
    private static WSClient wsClient;
    private static WSAutoReconnect autoReconnect;
    private static Thread autoReconnectThread;

    public static void main(String[] args) {
        noGUI = (args.length > 0 && args[0].equals("-nogui"))
                || SettingsManager.getManager().getConfig().getBoolean("debug.nogui");
        init();
        start();
    }

    private static void init() {
        // Orianna setup & pre-caching
        Orianna.loadConfiguration("config.json");
        Champions.get().load();
        SummonerSpells.get().load();

        // Assets updates
        AssetsUpdater.updateDDragonAssets();
        AssetsUpdater.updateCDragonAssets();

        // Create lockfile monitor thread
        lockfileMonitorThread = new Thread(LockfileMonitor.getInstance());
        lockfileMonitorThread.setName("Lockfile Monitor");

        // Create setttings watcher thread
        settingsWatcherThread = new Thread(SettingsWatcher.getInstance());
        settingsWatcherThread.setName("Settings Watcher");
    }

    public static void start() {
        if (isStarted) {
            throw new IllegalStateException("App is already started");
        }

        lockfileMonitorThread.start();
        WSServer.getInstance().start();
        settingsWatcherThread.start();
        if (!noGUI) {
            //noinspection ResultOfMethodCallIgnored
            CefManager.getInstance(); // Initialize CEF singleton
        }

        isStarted = true;
    }

    @Contract("_ -> fail") // Indicates that it WILL stop the application
    public static synchronized void stop(boolean force) {
        if (isClosed) {
            throw new IllegalStateException("App is already closed");
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
            settingsWatcherThread.join();
            LOGGER.debug("Settings watcher stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping settings watcher", e);
        }

        LockfileMonitor.getInstance().stop();
        LOGGER.debug("Waiting for lockfile monitor to close...");
        try {
            lockfileMonitorThread.join();
            LOGGER.debug("Lockfile monitor stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping lockfile monitor", e);
        }

        if (wsClient != null) {
            LOGGER.debug("Stopping wsClient...");
            try {
                wsClient.closeBlocking();
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

        if (autoReconnect != null) {
            LOGGER.debug("Stopping WSAutoReconnect...");
            autoReconnect.stop();
            try {
                autoReconnectThread.join();
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

    public static WSClient getWsClient() {
        return wsClient;
    }

    public static void onLeagueStart(String lockfileContent) {
        LOGGER.debug("Client launched!");

        String[] parts = Utils.parseLockfile(lockfileContent);
        String port = parts[2];
        String password = parts[3];

        // Auth header gets added
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Authorization", Utils.fromPasswordToAuthToken(password));

        wsClient = new WSClient(URI.create("wss://127.0.0.1:" + port + "/"), httpHeaders);
        try {
            wsClient.connectBlocking(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Exception caught:", e);
            Thread.currentThread().interrupt();
        }

        // Start autoreconnect thread
        autoReconnect = new WSAutoReconnect();
        autoReconnectThread = new Thread(autoReconnect);
        autoReconnectThread.setName("WebSocket Auto Reconnect");
        autoReconnectThread.start();
    }

    public static void onLeagueStop() {
        // Stop WSAutoReconnect
        if (autoReconnect != null) {
            autoReconnect.stop();
            try {
                autoReconnectThread.join();
            } catch (InterruptedException e) {
                LOGGER.error("Exception caught: ", e);
            }
            autoReconnect = null;
            autoReconnectThread = null;
            LOGGER.debug("Client closed.");
        }
    }
}

