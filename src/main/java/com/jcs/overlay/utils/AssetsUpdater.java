package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.common.OriannaException;
import com.merakianalytics.orianna.types.core.staticdata.*;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public class AssetsUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsUpdater.class);
    private static final String IMG_FOLDER_PATH = System.getProperty("user.dir") + "/web/img/";
    private static final DateTimeFormatter RFC1123_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));
    private static boolean cDragonSuccess = true;

    private AssetsUpdater() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Updates DDragon assets in {@link #IMG_FOLDER_PATH}.
     */
    public static void updateDDragonAssets() {
        LOGGER.info("Checking for updated DDragon assets...");
        if (checkForNewDDragonPatch()) {
            LOGGER.info("New DDragon assets were released. Updating...");
            performDDragonUpdate();
        } else {
            LOGGER.info("No updated DDragon assets available.");
        }
    }

    /**
     * Updates CDragon assets in {@link #IMG_FOLDER_PATH}.
     */
    public static void updateCDragonAssets() {
        LOGGER.info("Checking for a CDragon assets update...");
        String latestGamePatch = Patches.get().get(0).getName();
        LOGGER.debug("Latest game patch is {}.", latestGamePatch);
        String localCDragonPatch = SettingsManager.getConfig().getString("debug.cdragonPatch");
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
        String localVersion = SettingsManager.getConfig().getString("debug.ddragonPatch");
        String latestVersion = Versions.get().get(0);
        return !localVersion.equals(latestVersion);
    }

    private static void performDDragonUpdate() {
        boolean success = true;

        // Download all summoner spells images and write them to PNG files
        for (SummonerSpell spell : SummonerSpells.get()) {
            BufferedImage spellImg;
            try {
                spellImg = spell.getImage().get();
                if (spellImg != null) {
                    Path path = Paths.get(IMG_FOLDER_PATH + "icon/spell/" + spell.getId() + ".png");
                    writeImageToPngFile(spellImg, path);
                } else {
                    throw new NullPointerException("spellImg is null!");
                }
            } catch (OriannaException | NullPointerException e) {
                LOGGER.error("Could not get the image of summoner spell " + spell.getName(), e);
                success = false;
            } catch (IOException e) {
                LOGGER.error("An error occurred while writing to the PNG file.", e);
                success = false;
            }
        }

        if (success) {
            LOGGER.debug("Updating the DDragon patch in config...");
            String latestVersion = Versions.get().get(0);
            SettingsManager.updateValue("debug.ddragonPatch",
                    ConfigValueFactory.fromAnyRef(latestVersion));
            LOGGER.info("DDragon assets update completed.");
        } else {
            LOGGER.info("DDragon assets update did not fully succeed. We will retry updating on the next startup.");
        }
    }

    private static void performCDragonUpdate(OkHttpClient client, String latestCDragonPatch, String localCDragonPatch) {
        Champions allChampions = Champions.get();
        Patch gamePatch = Patch.named(localCDragonPatch).get();
        ZonedDateTime localPatchReleaseTime;

        if (gamePatch.exists()) {
            DateTime jodaUTCStartTime = gamePatch.getStartTime().withZone(DateTimeZone.UTC);
            Instant instant = Instant.ofEpochMilli(jodaUTCStartTime.getMillis());
            ZoneId zoneId = ZoneId.of(jodaUTCStartTime.getZone().getID(), ZoneId.SHORT_IDS);
            localPatchReleaseTime = ZonedDateTime.ofInstant(instant, zoneId);
        } else {
            localPatchReleaseTime = null; // localCDragonPatch is not a valid patch
        }

        // Download the generic champion icon and write it to a PNG file
        String url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                "/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/-1.png";
        Path path = Paths.get(IMG_FOLDER_PATH, "icon/champion/icon_None.png");
        downloadPngIfModified(client, localPatchReleaseTime, url, path);

        // Download every champion's centered splash art and write them to PNG files
        for (Champion champion : allChampions) {
            url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-splashes/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            path = Paths.get(IMG_FOLDER_PATH + "splash/" + champion.getKey() + ".png");
            downloadPngIfModified(client, localPatchReleaseTime, url, path);
        }

        // Download every champion tile and write them to PNG files
        for (Champion champion : allChampions) {
            url = "https://raw.communitydragon.org/" + latestCDragonPatch +
                    "/plugins/rcp-be-lol-game-data/global/default/v1/champion-tiles/" +
                    champion.getId() + "/" + champion.getId() + "000.jpg";
            path = Paths.get(IMG_FOLDER_PATH + "tile/" + champion.getKey() + ".png");
            downloadPngIfModified(client, localPatchReleaseTime, url, path);
        }

        // Create an idle callback that will wake up the current thread when all PNGs requests have been handled
        Object lock = new Object();
        client.dispatcher().setIdleCallback(() -> {
            synchronized (lock) {
                lock.notify();
            }
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        if (cDragonSuccess) {
            LOGGER.debug("Updating the CDragon patch in config...");
            SettingsManager.updateValue("debug.cdragonPatch",
                    ConfigValueFactory.fromAnyRef(latestCDragonPatch));
            LOGGER.info("CDragon assets update completed.");
        } else {
            LOGGER.info("CDragon assets update did not fully succeed. We will retry updating on the next startup.");
        }
    }

    /**
     * Checks if the file located at {@code url} has been modified since a provided {@link ZonedDateTime} and downloads
     * it to the provided {@link Path} using an {@link OkHttpClient}.
     *
     * @param client the {@link OkHttpClient} to be used for sending requests.
     * @param since  a {@link ZonedDateTime} to be checked against the last modified time of the file. If null, the file
     *               will be downloaded without checking the last modification time.
     * @param url    the URL to the file.
     * @param path   a {@link Path} to where the PNG file should be saved.
     */
    private static void downloadPngIfModified(OkHttpClient client,
                                              @Nullable ZonedDateTime since,
                                              String url,
                                              Path path) {
        if (since != null) {
            Request req = new Request.Builder().url(url).head().build();
            LOGGER.debug("Queuing HEAD request to " + url);
            client.newCall(req).enqueue(new HeadCallback(client, since, path));
        } else {
            Request getReq = new Request.Builder().url(url).build();
            LOGGER.debug("Queuing GET request to " + url);
            client.newCall(getReq).enqueue(new GetCallback(path));
        }
    }

    /**
     * Writes a provided {@link BufferedImage} into the file at the provided {@link Path}, creating parent directories
     * if they do not exist.
     *
     * @param img  The {@link BufferedImage}, cannot be null.
     * @param path The {@link Path} at which the file will be written to.
     * @throws IOException if parent directories could not all be created, if an {@link OutputStream} could not be
     *                     opened at {@code path}, or if an error occurs while writing into the file.
     */
    private static void writeImageToPngFile(@NotNull BufferedImage img, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        OutputStream os = Files.newOutputStream(path);
        ImageIO.write(img, "png", os);
    }

    /**
     * Writes the {@link InputStream} of an image into the file at the provided {@link Path}, creating parent
     * directories if they do not exist.
     *
     * @param is   The {@link InputStream}. It will not be closed by this method.
     * @param path The {@link Path} at which the file will be written to.
     * @throws IOException if parent directories could not all be created, if an {@link OutputStream} could not be
     *                     opened at {@code path}, or if an error occurs while reading from {@code iis}/writing
     *                     into the file.
     */
    private static void writeImageToPngFile(@NotNull InputStream is, Path path) throws IOException {
        LOGGER.debug("Writing image to {}", path);
        Files.createDirectories(path.getParent());
        OutputStream os = Files.newOutputStream(path);
        BufferedImage img = ImageIO.read(is);
        ImageIO.write(img, "png", os);
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

    private static class HeadCallback implements Callback {
        private final ZonedDateTime since;
        private final Path pngPath;
        private final OkHttpClient client;

        public HeadCallback(OkHttpClient client, @Nullable ZonedDateTime since, Path pngPath) {
            this.client = client;
            this.since = since;
            this.pngPath = pngPath;
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            cDragonSuccess = false;
            LOGGER.error("Could not get headers for request " + call.request(), e);
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response res) throws IOException {
            if (res.code() != 200) {
                throw new IOException("Unexpected HTTP response code " + res.code() + " for request " + call.request());
            }
            String header = res.header("Last-Modified");
            if (header == null) {
                LOGGER.warn("No Last-Modified header was found, getting anyway...");
            } else {
                TemporalAccessor parse = RFC1123_FORMATTER.parse(header);
                ZonedDateTime lastModifiedTime = ZonedDateTime.from(parse);
                if (!lastModifiedTime.isAfter(this.since)) {
                    return;
                }
            }
            Request getReq = call.request().newBuilder().get().build();
            LOGGER.debug("Queuing GET request to " + getReq.url());
            this.client.newCall(getReq).enqueue(new GetCallback(this.pngPath));
        }
    }

    private static class GetCallback implements Callback {
        private final Path pngPath;

        public GetCallback(Path pngPath) {
            this.pngPath = pngPath;
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            cDragonSuccess = false;
            LOGGER.error("An error occurred with request " + call.request(), e);
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response res) throws IOException {
            if (res.code() != 200) {
                throw new IOException("Unexpected HTTP response code: " + res.code());
            }
            ResponseBody body = res.body();
            if (body == null) {
                throw new IOException("Response body for request " + call.request() + " is null!");
            }
            try {
                writeImageToPngFile(body.byteStream(), this.pngPath);
            } catch (IOException e) {
                throw new IOException("Could not write image to PNG file!", e);
            }
        }
    }
}
