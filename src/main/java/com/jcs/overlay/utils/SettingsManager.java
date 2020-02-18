package com.jcs.overlay.utils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SettingsManager {
    private final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    private Config config;
    private final Path configPath = Paths.get(System.getProperty("user.dir") + "/config.conf");

    private SettingsManager() {
        Config effectiveConfig;

        // recreate non-existent config
        if (!Files.exists(this.configPath)) {
            this.logger.warn("Config file could not be found, recreating one.");
            try {
                Files.createFile(this.configPath);
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Path path = Paths.get(classLoader.getResource("application.conf").toURI());
                Files.write(this.configPath, Files.readAllBytes(path));
            } catch (Exception e) {
                this.logger.error(e.getMessage(), e);
            }
        }

        // parse config file and check its validity
        Config fileConfig = ConfigFactory.parseFile(this.configPath.toFile());
        try {
            fileConfig.checkValid(ConfigFactory.defaultApplication(), "overlay");
            effectiveConfig = ConfigFactory.load(fileConfig);
        } catch (ConfigException.ValidationFailed e) {
            this.logger.error("Config file is invalid, using default configuration. If you wish to recreate the file," +
                    " simply delete it and it will be recreated on next startup.");
            effectiveConfig = ConfigFactory.load();
        } catch (ConfigException e) {
            this.logger.error(e.getMessage(), e);
            effectiveConfig = ConfigFactory.load();
        }
        this.config = effectiveConfig;
    }

    private static class Holder {
        private static final SettingsManager instance = new SettingsManager();
    }

    public static SettingsManager getManager() {
        return Holder.instance;
    }

    public Config getConfig() {
        return this.config.getConfig("overlay");
    }

    public void updateConfig(Config newConfig) {
        this.config = newConfig;
    }

    public void writeConfig() {
        try {
            Files.write(this.configPath, this.config.root()
                    .render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(false))
                    .getBytes()
            );
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
        }
    }
}
