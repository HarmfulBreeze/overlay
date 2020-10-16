package com.jcs.overlay.utils;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
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


}
