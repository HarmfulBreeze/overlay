package com.jcs.overlay;

import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.WSAutoReconnect;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.core.staticdata.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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
    private static App app;
    private final Logger logger = LoggerFactory.getLogger(App.class);
    private final LockfileMonitor lockfileMonitor;
    private final Thread lockfileMonitorThread;
    private final WebSocketServer wsServer;
    private static boolean guiEnabled;
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
        Champions.get().load();
        if (this.checkForNewPatch()) {
            this.logger.info("New patch detected! Updating webapp images.");
            this.updateWebappImages();
            this.updateLatestPatchFile();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("-nogui")) {
            guiEnabled = false;
        } else {
            guiEnabled = true;
        }
        app = new App();
        app.start();
    }

    public static App getApp() {
        return app;
    }

    private boolean checkForNewPatch() {
        File latestVersionFile = new File(System.getProperty("user.dir") + "/latestPatch.txt");
        String latestVersion = Versions.get().get(0);
        try {
            if (latestVersionFile.createNewFile()) {
                return true;
            }
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
            return true;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(latestVersionFile))) {
            String s = reader.readLine();
            return s == null || !s.equals(latestVersion);
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
            return true;
        }
    }

    private void updateLatestPatchFile() {
        File latestVersionFile = new File(System.getProperty("user.dir") + "/latestPatch.txt");
        String latestVersion = Versions.get().get(0);
        try (FileWriter writer = new FileWriter(latestVersionFile)) {
            writer.write(latestVersion);
        } catch (IOException e) {
            this.logger.info(e.getMessage(), e);
        }
    }

    public WebSocketServer getWsServer() {
        return this.wsServer;
    }

    synchronized private void start() {
        this.lockfileMonitorThread.setName("Lockfile Monitor");
        this.lockfileMonitorThread.start();

        this.wsServer.start();

        if (this.guiEnabled) {
            new CefManager();
        }
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
        System.exit(0);
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

    private void updateWebappImages() {
        String imgFolderPath = System.getProperty("user.dir") + "/web/img/";

        // Download all summoner spells images and write them to pngs
        SummonerSpells spells = SummonerSpells.get();
        for (SummonerSpell spell : spells) {
            File file = new File(imgFolderPath + "icon/spell/" + spell.getId() + ".png");
            try {
                ImageIO.write(spell.getImage().get(), "png", file);
            } catch (IOException e) {
                this.logger.error(e.getMessage(), e);
            }
        }

        // Download all champion icons and write them to pngs
        Champions allChampions = Champions.get();
        for (Champion champion : allChampions) {
            File file = new File(imgFolderPath + "icon/champion/icon_" + champion.getKey() + ".png");
            try {
                ImageIO.write(champion.getImage().get(), "png", file);
            } catch (IOException e) {
                this.logger.error(e.getMessage(), e);
            }
        }

        // Download the generic champion icon directly from the CDN and write it to a png
        OkHttpClient client = new OkHttpClient();
        String latestVersion = Versions.get().get(0);
        String url = "https://cdn.communitydragon.org/" + latestVersion + "/champion/generic/square";
        Request request = new Request.Builder().url(url).build();
        this.logger.info("Making GET request to " + url);
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                InputStream is = response.body().byteStream();
                File file = new File(imgFolderPath + "icon/champion/icon_None.png");
                BufferedImage img = ImageIO.read(is);
                ImageIO.write(img, "png", file);
            }
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
        }

        // Download every champion's centered splash art and write them to pngs
        for (Champion champion : allChampions) {
            String championKey = champion.getKey();
            url = "https://cdn.communitydragon.org/" + latestVersion + "/champion/" + championKey + "/splash-art/centered";
            request = new Request.Builder().url(url).build();
            this.logger.info("Making GET request to " + url);
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 200 && response.body() != null) {
                    InputStream is = response.body().byteStream();
                    File file = new File(imgFolderPath + "splash/" + championKey + ".png");
                    BufferedImage img = ImageIO.read(is);
                    ImageIO.write(img, "png", file);
                }
            } catch (IOException e) {
                this.logger.error(e.getMessage(), e);
            }
        }
    }
}

