package com.jcs.overlay.utils;

import com.jcs.overlay.App;
import com.jcs.overlay.cef.CefManager;
import com.jcs.overlay.websocket.WSClient;
import com.jcs.overlay.websocket.WSServer;
import com.jcs.overlay.websocket.messages.J2W.SetupWebappMessage;
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
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.dir") + "/config.conf");
    private Config config;

    private SettingsManager() {
        // recreate non-existent config
        if (!Files.exists(CONFIG_PATH)) {
            LOGGER.warn("Config file could not be found, recreating one.");
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                InputStream applicationConfStream = classLoader.getResourceAsStream("application.conf");
                // application.conf should always exist!
                assert applicationConfStream != null;
                Files.copy(applicationConfStream, CONFIG_PATH);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        try {
            // parse config file and check its validity
            Config fileConfig = ConfigFactory.parseFile(CONFIG_PATH.toFile());
            verifyConfig(fileConfig);
            this.config = ConfigFactory.load(fileConfig).getConfig("overlay");
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
    }

    private static void verifyConfig(Config fileConfig) throws ConfigException.ValidationFailed {
        fileConfig.checkValid(ConfigFactory.defaultApplication(), "overlay");
        TimerStyle.checkTimerStyle(fileConfig);
        SummonerSpellsDisplayStrategy.checkStrategy(fileConfig);
    }

    public static SettingsManager getManager() {
        return Holder.instance;
    }

    public Config getConfig() {
        return this.config;
    }

    public synchronized void updateValue(String path, ConfigValue value) {
        this.config = this.config.withValue(path, value);
        this.writeConfig();
    }

    private void writeConfig() {
        try {
            Files.write(CONFIG_PATH, this.config.atPath("overlay")
                    .root()
                    .render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(true))
                    .getBytes()
            );
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    protected synchronized void refreshConfig() {
        // Load updated config
        Config prevConf = this.config;
        try {
            Config fileConfig = ConfigFactory.parseFile(CONFIG_PATH.toFile());
            verifyConfig(fileConfig);
            this.config = ConfigFactory.load(fileConfig).getConfig("overlay");
        } catch (ConfigException.ValidationFailed | ConfigException.Parse e) {
            String errorMessage = "Config file is invalid. Overlay configuration has not been modified.";
            LOGGER.warn(errorMessage);
            JOptionPane.showMessageDialog(null, errorMessage, "Warning!",
                    JOptionPane.WARNING_MESSAGE);
        }

        // Update config where needed
        LOGGER.info("Updating configuration...");
        // Window size
        if (!App.noGUI) {
            int windowWidth = this.config.getInt("window.width");
            int windowHeight = this.config.getInt("window.height");
            CefManager.getInstance().getMainFrame().resizeWindow(windowWidth, windowHeight);
        }
        // Webapp config
        WSClient wsClient = App.getWsClient();
        if (wsClient != null && wsClient.championSelectHasStarted()) {
            SetupWebappMessage msg = new SetupWebappMessage();
            WSServer.getInstance().broadcastWebappMessage(SetupWebappMessage.class, msg);
        }

        // Check non-updatable settings
        if (this.config.getBoolean("debug.nogui") != prevConf.getBoolean("debug.nogui")
                || this.config.getBoolean("cef.disableGpuCompositing") != prevConf.getBoolean("cef.disableGpuCompositing")
                || !this.config.getString("debug.cdragonPatch").equals(prevConf.getString("debug.cdragonPatch"))
                || !this.config.getString("debug.ddragonPatch").equals(prevConf.getString("debug.ddragonPatch"))) {
            LOGGER.info("Configuration updated. Some settings need Overlay to be restarted in order to take effect.");
        } else {
            LOGGER.info("Configuration updated.");
        }
    }

    private static class Holder {
        private static final SettingsManager instance = new SettingsManager();
    }
}
