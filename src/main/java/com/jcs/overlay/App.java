package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.SettingsWatcher;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.cef.CefApp;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class App {
    private static App APP;
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static boolean guiEnabled;
    private final LockfileMonitor lockfileMonitor;
    private final Thread lockfileMonitorThread;
    private final WebSocketServer wsServer;
    private WebSocketClient wsClient;
    private WSAutoReconnect autoReconnect;
    private Thread autoReconnectThread;
    private CefManager cefManager;

    private App() {
        this.lockfileMonitor = new LockfileMonitor();
        this.lockfileMonitorThread = new Thread(this.lockfileMonitor);

        InetSocketAddress address = new InetSocketAddress("localhost", 8887);
        this.wsServer = new WSServer(address);

        // Orianna
        Orianna.loadConfiguration("config.json");
        // Pre-caching
        Champions.get().load();
        if (this.checkForNewPatch()) {
            LOGGER.info("New patch detected! Updating webapp images.");
            this.updateWebappImages();
            this.updateLatestPatchFile();
        }
    }

    public static void main(String[] args) {
        guiEnabled = args.length <= 0 || !args[0].equals("-nogui");
        APP = new App();
        APP.start();
    }

    public static App getApp() {
        return APP;
    }

    private boolean checkForNewPatch() {
        File latestVersionFile = new File(System.getProperty("user.dir") + "/latestPatch.txt");
        String latestVersion = Versions.get().get(0);
        try {
            if (latestVersionFile.createNewFile()) {
                return true;
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return true;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(latestVersionFile))) {
            String s = reader.readLine();
            return s == null || !s.equals(latestVersion);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return true;
        }
    }

    private void updateLatestPatchFile() {
        File latestVersionFile = new File(System.getProperty("user.dir") + "/latestPatch.txt");
        String latestVersion = Versions.get().get(0);
        try (FileWriter writer = new FileWriter(latestVersionFile)) {
            writer.write(latestVersion);
        } catch (IOException e) {
            LOGGER.info(e.getMessage(), e);
        }
    }

    public WebSocketServer getWsServer() {
        return this.wsServer;
    }

    synchronized private void start() {
        this.lockfileMonitorThread.setName("Lockfile Monitor");
        this.lockfileMonitorThread.start();

        this.wsServer.start();

        Thread settingsWatcherThread = new Thread(new SettingsWatcher());
        settingsWatcherThread.setName("Settings Watcher");
        settingsWatcherThread.start();

        if (guiEnabled) {
            this.cefManager = new CefManager();
        }
    }

    synchronized public void stop(boolean shouldRestart) {
        LOGGER.info("Shutting down...");
        if (CefApp.getState() != CefApp.CefAppState.TERMINATED) {
            CefApp.getInstance().dispose();
        }
        this.cefManager.getMainFrame().dispose();
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

        LOGGER.info("Successfully shut down. Bye!");

        if (shouldRestart) {
            APP = new App();
            APP.start();
        } else {
            System.exit(0);
        }
    }

    public LockfileMonitor getLockfileMonitor() {
        return this.lockfileMonitor;
    }

    public WebSocketClient getWsClient() {
        return this.wsClient;
    }

    void onLeagueStart(String lockfileContent) {
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

    void onLeagueStop() {
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

    private void updateWebappImages() {
        String imgFolderPath = System.getProperty("user.dir") + "/web/img/";

        // Download all summoner spells images and write them to pngs
        SummonerSpells spells = SummonerSpells.get();
        for (SummonerSpell spell : spells) {
            File file = new File(imgFolderPath + "icon/spell/" + spell.getId() + ".png");
            try {
                file.getParentFile().mkdirs();
                ImageIO.write(spell.getImage().get(), "png", file);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download all champion icons and write them to pngs
        Champions allChampions = Champions.get();
        for (Champion champion : allChampions) {
            File file = new File(imgFolderPath + "icon/champion/icon_" + champion.getKey() + ".png");
            try {
                file.getParentFile().mkdirs();
                ImageIO.write(champion.getImage().get(), "png", file);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download the generic champion icon directly from the CDN and write it to a png
        OkHttpClient client = new OkHttpClient();
        String latestVersion = Versions.get().get(0);
        String url = "https://cdn.communitydragon.org/" + latestVersion + "/champion/generic/square";
        Request request = new Request.Builder().url(url).build();
        LOGGER.info("Making GET request to " + url);
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                InputStream is = response.body().byteStream();
                File file = new File(imgFolderPath + "icon/champion/icon_None.png");
                BufferedImage img = ImageIO.read(is);
                ImageIO.write(img, "png", file);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Download every champion's centered splash art and write them to pngs
        for (Champion champion : allChampions) {
            String championKey = champion.getKey();
            url = "https://cdn.communitydragon.org/" + latestVersion + "/champion/" + championKey + "/splash-art/centered";
            request = new Request.Builder().url(url).build();
            LOGGER.info("Making GET request to " + url);
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 200 && response.body() != null) {
                    InputStream is = response.body().byteStream();
                    File file = new File(imgFolderPath + "splash/" + championKey + ".png");
                    file.getParentFile().mkdirs();
                    BufferedImage img = ImageIO.read(is);
                    ImageIO.write(img, "png", file);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}

