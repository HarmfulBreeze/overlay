package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.staticdata.*;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.stream.Collectors;

import static com.sun.jna.platform.win32.Tlhelp32.TH32CS_SNAPPROCESS;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Utils {
    private static final Object LOCK = new Object();

    private Utils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String readLockfile(Path lockfilePath) {
        String lockfileContents = null;
        try {
            Path tempLockfile = Files.createTempFile(null, null);
            Files.copy(lockfilePath, tempLockfile, REPLACE_EXISTING);
            lockfileContents = Files.lines(tempLockfile).collect(Collectors.joining());
            Files.delete(tempLockfile);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return lockfileContents;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    @NotNull
    public static Path getLeagueDirectory() {
        do {
            Kernel32 k32 = Kernel32.INSTANCE;
            WinNT.HANDLE snapshot = k32.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
            Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
            byte[] buf = new byte[1024];
            while (k32.Process32Next(snapshot, processEntry)) {
                if (Native.toString(processEntry.szExeFile).equals("LeagueClient.exe")) {
                    WinNT.HANDLE handle = k32.OpenProcess(
                            WinNT.PROCESS_QUERY_INFORMATION + WinNT.PROCESS_VM_READ,
                            false,
                            processEntry.th32ProcessID.intValue());
                    int result = psapi.INSTANCE.GetModuleFileNameExA(
                            handle,
                            Pointer.NULL,
                            buf,
                            1024);
                    k32.CloseHandle(handle);
                    if (result > 0) { // Path was retrieved successfully
                        k32.CloseHandle(snapshot);
                        String pathToClient = Native.toString(buf).substring(0, result);
                        return Paths.get(pathToClient).getParent();
                    }
                }
            }
            k32.CloseHandle(snapshot);
            synchronized (LOCK) {
                try {
                    LOCK.wait(2000);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
        } while (true);
    }

    /**
     * Convertit le contenu du lockfile passé en paramètre en tableau de chaînes de caractères.<br>
     * 0) Nom du processus du client<br>
     * 1) PID<br>
     * 2) Port WS<br>
     * 3) Mot de passe WS<br>
     * 4) Protocole (normalement HTTPS)
     *
     * @param lockfileContents Le contenu du fichier lockfile.
     * @return Un tableau de String contenant les différents éléments
     * @throws IllegalArgumentException Si la chaîne de caractères n'est pas du bon format
     */
    @NotNull
    public static String[] parseLockfile(@NotNull String lockfileContents) {
        String[] split = lockfileContents.split(":");
        if (split.length != 5 || !lockfileContents.matches("(.*:[0-9]*:[0-9]*:.*:.*)")) {
            throw new IllegalArgumentException("Le fichier lockfile n'est pas du bon format, a-t-il changé de format ?");
        }

        return split;
    }

    private interface psapi extends StdCallLibrary {
        psapi INSTANCE = Native.load("psapi", psapi.class);

        int GetModuleFileNameExA(WinNT.HANDLE hProcess, Pointer hModule, byte[] lpFilename, int nSize);
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

        // Shutdown our OkHttp client
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    public static void updateLatestPatchFile() {
        String latestVersion = Versions.get().get(0);
        SettingsManager.getManager().updateValue("debug.latestPatch",
                ConfigValueFactory.fromAnyRef(latestVersion));
        SettingsManager.getManager().writeConfig();
    }
}
