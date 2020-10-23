package com.jcs.overlay.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class SettingsWatcher implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsWatcher.class);
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.dir") + "/config.conf");
    private boolean shouldStop = false;
    private WatchService watchService;

    // Private constructor
    private SettingsWatcher() {
    }

    // Instance getter
    public static SettingsWatcher getInstance() {
        return SettingsWatcherHolder.INSTANCE;
    }

    // Singleton holder
    private static class SettingsWatcherHolder {
        private static final SettingsWatcher INSTANCE = new SettingsWatcher();
    }

    @Override
    public void run() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            CONFIG_PATH.getParent().register(this.watchService, ENTRY_MODIFY, ENTRY_DELETE);

            while (!this.shouldStop) {
                WatchKey key = this.watchService.take();

                //noinspection BusyWait
                Thread.sleep(50); // hack to avoid multiple triggers

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context().toString().equals("config.conf")) {
                        if (event.kind() == ENTRY_MODIFY) {
                            LOGGER.warn("Config file has been modified!");
                            SettingsManager.getManager().refreshConfig();
                        } else {
                            LOGGER.warn("Config file was deleted. Overlay configuration remains unchanged.");
                        }
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
