package com.jcs.overlay;

import com.jcs.overlay.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class LockfileMonitor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockfileMonitor.class);
    private WatchService watchService;

    public synchronized void setLeagueStarted(boolean leagueStarted) {
        this.leagueStarted = leagueStarted;
    }

    private boolean leagueStarted;

    @Override
    public void run() {
        Path leagueFolder;
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            leagueFolder = Utils.getLeagueDirectory();
            leagueFolder.register(this.watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return;
        }

        Path lockfilePath = leagueFolder.resolve("lockfile");

        LOGGER.info("Welcome! Awaiting connection to the game...");

        // On vérifie si le jeu est déjà démarré, si oui, se connecter directement
        if (Files.exists(lockfilePath)) {
            String lockfileContent = Utils.readLockFile();
            if (!lockfileContent.isEmpty()) {
                this.leagueStarted = true;
                App.getApp().onLeagueStart(lockfileContent);
            } else {
                this.leagueStarted = false;
            }
        } else {
            this.leagueStarted = false;
        }

        WatchKey key;
        String lockfileContent;
        try {
            while ((key = this.watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    // Lockfile modifié -> League mal fermé, on redémarre, ou League qui s'ouvre pour la 1ere fois
                    if (event.kind() == ENTRY_MODIFY && ((Path) event.context()).endsWith("lockfile")) {
                        if (this.leagueStarted) {
                            continue;
                        }
                        App.getApp().onLeagueStop();
                        lockfileContent = Utils.readLockFile();
                        App.getApp().onLeagueStart(lockfileContent);
                        this.setLeagueStarted(true);
                    }

                    // Si le lockfile est supprimé, on en déduit que le client est fermé
                    else if (event.kind() == ENTRY_DELETE && ((Path) event.context()).endsWith("lockfile")) {
                        this.setLeagueStarted(false);
                        App.getApp().onLeagueStop();
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException e) {
            // Will be thrown when LockfileMonitor.stop() is called.
        }
    }

    void stop() {
        try {
            this.watchService.close();
        } catch (IOException e) {
            LOGGER.error("Exception caught: ", e);
        }
    }
}
