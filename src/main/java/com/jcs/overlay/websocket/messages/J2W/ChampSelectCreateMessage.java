package com.jcs.overlay.websocket.messages.J2W;

import java.util.List;

public class ChampSelectCreateMessage {
    private final TeamNames teamNames;
    private final TeamColors teamColors;
    private final boolean timerIsInStreamRectangle;
    private final String messageType = "ChampSelectCreate";

    public ChampSelectCreateMessage(TeamNames teamNames, TeamColors teamColors, boolean timerIsInStreamRectangle) {
        this.teamNames = teamNames;
        this.teamColors = teamColors;
        this.timerIsInStreamRectangle = timerIsInStreamRectangle;
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
            this.team100Color = new Color(team100Color.get(0), team100Color.get(1), team100Color.get(2));
            this.team200Color = new Color(team200Color.get(0), team200Color.get(1), team200Color.get(2));
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
    }
}