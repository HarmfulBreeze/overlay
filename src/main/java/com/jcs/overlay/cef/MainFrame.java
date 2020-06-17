package com.jcs.overlay.cef;

import com.jcs.overlay.App;
import com.jcs.overlay.cef.handlers.KeyboardHandler;
import com.jcs.overlay.utils.SettingsManager;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = -5570653778104813836L;
    private static final Logger LOGGER = LoggerFactory.getLogger(MainFrame.class);
    private CefApp cefApp_;
    private final CefClient client_;
    private final CefBrowser browser_;
    private final Component browserUI_;
    private boolean browserFocus_ = true;

    public MainFrame(String startURL, boolean useOSR, boolean isTransparent) {
        List<String> cliSwitches = new ArrayList<>();
        if (SettingsManager.getManager().getConfig().getBoolean("cef.disableGpuCompositing")) {
            cliSwitches.add("--disable-gpu-compositing");
        }
        CefApp.addAppHandler(new CefAppHandlerAdapter(cliSwitches.toArray(new String[0])) {
            @Override
            public void stateHasChanged(CefAppState state) {
                // Shutdown the app if the native CEF part is terminated
                if (state == CefApp.CefAppState.TERMINATED) {
                    App.getApp().stop(false);
                }
            }
        });
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = useOSR;
        try {
            this.cefApp_ = CefApp.getInstance(settings);
        } catch (UnsatisfiedLinkError ule) {
            if (ule.getMessage().contains("Can't load IA 32-bit .dll")) {
                String errorMessage = "Cannot run the 64-bit version of overlay with a 32-bit Java installation.\n" +
                        "Download the win32 version of overlay or setup 64-bit Java.";
                LOGGER.error(errorMessage);
                JOptionPane.showMessageDialog(null, errorMessage, "Error!", JOptionPane.ERROR_MESSAGE);
                App.getApp().stop(true);
            } else if (ule.getMessage().contains("Can't load AMD 64-bit .dll")) {
                String errorMessage = "Cannot run the 32-bit version of overlay with a 64-bit Java installation.\n" +
                        "Download the win64 version of overlay or setup 32-bit Java.";
                LOGGER.error(errorMessage);
                JOptionPane.showMessageDialog(null, errorMessage, "Error!", JOptionPane.ERROR_MESSAGE);
                App.getApp().stop(true);
            } else {
                LOGGER.error("Could not load CEF!", ule);
                App.getApp().stop(true);
            }
        }

        this.client_ = this.cefApp_.createClient();

        this.browser_ = this.client_.createBrowser(startURL, useOSR, isTransparent);
        this.browserUI_ = this.browser_.getUIComponent();

        this.client_.addKeyboardHandler(new KeyboardHandler());

        // Clear focus from the address field when the browser gains focus.
        this.client_.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public void onGotFocus(CefBrowser browser) {
                if (MainFrame.this.browserFocus_) return;
                MainFrame.this.browserFocus_ = true;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                browser.setFocus(true);
            }

            @Override
            public void onTakeFocus(CefBrowser browser, boolean next) {
                MainFrame.this.browserFocus_ = false;
            }
        });

        this.getContentPane().add(this.browserUI_, BorderLayout.CENTER);
        int windowWidth = SettingsManager.getManager().getConfig().getInt("window.width");
        int windowHeight = SettingsManager.getManager().getConfig().getInt("window.height");
        this.getContentPane().setPreferredSize(new Dimension(windowWidth, windowHeight));
        this.pack();
        this.setResizable(false);
        this.setVisible(true);
        this.setTitle("Overlay");

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CefApp.getInstance().dispose();
            }
        });
    }
}
