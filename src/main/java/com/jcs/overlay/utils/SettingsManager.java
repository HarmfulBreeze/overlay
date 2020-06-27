package com.jcs.overlay.utils;

import com.jcs.overlay.websocket.messages.J2W.enums.SummonerSpellsDisplayStrategy;
import com.jcs.overlay.websocket.messages.J2W.enums.TimerStyle;
import com.typesafe.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SettingsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsManager.class);
    private final Path configPath = Paths.get(System.getProperty("user.dir") + "/config.conf");
    private Config config;

    private SettingsManager() {
        Config effectiveConfig = null;

        // recreate non-existent config
        if (!Files.exists(this.configPath)) {
            LOGGER.warn("Config file could not be found, recreating one.");
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                InputStream applicationConfStream = classLoader.getResourceAsStream("application.conf");
                // application.conf should always exist!
                assert applicationConfStream != null;
                Files.copy(applicationConfStream, this.configPath);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        try {
            // parse config file and check its validity
            Config fileConfig = ConfigFactory.parseFile(this.configPath.toFile());
            fileConfig.checkValid(ConfigFactory.defaultApplication(), "overlay");
            TimerStyle.checkTimerStyle(fileConfig);
            SummonerSpellsDisplayStrategy.checkStrategy(fileConfig);
            effectiveConfig = ConfigFactory.load(fileConfig);
        } catch (ConfigException.ValidationFailed | ConfigException.Parse e) {
            String errorMessage = "Config file is invalid. " +
                    "If you wish to reset the file, simply delete it and it will be recreated on next startup. " +
                    "Overlay will now close.\n" +
                    "Exception message:\n" +
                    e.getMessage();
            LOGGER.error(errorMessage, e);
            JOptionPane.showMessageDialog(null, errorMessage, "Error!", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (ConfigException e) {
            LOGGER.error(e.getMessage(), e);
            System.exit(1);
        }
        // at this point, effectiveConfig should no longer be null
        assert effectiveConfig != null;
        this.config = effectiveConfig.getConfig("overlay");
    }

    public static SettingsManager getManager() {
        return Holder.instance;
    }

    public Config getConfig() {
        return this.config;
    }

    public void updateValue(String path, ConfigValue value) {
        this.config = this.config.withValue(path, value);
    }

    public void writeConfig() {
        try {
            Files.write(this.configPath, this.config.atPath("overlay")
                    .root()
                    .render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(true))
                    .getBytes()
            );
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static class Holder {
        private static final SettingsManager instance = new SettingsManager();
    }
}
