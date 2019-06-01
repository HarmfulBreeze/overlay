package com.jcs.overlay.utils;

import static com.sun.jna.platform.win32.WinReg.HKEY;

class RegPath {
    final HKEY hkey;
    final String key;
    final String value;

    RegPath(HKEY hkey, String key) {
        this.hkey = hkey;
        this.key = key;
        this.value = "LocalRootFolder";
    }
}
