package com.jcs.overlay.utils;

import com.jcs.overlay.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

// TODO: make the "league started" mechanism rely more on the process list
public class LockfileMonitor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockfileMonitor.class);
    private WatchService watchService;
    private boolean leagueStarted;
    private boolean shouldStop = false;

    // Private constructor
    private LockfileMonitor() {
    }

    // Instance getter
    public static LockfileMonitor getInstance() {
        return LockfileMonitorHolder.INSTANCE;
    }

    // Singleton holder
    private static class LockfileMonitorHolder {
        private static final LockfileMonitor INSTANCE = new LockfileMonitor();
    }

    public synchronized void setLeagueStarted(boolean leagueStarted) {
        this.leagueStarted = leagueStarted;
    }

    @Override
    public void run() {
        LOGGER.info("Welcome! Awaiting connection to the game...");

        Path leagueFolder;
        do {
            leagueFolder = Utils.getLeagueDirectory();
            if (this.shouldStop) {
                return;
            } else if (leagueFolder == null) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        } while (leagueFolder == null);

        Path lockfilePath = leagueFolder.resolve("lockfile");
        // We check whether the client is already open, if so, connect directly
        if (Files.exists(lockfilePath)) {
            String lockfileContent = Utils.readLockfile(lockfilePath);
            if (lockfileContent != null && !lockfileContent.isEmpty()) {
                this.leagueStarted = true;
                App.onLeagueStart(lockfileContent);
            } else {
                this.leagueStarted = false;
            }
        } else {
            this.leagueStarted = false;
        }

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            leagueFolder.register(this.watchService, ENTRY_MODIFY, ENTRY_DELETE);

            while (!this.shouldStop) {
                WatchKey key = this.watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context().toString().equals("lockfile")) {
                        if (event.kind() == ENTRY_MODIFY) { // lockfile modified -> first startup, or closed abruptly
                            if (this.leagueStarted) {
                                continue;
                            }
                            App.onLeagueStop();
                            String lockfileContent = Utils.readLockfile(lockfilePath);
                            App.onLeagueStart(lockfileContent);
                            this.setLeagueStarted(true);
                        } else if (event.kind() == ENTRY_DELETE) { // lockfile deleted -> client closed
                            this.setLeagueStarted(false);
                            App.onLeagueStop();
                        }
                    }
                }

                key.reset();
            }
        } catch (ClosedWatchServiceException e) {
            // Will be thrown when LockfileMonitor.stop() is called.
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        this.shouldStop = true;
        if (this.watchService != null) {
            try {
                this.watchService.close();
            } catch (IOException e) {
                LOGGER.error("Exception caught: ", e);
            }
        }
    }
}
