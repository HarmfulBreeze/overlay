package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.*;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.Champions;
import com.merakianalytics.orianna.types.core.staticdata.SummonerSpells;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
                || SettingsManager.getConfig().getBoolean("debug.nogui")
                || System.getenv("OVERLAY_NOGUI") != null;
        init();
        start();
    }

    private static void init() {
        // Redirect System.out and System.err to SLF4J (useful for CEF)
        SysOutOverSLF4J.registerLoggingSystem("org.slf4j.log4j12");
        SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();

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
            CefManager.getInstance().init(); // Initialize CEF singleton
        }

        isStarted = true;
    }

    @Contract("_ -> fail") // Indicates that it WILL stop the application
    public static synchronized void stop(boolean force) {
        if (isClosed) {
            throw new IllegalStateException("App is already closed");
        }

        LOGGER.info("Shutting down...");

        LOGGER.debug("Shutting down CEF...");
        CefManager.getInstance().stop();
        LOGGER.debug("CEF has been shut down.");

        LOGGER.debug("Stopping settings watcher...");
        SettingsWatcher.getInstance().stop();
        try {
            settingsWatcherThread.join();
            LOGGER.debug("Settings watcher stopped.");
        } catch (InterruptedException e) {
            LOGGER.error("Error stopping settings watcher", e);
        }

        LOGGER.debug("Stopping lockfile monitor...");
        LockfileMonitor.getInstance().stop();
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

    public static void onLeagueStart(String lockfileContent) {
        if (autoReconnect != null) {
            onLeagueStop(); // fix for client closed abruptly
        }
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
        autoReconnect = new WSAutoReconnect(wsClient);
        autoReconnectThread = new Thread(autoReconnect);
        autoReconnectThread.setName("WebSocket Auto Reconnect");
        autoReconnectThread.start();
    }

    public static void onLeagueStop() {
        // Stop WSAutoReconnect
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

    public static WSClient getWsClient() {
        return wsClient;
    }
}

