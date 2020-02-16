package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.staticdata.*;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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

        File leagueDirectory;
        try {
            leagueDirectory = getLeagueDirectory();
        } catch (FileNotFoundException e) {
            LOGGER.error("Exception caught: ", e);
            return "";
        }

        Path originalLockFile = leagueDirectory.toPath().resolve("lockfile");
        Path lockfile = leagueDirectory.toPath().resolve("lockfile.temp");
        try {
            Files.copy(originalLockFile, lockfile, REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Exception caught: ", e);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(lockfile.toFile()))) {
            lockfileContents = reader.readLine();
        } catch (Exception e) {
            LOGGER.error("Exception caught: ", e);
        }

        try {
            Files.delete(lockfile);
        } catch (IOException e) {
            LOGGER.error("Exception caught: ", e);
        }

        if (lockfileContents == null) {
            return "";
        } else {
            return lockfileContents;
        }
    }

    @NotNull
    public static File getLeagueDirectory() throws FileNotFoundException {
        RegPath p1 = new RegPath(WinReg.HKEY_CURRENT_USER, "Software\\Riot Games\\RADS");
        RegPath p2 = new RegPath(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\WOW6432Node\\Riot Games\\RADS");
        RegPath p3 = new RegPath(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Riot Games\\RADS");
        RegPath[] paths = {p1, p2, p3};
        for (RegPath path : paths) {
            if (Advapi32Util.registryValueExists(path.hkey, path.key, path.value)) {
                File directory = new File(Advapi32Util.registryGetStringValue(path.hkey, path.key, path.value)).getParentFile();
                if (directory.exists()) {
                    return directory;
                }
            }
        }

        File directory = new File("C:\\Riot Games\\League of Legends");
        if (directory.exists()) {
            return directory;
        }

        throw new FileNotFoundException("Impossible de trouver le dossier LoL !");
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

    public static boolean checkForNewPatch() {
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

    @SuppressWarnings("ResultOfMethodCallIgnored") // for mkdirs
    public static void updateWebappImages() {
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

    public static void updateLatestPatchFile() {
        File latestVersionFile = new File(System.getProperty("user.dir") + "/latestPatch.txt");
        String latestVersion = Versions.get().get(0);
        try (FileWriter writer = new FileWriter(latestVersionFile)) {
            writer.write(latestVersion);
        } catch (IOException e) {
            LOGGER.info(e.getMessage(), e);
        }
    }
}
