package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.staticdata.*;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AssetsUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsUpdater.class);
    private static final String IMG_FOLDER_PATH = System.getProperty("user.dir") + "/web/img/";

    private AssetsUpdater() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    public static void updateDDragonAssets() {
        LOGGER.info("Checking for updated DDragon assets...");
        if (checkForNewDDragonPatch()) {
            LOGGER.info("New DDragon assets were released. Updating...");
            performDDragonUpdate();
        } else {
            LOGGER.info("No updated DDragon assets available.");
        }
    }

    public static void updateCDragonAssets() {
        LOGGER.info("Checking for updated CDragon assets...");
        boolean newAssetsAvailable;
        try {
            newAssetsAvailable = checkForNewCDragonPatch();
        } catch (IOException e) {
            LOGGER.error("Could not verify the availability of updated CDragon assets.", e);
            return;
        }
        if (newAssetsAvailable) {
            LOGGER.info("New CDragon assets are available. Updating...");
            performCDragonUpdate();
        } else {
            LOGGER.info("No updated CDragon assets available.");
        }
    }

    /**
     * Checks if a new DDragon version was released since the last startup.
     *
     * @return {@code true} if a new version was released, else {@code false}.
     */
    private static boolean checkForNewDDragonPatch() {
        String localVersion = SettingsManager.getManager().getConfig().getString("debug.ddragonPatch");
        String latestVersion = Versions.get().get(0);
        return !localVersion.equals(latestVersion);
    }

    /**
     * Checks if a new CDragon Raw version was released since the last startup.
     *
     * @return {@code true} if a new patch was released, else {@code false}.
     */
    private static boolean checkForNewCDragonPatch() throws IOException { // TODO: move check logic in this function
        // localVersion is in game version format
        String localVersion = SettingsManager.getManager().getConfig().getString("debug.cdragonPatch");
        String latestVersion = Versions.get().get(0);
        if (!localVersion.equals(latestVersion)) {
            OkHttpClient client = new OkHttpClient();
            String[] latestCDragonVersion;
            latestCDragonVersion = getLatestCDragonVersion(client);
            return !latestCDragonVersion[1].equals(localVersion);
        } else {
            return false;
        }
    }

    private static void performDDragonUpdate() {
        OkHttpClient client = new OkHttpClient();

        // Download all summoner spells images and write them to PNG files
        SummonerSpells spells = SummonerSpells.get();
        for (SummonerSpell spell : spells) {
            try {
                Path path = Paths.get(IMG_FOLDER_PATH + "icon/spell/" + spell.getId() + ".png");
                Files.createDirectories(path.getParent());
                try (OutputStream os = Files.newOutputStream(path)) {
                    ImageIO.write(spell.getImage().get(), "png", os);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download all champion icons and write them to PNG files
        Champions allChampions = Champions.get();
        for (Champion champion : allChampions) {
            try {
                Path path = Paths.get(IMG_FOLDER_PATH + "icon/champion/icon_" + champion.getKey() + ".png");
                Files.createDirectories(path.getParent());
                try (OutputStream os = Files.newOutputStream(path)) {
                    ImageIO.write(champion.getImage().get(), "png", os);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Shutdown our OkHttp client
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();


        // Update latest patch in config
        String latestVersion = Versions.get().get(0);
        SettingsManager.getManager().updateValue("debug.latestPatch",
                ConfigValueFactory.fromAnyRef(latestVersion));
    }

    private static void performCDragonUpdate() {
        OkHttpClient client = new OkHttpClient();

        Champions allChampions = Champions.get();
        String[] latestCDragonVersion;
        try {
            latestCDragonVersion = getLatestCDragonVersion(client);
        } catch (IOException e) {
            LOGGER.error("Could not retrieve latest CDragon version. Skipping CDragon update.");
            return;
        }

        if (latestCDragonVersion[1].equals(SettingsManager.getManager().getConfig().getString("debug.cdragonPatch"))) {
            LOGGER.warn("CDragon still has outdated data, skipping CDragon update.");
            return;
        }

        // Download the generic champion icon and write it to a PNG file
        String url = "https://raw.communitydragon.org/" + latestCDragonVersion[0] +
                "/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/-1.png";
        Request request = new Request.Builder().url(url).build();
        LOGGER.info("Making GET request to " + url);
        try (Response response = client.newCall(request).execute();
             ResponseBody body = response.body()) {
            assert body != null; // Body is non-null as it comes from Call#execute
            if (response.code() == 200) {
                Path path = Paths.get(IMG_FOLDER_PATH, "icon/champion/icon_None.png");
                Files.createDirectories(path.getParent());
                try (InputStream is = body.byteStream();
                     OutputStream os = Files.newOutputStream(path)) {
                    BufferedImage img = ImageIO.read(is);
                    ImageIO.write(img, "png", os);
                }
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Download every champion's centered splash art and write them to PNG files
        for (Champion champion : allChampions) {
            url = "https://raw.communitydragon.org/" + latestCDragonVersion[0] +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-splashes/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            request = new Request.Builder().url(url).build();
            LOGGER.info("Making GET request to " + url);
            try (Response response = client.newCall(request).execute();
                 ResponseBody body = response.body()) {
                assert body != null; // Body is non-null as it comes from Call#execute
                if (response.code() == 200) {
                    Path path = Paths.get(IMG_FOLDER_PATH + "splash/" + champion.getKey() + ".png");
                    Files.createDirectories(path.getParent());
                    try (InputStream is = body.byteStream();
                         OutputStream os = Files.newOutputStream(path)) {
                        BufferedImage img = ImageIO.read(is);
                        ImageIO.write(img, "png", os);
                    }
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download every champion tile and write them to PNG files
        for (Champion champion : allChampions) {
            String championKey = champion.getKey();
            url = "https://raw.communitydragon.org/" + latestCDragonVersion[0] +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-tiles/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            request = new Request.Builder().url(url).build();
            LOGGER.info("Making GET request to " + url);
            try (Response response = client.newCall(request).execute();
                 ResponseBody body = response.body()) {
                assert body != null; // Body is non-null as it comes from Call#execute
                if (response.code() == 200) {
                    Path path = Paths.get(IMG_FOLDER_PATH + "tile/" + championKey + ".png");
                    Files.createDirectories(path.getParent());
                    try (InputStream is = body.byteStream();
                         OutputStream os = Files.newOutputStream(path)) {
                        BufferedImage img = ImageIO.read(is);
                        ImageIO.write(img, "png", os);
                    }
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Shutdown our OkHttp client
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();

        // Update latest patch in config
        SettingsManager.getManager().updateValue("debug.cdragonPatch",
                ConfigValueFactory.fromAnyRef(latestCDragonVersion[1]));
    }

    /**
     * Finds the latest CDragon version available.
     *
     * @param client an {@link OkHttpClient} to be used for performing requests.
     * @return an array of {@link String} of size 2 with:<br>
     * - [0] The CDragon version (in CDragon format, for example, 10.2 for a 10.2.1 game version)<br>
     * - [1] The corresponding game version (in game version format, like 10.2.1)
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private static String[] getLatestCDragonVersion(OkHttpClient client) throws IOException {
        LOGGER.info("Checking if CDragon has the latest patch data...");

        int versionIndex = 0;
        String gameVersion, stripped;
        boolean found = false;
        do {
            gameVersion = Versions.get().get(versionIndex);
            stripped = gameVersion.substring(0, gameVersion.length() - 2); // Removes the '.1' at the end of the version
            String url = "https://raw.communitydragon.org/" + stripped + "/";
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 200) {
                    if (versionIndex == 0) {
                        LOGGER.info("CDragon is up-to-date. Current version: " + stripped);
                    } else {
                        LOGGER.info("Found! Latest CDragon version is " + stripped);
                    }
                    found = true;
                } else if (response.code() == 404) {
                    ++versionIndex;
                    LOGGER.warn("CDragon has not been updated yet... Trying with version " + Versions.get().get(versionIndex));
                }
            }
        } while (!found);
        return new String[]{stripped, gameVersion};
    }
}
