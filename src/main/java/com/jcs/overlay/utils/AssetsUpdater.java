package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.searchable.SearchableList;
import com.merakianalytics.orianna.types.core.staticdata.*;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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
import java.util.Locale;

public class AssetsUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsUpdater.class);
    private static final String IMG_FOLDER_PATH = System.getProperty("user.dir") + "/web/img/";
    private static final DateTimeFormatter RFC1123_FORMATTER = DateTimeFormat
            .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withZoneUTC()
            .withLocale(Locale.US);

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
        LOGGER.info("Checking for a CDragon assets update...");
        String latestGamePatch = Patches.get().get(0).getName();
        LOGGER.debug("Latest game patch is {}.", latestGamePatch);
        String localCDragonPatch = SettingsManager.getManager().getConfig().getString("debug.cdragonPatch");
        if (!latestGamePatch.equals(localCDragonPatch)) {
            LOGGER.info("Local CDragon assets patch does not match latest game patch. Checking for updated assets...");
            OkHttpClient client = new OkHttpClient();
            String latestCDragonVersion;
            try {
                LOGGER.debug("Retrieving latest CDragon version...");
                latestCDragonVersion = getLatestCDragonVersion(client);
                LOGGER.debug("Latest CDragon version is {}.", latestCDragonVersion);
            } catch (IOException | IndexOutOfBoundsException e) {
                LOGGER.error("Could not retrieve the latest CDragon version.", e);
                return;
            }
            if (latestGamePatch.equals(latestCDragonVersion)) {
                LOGGER.info("Updated assets are available! Updating.");
                performCDragonUpdate(client, latestCDragonVersion, localCDragonPatch);
                LOGGER.info("CDragon assets update has been successfully completed.");
            } else {
                LOGGER.warn("Updated assets for patch {} are not available yet. " +
                        "Overlay will check again on next startup.", latestGamePatch);
            }
            // Shutdown our OkHttp client
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        } else {
            LOGGER.info("CDragon assets are up-to-date.");
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

    private static void performDDragonUpdate() {
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

        // Update latest patch in config
        String latestVersion = Versions.get().get(0);
        SettingsManager.getManager().updateValue("debug.ddragonPatch",
                ConfigValueFactory.fromAnyRef(latestVersion));
    }

    private static void performCDragonUpdate(OkHttpClient client, String latestCDragonPatch, String localCDragonPatch) {
        Champions allChampions = Champions.get();
        SearchableList<Patch> patchList = Patches.named(localCDragonPatch).get();
        DateTime localPatchReleaseTime;
        // if (patchList.size() > 0) {
        if (patchList.size() > 0 && patchList.get(0).getStartTime() != null) { // hack for not-empty patchList with invalid name
            localPatchReleaseTime = patchList.get(0).getStartTime().withZone(DateTimeZone.UTC);
        } else {
            localPatchReleaseTime = null;
        }

        // Download the generic champion icon and write it to a PNG file
        String url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                "/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/-1.png";
        Request.Builder builder = new Request.Builder().url(url);
        Request headRequest = builder.head().build();
        LOGGER.info("Making HEAD request to " + url);
        try (Response res = client.newCall(headRequest).execute()) {
            DateTime lastModifiedTime = RFC1123_FORMATTER.parseDateTime(res.header("Last-Modified"));
            if (localPatchReleaseTime == null || lastModifiedTime.isAfter(localPatchReleaseTime)) {
                Request getRequest = builder.get().build();
                LOGGER.info("Making GET request to " + url);
                try (Response response = client.newCall(getRequest).execute()) {
                    ResponseBody body = response.body();
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
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Download every champion's centered splash art and write them to PNG files
        for (Champion champion : allChampions) {
            url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-splashes/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            builder = new Request.Builder().url(url);
            headRequest = builder.head().build();
            LOGGER.info("Making HEAD request to " + url);
            try (Response res = client.newCall(headRequest).execute()) {
                DateTime lastModifiedTime = RFC1123_FORMATTER.parseDateTime(res.header("Last-Modified"));
                if (localPatchReleaseTime == null || lastModifiedTime.isAfter(localPatchReleaseTime)) {
                    Request getRequest = builder.get().build();
                    LOGGER.info("Making GET request to " + url);
                    try (Response response = client.newCall(getRequest).execute()) {
                        ResponseBody body = response.body();
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
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download every champion tile and write them to PNG files
        for (Champion champion : allChampions) {
            String championKey = champion.getKey();
            url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-tiles/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            builder = new Request.Builder().url(url);
            headRequest = builder.head().build();
            LOGGER.info("Making HEAD request to " + url);
            try (Response res = client.newCall(headRequest).execute()) {
                DateTime lastModifiedTime = RFC1123_FORMATTER.parseDateTime(res.header("Last-Modified"));
                if (localPatchReleaseTime == null || lastModifiedTime.isAfter(localPatchReleaseTime)) {
                    Request getRequest = builder.get().build();
                    LOGGER.info("Making GET request to " + url);
                    try (Response response = client.newCall(getRequest).execute()) {
                        ResponseBody body = response.body();
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
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Update latest patch in config
        SettingsManager.getManager().updateValue("debug.cdragonPatch",
                ConfigValueFactory.fromAnyRef(latestCDragonPatch));
    }

    /**
     * Finds the latest CDragon version available.
     *
     * @param client an {@link OkHttpClient} to be used for performing requests.
     * @return a {@link String} of the latest patch of which CDragon has assets.
     * @throws IOException               if the request could not be executed due to cancellation, a connectivity problem or timeout,
     *                                   or if the HTTP response code was unexpected.
     * @throws IndexOutOfBoundsException if all patches were tried and none were valid as a CDragon patch.
     */
    @NotNull
    private static String getLatestCDragonVersion(OkHttpClient client) throws IOException, IndexOutOfBoundsException {
        int patchIndex = 0;
        String gamePatch;
        do {
            gamePatch = Patches.get().get(patchIndex).getName();
            String url = "https://raw.communitydragon.org/" + gamePatch + "/";
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 200) {
                    return gamePatch;
                } else if (response.code() == 404) {
                    ++patchIndex;
                } else {
                    throw new IOException("Unexpected response code: " + response.code());
                }
            }
        } while (true);
    }
}
