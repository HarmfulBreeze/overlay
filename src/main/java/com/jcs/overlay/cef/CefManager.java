package com.jcs.overlay.cef;

import org.cef.CefApp;

public class CefManager {

    private MainFrame mainFrame;

    public CefManager() {
        if (!CefApp.startup()) {
            System.out.println("Startup initialization failed!");
            return;
        }
        String url = "file:///" + System.getProperty("user.dir") + "/web/index.html";
        url = url.replace('\\', '/');
        this.mainFrame = new MainFrame(url, false, false);
    }

    public MainFrame getMainFrame() {
        return this.mainFrame;
    }
}
