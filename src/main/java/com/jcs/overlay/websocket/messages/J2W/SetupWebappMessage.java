package com.jcs.overlay.websocket.messages.J2W;

import com.jcs.overlay.utils.SettingsManager;
import com.jcs.overlay.websocket.messages.J2W.enums.SummonerSpellsDisplayStrategy;
import com.jcs.overlay.websocket.messages.J2W.enums.TimerStyle;
import com.typesafe.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class SetupWebappMessage extends WebappMessage {
    private final TeamNames teamNames;
    private final CoachNames coachNames;
    private final TeamColors teamColors;
    private final WebappConfig webappConfig;
    private final String messageType = "SetupWebapp";

    public SetupWebappMessage() {
        this.teamNames = new TeamNames();
        this.coachNames = new CoachNames();
        this.teamColors = new TeamColors();
        this.webappConfig = new WebappConfig();
    }

    public SetupWebappMessage(TeamNames teamNames, CoachNames coachNames, TeamColors teamColors, WebappConfig webappConfig) {
        this.teamNames = teamNames;
        this.coachNames = coachNames;
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

        public TeamNames() {
            Config config = SettingsManager.getConfig();
            this.team100 = config.getString("teams.blue.name");
            this.team200 = config.getString("teams.red.name");
        }

        public TeamNames(String team100, String team200) {
            this.team100 = team100;
            this.team200 = team200;
        }
    }

    public static class CoachNames {
        private final String coach100;
        private final String coach200;

        public CoachNames() {
            Config config = SettingsManager.getConfig();
            this.coach100 = config.getString("teams.blue.coach");
            this.coach200 = config.getString("teams.red.coach");
        }

        public CoachNames(String coach100, String coach200) {
            this.coach100 = coach100;
            this.coach200 = coach200;
        }
    }

    public static class TeamColors {
        private final Color team100Color;
        private final Color team200Color;

        public TeamColors() {
            Config config = SettingsManager.getConfig();
            this.team100Color = new Color(config.getIntList("teams.blue.rgbColor"));
            this.team200Color = new Color(config.getIntList("teams.red.rgbColor"));
        }

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

    public static class MusicSetup {
        private final boolean shouldPlay;
        private final float volume;

        public MusicSetup() {
            Config config = SettingsManager.getConfig();
            this.shouldPlay = config.getBoolean("webapp.music.enabled");
            this.volume = config.getNumber("webapp.music.volume").floatValue();
        }

        public MusicSetup(boolean shouldPlay, float volume) {
            this.shouldPlay = shouldPlay;
            this.volume = volume;
        }
    }

    public static class WebappConfig {
        private final boolean championSplashesEnabled;
        private final TimerStyle timerStyle;
        private final Map<String, Color> fontColors;
        private final Map<String, String> fontSizes;
        private final SummonerSpellsDisplayStrategy summonerSpellsDisplayStrategy;
        private final MusicSetup musicSetup;

        public WebappConfig() {
            Config config = SettingsManager.getConfig();
            this.championSplashesEnabled = config.getBoolean("webapp.championSplashesEnabled");
            this.timerStyle = TimerStyle.getTimerStyle(config.getString("webapp.timer.style"));
            this.fontColors = new HashMap<>();
            this.fontSizes = new HashMap<>();
            this.summonerSpellsDisplayStrategy = SummonerSpellsDisplayStrategy.getStrategy(config.getString("webapp.summonerSpellsDisplayStrategy"));
            this.musicSetup = new MusicSetup();

            List<Integer> coachesColorList = config.getIntList("webapp.fontColors.coaches");
            List<Integer> picksColorList = config.getIntList("webapp.fontColors.picks");
            List<Integer> teamNamesColorList = config.getIntList("webapp.fontColors.teamNames");
            List<Integer> timerColorList = config.getIntList("webapp.fontColors.timer");
            this.fontColors.put("coaches", new Color(coachesColorList));
            this.fontColors.put("picks", new Color(picksColorList));
            this.fontColors.put("teamNames", new Color(teamNamesColorList));
            this.fontColors.put("timer", new Color(timerColorList));

            this.fontSizes.put("coaches", config.getString("webapp.fontSizes.coaches"));
            this.fontSizes.put("teamNames", config.getString("webapp.fontSizes.teamNames"));
        }

        public WebappConfig(boolean championSplashesEnabled, TimerStyle timerStyle, Map<String, Color> fontColors, Map<String, String> fontSizes, SummonerSpellsDisplayStrategy summonerSpellsDisplayStrategy, MusicSetup musicSetup) {
            this.championSplashesEnabled = championSplashesEnabled;
            this.timerStyle = timerStyle;
            this.fontColors = fontColors;
            this.fontSizes = fontSizes;
            this.summonerSpellsDisplayStrategy = summonerSpellsDisplayStrategy;
            this.musicSetup = musicSetup;
        }
    }
}