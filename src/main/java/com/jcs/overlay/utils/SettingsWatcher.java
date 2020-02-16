package com.jcs.overlay.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class SettingsWatcher implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsWatcher.class);
    private final Path configPath = Paths.get(System.getProperty("user.dir") + "/config.conf");
    private boolean shouldStop = false;
    private WatchService watchService;

    @Override
    public void run() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.configPath.getParent().register(this.watchService, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return;
        }

        try {
            while (!this.shouldStop) {
                WatchKey key = this.watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (((Path) event.context()).endsWith("config.conf")) {
                        LOGGER.warn("Config file has been altered. Restart the app to apply changes.");
                        this.shouldStop = true;
                    }
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException e) {
            // Do nothing, thrown when stopping the SettingsWatcher
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        this.shouldStop = true;
        try {
            this.watchService.close();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
