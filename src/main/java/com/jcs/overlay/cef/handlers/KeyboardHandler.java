package com.jcs.overlay.cef.handlers;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefKeyboardHandlerAdapter;

public class KeyboardHandler extends CefKeyboardHandlerAdapter {
    @Override
    public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
        // F5
        if (event.windows_key_code == 0x74 && event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {
            browser.reload();
            return true;
        }
        return false;
    }
}
