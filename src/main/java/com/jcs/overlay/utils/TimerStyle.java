package com.jcs.overlay.utils;

import com.squareup.moshi.Json;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import java.util.Collections;

public enum TimerStyle {
    @Json(name = "moving") MOVING("moving"),
    @Json(name = "inStreamRectangle") INSTREAMRECTANGLE("inStreamRectangle"),
    @Json(name = "inTeamNames") INTEAMNAMES("inTeamNames");

    public static final String TIMER_STYLE_CONFIG_PATH = "overlay.webapp.timer.style";
    private final String styleName;

    TimerStyle(String styleName) {
        this.styleName = styleName;
    }

    public static void checkTimerStyle(Config config) throws ConfigException.ValidationFailed {
        String styleName = config.getString(TIMER_STYLE_CONFIG_PATH);
        TimerStyle style = getTimerStyle(styleName);
        if (style != null) {
            return;
        }
        throw new ConfigException.ValidationFailed(Collections.singletonList(
                new ConfigException.ValidationProblem(
                        TIMER_STYLE_CONFIG_PATH,
                        config.origin(),
                        "Invalid timer style.")));
    }

    public static TimerStyle getTimerStyle(String styleName) {
        for (TimerStyle style : TimerStyle.values()) {
            if (style.styleName.equals(styleName)) {
                return style;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.styleName;
    }
}
