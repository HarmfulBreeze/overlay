package com.jcs.overlay.websocket.messages.J2W;

import com.jcs.overlay.utils.SettingsManager;
import com.jcs.overlay.websocket.messages.J2W.enums.SummonerSpellsDisplayStrategy;
import com.jcs.overlay.websocket.messages.J2W.enums.TimerStyle;
import com.typesafe.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class ChampSelectCreateMessage extends WebappMessage {
    private final TeamNames teamNames;
    private final TeamColors teamColors;
    private final WebappConfig webappConfig;
    private final String messageType = "ChampSelectCreate";

    public ChampSelectCreateMessage(TeamNames teamNames, TeamColors teamColors, WebappConfig webappConfig) {
        this.teamNames = teamNames;
        this.teamColors = teamColors;
        this.webappConfig = webappConfig;
    }

    @Override
    public String getMessageType() {
        return this.messageType;
    }

    public static class TeamNames {
        private final String team100;
        private final String team200;

        public TeamNames(String team100, String team200) {
            this.team100 = team100;
            this.team200 = team200;
        }
    }

    public static class TeamColors {
        private final Color team100Color;
        private final Color team200Color;

        public TeamColors(Color team100Color, Color team200Color) {
            this.team100Color = team100Color;
            this.team200Color = team200Color;
        }

        public TeamColors(List<Integer> team100Color, List<Integer> team200Color) {
            this.team100Color = new Color(team100Color);
            this.team200Color = new Color(team200Color);
        }
    }

    public static class Color {
        private final int red;
        private final int green;
        private final int blue;

        public Color(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public Color(List<Integer> colorList) {
            this.red = colorList.get(0);
            this.green = colorList.get(1);
            this.blue = colorList.get(2);
        }
    }

    public static class WebappConfig {
        private static final transient Config CONFIG = SettingsManager.getManager().getConfig();
        private final boolean championSplashesEnabled;
        private final String teamNamesFontSize;
        private final TimerStyle timerStyle;
        private final Map<String, Color> fontColors;
        private final SummonerSpellsDisplayStrategy summonerSpellsDisplayStrategy;

        public WebappConfig() {
            this.championSplashesEnabled = CONFIG.getBoolean("webapp.championSplashesEnabled");
            this.teamNamesFontSize = CONFIG.getString("webapp.teamNamesFontSize");
            this.timerStyle = TimerStyle.getTimerStyle(CONFIG.getString("webapp.timer.style"));
            this.fontColors = new HashMap<>();
            this.summonerSpellsDisplayStrategy = SummonerSpellsDisplayStrategy.getStrategy(CONFIG.getString("webapp.summonerSpellsDisplayStrategy"));

            List<Integer> banColorList = CONFIG.getIntList("webapp.fontColors.bans");
            List<Integer> picksColorList = CONFIG.getIntList("webapp.fontColors.picks");
            List<Integer> teamNamesColorList = CONFIG.getIntList("webapp.fontColors.teamNames");
            List<Integer> timerColorList = CONFIG.getIntList("webapp.fontColors.timer");
            this.fontColors.put("bans", new Color(banColorList));
            this.fontColors.put("picks", new Color(picksColorList));
            this.fontColors.put("teamNames", new Color(teamNamesColorList));
            this.fontColors.put("timer", new Color(timerColorList));
        }

        public WebappConfig(boolean championSplashesEnabled, String teamNamesFontSize, TimerStyle timerStyle, Map<String, Color> fontColors, SummonerSpellsDisplayStrategy summonerSpellsDisplayStrategy) {
            this.championSplashesEnabled = championSplashesEnabled;
            this.teamNamesFontSize = teamNamesFontSize;
            this.timerStyle = timerStyle;
            this.fontColors = fontColors;
            this.summonerSpellsDisplayStrategy = summonerSpellsDisplayStrategy;
        }
    }
}