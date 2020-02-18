package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.staticdata.*;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Utils {
    private Utils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    @NotNull
    public static String readLockFile() {
        String lockfileContents = null;

        Path leagueDirectory;
        try {
            leagueDirectory = getLeagueDirectory();
        } catch (FileNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }

        Path originalLockFile = leagueDirectory.resolve("lockfile");
        Path tempLockFile = leagueDirectory.resolve("lockfile.temp");
        try {
            Files.copy(originalLockFile, tempLockFile, REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        try (BufferedReader reader = Files.newBufferedReader(tempLockFile)) {
            lockfileContents = reader.readLine();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        try {
            Files.delete(tempLockFile);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (lockfileContents == null) {
            return "";
        } else {
            return lockfileContents;
        }
    }

    public static Path getLeagueDirectory() throws FileNotFoundException {
        RegPath p1 = new RegPath(WinReg.HKEY_CURRENT_USER, "Software\\Riot Games\\RADS");
        RegPath p2 = new RegPath(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\WOW6432Node\\Riot Games\\RADS");
        RegPath p3 = new RegPath(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Riot Games\\RADS");
        RegPath[] paths = {p1, p2, p3};
        for (RegPath path : paths) {
            if (Advapi32Util.registryValueExists(path.hkey, path.key, path.value)) {
                Path directory = Paths.get(Advapi32Util.registryGetStringValue(path.hkey, path.key, path.value))
                        .getParent(); // We call getParent else we get the RADS folder
                if (Files.exists(directory)) {
                    return directory;
                }
            }
        }

        Path directory = Paths.get("C:\\Riot Games\\League of Legends");
        if (Files.exists(directory)) {
            return directory;
        }

        throw new FileNotFoundException("Could not find the League of Legends directory!");
    }

    /**
     * Convertit le contenu du lockfile passé en paramètre en tableau de chaînes de caractères.<br>
     * 0) Nom du processus du client<br>
     * 1) PID<br>
     * 2) Port WS<br>
     * 3) Mot de passe WS<br>
     * 4) Protocole (normalement HTTPS)
     * @param lockfileContents Le contenu du fichier lockfile.
     * @return Un tableau de String contenant les différents éléments
     * @throws IllegalArgumentException Si la chaîne de caractères n'est pas du bon format
     */
    @NotNull
    public static String[] parseLockfile(@NotNull String lockfileContents)  {
        String[] split = lockfileContents.split(":");
        if (split.length != 5 || !lockfileContents.matches("(.*:[0-9]*:[0-9]*:.*:.*)")) {
            throw new IllegalArgumentException("Le fichier lockfile n'est pas du bon format, a-t-il changé de format ?");
        }

        return split;
    }

    @NotNull
    public static String fromPasswordToAuthToken(String password) {
        String start = "riot:" + password;
        return "Basic " + Base64.getEncoder().encodeToString(start.getBytes());
    }

    /**
     * Checks if a more recent patch was released since the last startup.
     *
     * @return {@code true} if a new patch was released, else {@code false}.
     */
    public static boolean checkForNewPatch() {
        String currentVersion = SettingsManager.getManager().getConfig().getString("debug.latestPatch");
        String latestVersion = Versions.get().get(0);
        return !currentVersion.equals(latestVersion);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored") // for mkdirs
    public static void updateWebappImages() {
        String imgFolderPath = System.getProperty("user.dir") + "/web/img/";

        // Download all summoner spells images and write them to pngs
        SummonerSpells spells = SummonerSpells.get();
        for (SummonerSpell spell : spells) {
            Path path = Paths.get(imgFolderPath + "icon/spell/" + spell.getId() + ".png");
            try {
                Files.createDirectories(path.getParent());
                ImageIO.write(spell.getImage().get(), "png", Files.newOutputStream(path));
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // Download all champion icons and write them to pngs
        Champions allChampions = Champions.get();
        for (Champion champion : allChampions) {
            Path path = Paths.get(imgFolderPath + "icon/champion/icon_" + champion.getKey() + ".png");
            try {
                Files.createDirectories(path.getParent());
                ImageIO.write(champion.getImage().get(), "png", Files.newOutputStream(path));
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
                Path path = Paths.get(imgFolderPath, "icon/champion/icon_None.png");
                BufferedImage img = ImageIO.read(is);
                ImageIO.write(img, "png", Files.newOutputStream(path));
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
                    Path path = Paths.get(imgFolderPath + "splash/" + championKey + ".png");
                    Files.createDirectories(path.getParent());
                    BufferedImage img = ImageIO.read(is);
                    ImageIO.write(img, "png", Files.newOutputStream(path));
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public static void updateLatestPatchFile() {
        String latestVersion = Versions.get().get(0);
        Config newConfig = SettingsManager.getManager().getConfig()
                .withValue("debug.latestPatch", ConfigValueFactory.fromAnyRef(latestVersion))
                .atPath("overlay");
        SettingsManager.getManager().updateConfig(newConfig);
        SettingsManager.getManager().writeConfig();
    }
}
