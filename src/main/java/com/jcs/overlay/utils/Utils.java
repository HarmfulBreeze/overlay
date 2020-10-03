package com.jcs.overlay.utils;

import com.merakianalytics.orianna.types.core.staticdata.*;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.typesafe.config.ConfigValueFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.stream.Collectors;

import static com.sun.jna.platform.win32.Tlhelp32.TH32CS_SNAPPROCESS;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Utils {
    private static final Object LOCK = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    private static final String IMG_FOLDER_PATH = System.getProperty("user.dir") + "/web/img/";

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

    /**
     * Gets the Windows League of Legends directory from the process list.
     *
     * @return A {@code Path} object holding an absolute path to the League directory,
     * or {@code null} if it could not be found.
     **/
    @Nullable
    public static Path getLeagueDirectory() {
        Kernel32 k32 = Kernel32.INSTANCE;
        WinNT.HANDLE snapshot = k32.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        char[] charbuf = new char[WinDef.MAX_PATH];
        while (k32.Process32Next(snapshot, processEntry)) {
            if (Native.toString(processEntry.szExeFile).equals("LeagueClient.exe")) {
                WinNT.HANDLE handle = k32.OpenProcess(
                        WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                        false,
                        processEntry.th32ProcessID.intValue());
                IntByReference charbufSize = new IntByReference(WinDef.MAX_PATH);
                boolean result = k32.QueryFullProcessImageName(handle, 0, charbuf, charbufSize);
                k32.CloseHandle(handle);
                if (result) { // Path was retrieved successfully
                    k32.CloseHandle(snapshot);
                    String pathToClient = String.valueOf(charbuf);
                    pathToClient = pathToClient.substring(0, charbufSize.getValue()); // remove the trailing NUL chars
                    return Paths.get(pathToClient).getParent();
                }
            }
        }
        k32.CloseHandle(snapshot);
        return null;
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

    /**
     * @return an {@link SSLContext} permissive enough to allow the League Client self-signed certificate.
     * @throws NoSuchAlgorithmException if no {@link Provider} was found for handling TLS.
     * @throws KeyManagementException   if {@link SSLContext} initialization failed.
     */
    @NotNull
    public static SSLContext getSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{}; // Return an empty accepted issuers array
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing, trust client SSL auth
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing, trust server SSL auth
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, null);
        return sslContext;
    }

    @NotNull
    public static String fromPasswordToAuthToken(String password) {
        String start = "riot:" + password;
        return "Basic " + Base64.getEncoder().encodeToString(start.getBytes());
    }

    /**
     * Checks if a new CDragon Raw version was released since the last startup.
     *
     * @return {@code true} if a new patch was released, else {@code false}.
     */
    public static boolean checkForNewCDragonPatch() { // TODO: move check logic in this function
        String currentVersion = SettingsManager.getManager().getConfig().getString("debug.cdragonPatch");
        String latestVersion = Versions.get().get(0);
        return !currentVersion.equals(latestVersion);
    }

    /**
     * Checks if a new DDragon version was released since the last startup.
     *
     * @return {@code true} if a new version was released, else {@code false}.
     */
    public static boolean checkForNewDDragonPatch() {
        String currentVersion = SettingsManager.getManager().getConfig().getString("debug.ddragonPatch");
        String latestVersion = Versions.get().get(0);
        return !currentVersion.equals(latestVersion);
    }


    public static void performDDragonUpdate() {
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

    public static void performCDragonUpdate() {
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
     * @param client An {@link OkHttpClient} to be used for performing requests.
     * @return An array of {@link String} of size 2 with:<br>
     * - [0] The CDragon version<br>
     * - [1] The corresponding game version
     * @throws IOException Thrown if a connection error occurs.
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
