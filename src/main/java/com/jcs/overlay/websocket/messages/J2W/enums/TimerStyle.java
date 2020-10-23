package com.jcs.overlay.websocket.messages.J2W.enums;

import com.squareup.moshi.Json;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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
        if (style == null) {
            throw new ConfigException.ValidationFailed(Collections.singletonList(
                    new ConfigException.ValidationProblem(
                            TIMER_STYLE_CONFIG_PATH,
                            config.origin(),
                            "Invalid timer style.")));
        }
    }

    @Nullable
    public static TimerStyle getTimerStyle(String styleName) {
        return Arrays.stream(TimerStyle.values())
                .filter(ts -> ts.styleName.equals(styleName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return this.styleName;
    }
}
