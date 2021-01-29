package com.jcs.overlay.cef;

import org.cef.CefApp;

public class CefManager {

    private MainFrame mainFrame;

    // Private constructor
    private CefManager() {
        if (!CefApp.startup(null)) {
            System.out.println("Startup initialization failed!");
            return;
        }
        String url = "file:///" + System.getProperty("user.dir") + "/web/index.html";
        url = url.replace('\\', '/');
        this.mainFrame = new MainFrame(url, false, false);
    }

    // Instance getter
    public static CefManager getInstance() {
        return Holder.INSTANCE;
    }

    // Singleton holder
    private static class Holder {
        private static final CefManager INSTANCE = new CefManager();
    }

    public MainFrame getMainFrame() {
        return this.mainFrame;
    }
}
