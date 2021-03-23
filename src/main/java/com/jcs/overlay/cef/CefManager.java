package com.jcs.overlay.cef;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;

import static org.cef.CefApp.CefAppState.NONE;
import static org.cef.CefApp.CefAppState.TERMINATED;

public class CefManager {

    private boolean isInitialized = false;
    private boolean isStopped = false;
    private MainFrame mainFrame;

    // Private constructor
    private CefManager() {
    }

    // Instance getter
    public static CefManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Initializes CefManager. Only first call is effective: next calls are NOPs.
     */
    public synchronized void init() {
        if (!this.isInitialized && !this.isStopped) {
            if (!CefApp.startup(null)) {
                System.out.println("Startup initialization failed!");
                return;
            }
            String url = "file:///" + System.getProperty("user.dir") + "/web/index.html";
            url = url.replace('\\', '/');
            this.mainFrame = new MainFrame(url, false, false);
            this.isInitialized = true;
        }
    }

    /**
     * Stops CefManager.
     * CefManager will only be stopped if it has already been initialized, and has not yet been stopped.
     */
    public synchronized void stop() {
        if (this.isInitialized && !this.isStopped) {
            CefAppState cefAppState = CefApp.getState();
            // Dispose of CefApp only if it's currently running
            if (cefAppState != NONE && cefAppState != TERMINATED) {
                CefApp.getInstance().dispose();
            }
            // Dispose of the mainframe only if it has existed
            if (cefAppState != NONE) {
                this.mainFrame.dispose();
            }
            this.isStopped = true;
        }
    }

    public MainFrame getMainFrame() {
        return this.mainFrame;
    }

    // Singleton holder
    private static class Holder {
        private static final CefManager INSTANCE = new CefManager();
    }
}
