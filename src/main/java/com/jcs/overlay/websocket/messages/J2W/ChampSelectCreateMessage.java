package com.jcs.overlay.websocket.messages.J2W;

public class ChampSelectCreateMessage {
    private final TeamNames teamNames;
    private final TeamColors teamColors;
    private final String messageType = "ChampSelectCreate";

    public ChampSelectCreateMessage(TeamNames teamNames, TeamColors teamColors) {
        this.teamNames = teamNames;
        this.teamColors = teamColors;
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