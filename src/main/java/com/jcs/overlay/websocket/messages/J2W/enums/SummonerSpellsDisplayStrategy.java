package com.jcs.overlay.websocket.messages.J2W.enums;

import com.squareup.moshi.Json;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public enum SummonerSpellsDisplayStrategy {
    @Json(name = "gameStarting") GAME_STARTING("gameStarting"),
    @Json(name = "banPick") BAN_PICK("banPick");

    private static final String STRATEGY_CONFIG_PATH = "overlay.webapp.summonerSpellsDisplayStrategy";
    private final String strategyName;

    SummonerSpellsDisplayStrategy(String strategyName) {
        this.strategyName = strategyName;
    }

    public static void checkStrategy(Config config) throws ConfigException.ValidationFailed {
        String strategyName = config.getString("overlay.webapp.summonerSpellsDisplayStrategy");
        SummonerSpellsDisplayStrategy strategy = getStrategy(strategyName);
        if (strategy == null) {
            throw new ConfigException.ValidationFailed(Collections.singletonList(
                    new ConfigException.ValidationProblem(STRATEGY_CONFIG_PATH,
                            config.origin(),
                            "Invalid summoner spell display strategy.")));
        }
    }

    @Nullable
    public static SummonerSpellsDisplayStrategy getStrategy(String strategyName) {
        for (SummonerSpellsDisplayStrategy s : SummonerSpellsDisplayStrategy.values()) {
            if (s.strategyName.equals(strategyName)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.strategyName;
    }
}
