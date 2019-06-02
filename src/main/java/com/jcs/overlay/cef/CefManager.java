package com.jcs.overlay.cef;

import org.cef.CefApp;

public class CefManager {

    private MainFrame mainFrame;

    public CefManager() {
        if (!CefApp.startup()) {
            System.out.println("Startup initialization failed!");
            return;
        }
        this.mainFrame = new MainFrame("file:///C:/Users/piorr/WebstormProjects/overlay/build/index.html", false, false);
    }

    public MainFrame getMainFrame() {
        return this.mainFrame;
    }
}
