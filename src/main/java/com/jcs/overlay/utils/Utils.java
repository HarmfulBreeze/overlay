package com.jcs.overlay.utils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
