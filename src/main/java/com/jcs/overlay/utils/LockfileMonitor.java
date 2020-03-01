package com.jcs.overlay.utils;

import com.jcs.overlay.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class LockfileMonitor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockfileMonitor.class);
    private WatchService watchService;
    private boolean leagueStarted;

    public synchronized void setLeagueStarted(boolean leagueStarted) {
        this.leagueStarted = leagueStarted;
    }

    @Override
    public void run() {
        LOGGER.info("Welcome! Awaiting connection to the game...");

        Path leagueFolder = Utils.getLeagueDirectory();
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            leagueFolder.register(this.watchService, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return;
        }

        Path lockfilePath = leagueFolder.resolve("lockfile");

        // On vérifie si le jeu est déjà démarré, si oui, se connecter directement
        if (Files.exists(lockfilePath)) {
            String lockfileContent = Utils.readLockfile(lockfilePath);
            if (lockfileContent != null && !lockfileContent.isEmpty()) {
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
                        lockfileContent = Utils.readLockfile(lockfilePath);
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

    public void stop() {
        try {
            this.watchService.close();
        } catch (IOException e) {
            LOGGER.error("Exception caught: ", e);
        }
    }
}
