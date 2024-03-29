package com.jcs.overlay.websocket.messages.J2W;

import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;
import com.jcs.overlay.websocket.messages.J2W.enums.SummonerSpellsDisplayStrategy;
import com.jcs.overlay.websocket.messages.J2W.enums.TimerStyle;
import com.squareup.moshi.Moshi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jcs.overlay.websocket.messages.J2W.SetupWebappMessage.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WebappMessageTest {
    private static final Moshi MOSHI = new Moshi.Builder().build();

    @Test
    void setupWebappMessageTest() {
        System.out.println("Starting champSelectCreateMessageTest...");
        TeamNames teamNames = new TeamNames("Blue team", "Red team");
        CoachNames coachNames = new CoachNames("Blue team coach", "Red team coach");
        Color c1 = new Color(0, 0, 0);
        Color c2 = new Color(255, 255, 255);
        TeamColors colors = new TeamColors(c1, c2);
        TimerStyle timerStyle = TimerStyle.MOVING;
        Map<String, Color> fontColors = new HashMap<>();
        fontColors.put("coaches", new Color(1, 1, 1));
        fontColors.put("picks", new Color(2, 2, 2));
        fontColors.put("teamNames", new Color(3, 3, 3));
        fontColors.put("timer", new Color(4, 4, 4));
        Map<String, String> fontSizes = new HashMap<>();
        fontSizes.put("coaches", "2vh");
        fontSizes.put("teamNames", "3vh");
        SummonerSpellsDisplayStrategy summonerSpellsDisplayStrategy = SummonerSpellsDisplayStrategy.values()[0];
        MusicSetup musicSetup = new MusicSetup(true, 0.5f);
        WebappConfig config = new WebappConfig(true, timerStyle, fontColors, fontSizes, summonerSpellsDisplayStrategy, musicSetup);
        SetupWebappMessage msg = new SetupWebappMessage(teamNames, coachNames, colors, config);

        String expected = "{\"coachNames\":{\"coach100\":\"Blue team coach\",\"coach200\":\"Red team coach\"},\"messageType\":\"SetupWebapp\",\"teamColors\":{\"team100Color\":{\"blue\":0,\"green\":0,\"red\":0},\"team200Color\":{\"blue\":255,\"green\":255,\"red\":255}},\"teamNames\":{\"team100\":\"Blue team\",\"team200\":\"Red team\"},\"webappConfig\":{\"championSplashesEnabled\":true,\"fontColors\":{\"coaches\":{\"blue\":1,\"green\":1,\"red\":1},\"timer\":{\"blue\":4,\"green\":4,\"red\":4},\"teamNames\":{\"blue\":3,\"green\":3,\"red\":3},\"picks\":{\"blue\":2,\"green\":2,\"red\":2}},\"fontSizes\":{\"coaches\":\"2vh\",\"teamNames\":\"3vh\"},\"musicSetup\":{\"shouldPlay\":true,\"volume\":0.5},\"summonerSpellsDisplayStrategy\":\"" + summonerSpellsDisplayStrategy + "\",\"timerStyle\":\"moving\"}}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(SetupWebappMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test champSelectCreateMessageTest completed.");
    }

    @Test
    void resetWebappMessageTest() {
        System.out.println("Starting champSelectDeleteMessageTest...");

        ResetWebappMessage msg = new ResetWebappMessage();
        String expected = "{\"messageType\":\"ResetWebapp\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(ResetWebappMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test champSelectDeleteMessageTest completed.");
    }

    @Test
    void newBanMessageTest() {
        System.out.println("Starting newBanMessageTest...");

        NewBanMessage msg = new NewBanMessage("Yasuo", 4);
        String expected = "{\"banId\":4,\"championKey\":\"Yasuo\",\"messageType\":\"NewBan\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(NewBanMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test newBanMessageTest completed.");
    }

    @Test
    void playerNamesMessageTest() {
        System.out.println("Starting playerNamesMessageTest...");

        Map<Integer, String> players = new HashMap<>();
        players.put(6, "Yassuo");
        PlayerNamesMessage msg = new PlayerNamesMessage(players);
        String expected = "{\"messageType\":\"PlayerNames\",\"players\":{\"6\":\"Yassuo\"}}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(PlayerNamesMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test playerNamesMessageTest completed.");
    }

    @Test
    void preloadSplashImagesMessageTest() {
        System.out.println("Starting preloadSplashImagesMessageTest...");

        List<String> championKeys = new ArrayList<>();
        championKeys.add("Yasuo");
        PreloadSplashImagesMessage msg = new PreloadSplashImagesMessage(championKeys);
        String expected = "{\"champions\":[\"Yasuo\"],\"messageType\":\"PreloadSplashImages\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(PreloadSplashImagesMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test preloadSplashImagesMessageTest completed.");
    }

    @Test
    void setBanIntentMessageTest() {
        System.out.println("Starting setBanIntentMessageTest...");

        SetBanIntentMessage msg = new SetBanIntentMessage("Yasuo", 4);
        String expected = "{\"actorCellId\":4,\"championKey\":\"Yasuo\",\"messageType\":\"SetBanIntent\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(SetBanIntentMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test setBanintentMessageTest completed.");
    }

    @Test
    void setBanPickMessageTest() {
        System.out.println("Starting setBanPickMessageTest...");

        SetBanPickMessage msg1 = new SetBanPickMessage(8, true, false);
        SetBanPickMessage msg2 = new SetBanPickMessage(7, false, true);
        SetBanPickMessage msg3 = new SetBanPickMessage(6, false, false);
        SetBanPickMessage msg4 = new SetBanPickMessage(5, true, true);

        String expected1 = "{\"cellId\":8,\"isBanning\":false,\"isPicking\":true,\"messageType\":\"SetBanPick\"}";
        String expected2 = "{\"cellId\":7,\"isBanning\":true,\"isPicking\":false,\"messageType\":\"SetBanPick\"}";
        String expected3 = "{\"cellId\":6,\"isBanning\":false,\"isPicking\":false,\"messageType\":\"SetBanPick\"}";
        String expected4 = "{\"cellId\":5,\"isBanning\":true,\"isPicking\":true,\"messageType\":\"SetBanPick\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg1.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSONs generated by Moshi to the expected ones...");
        assertEquals(expected1, MOSHI.adapter(SetBanPickMessage.class).toJson(msg1), "Generated message does not match the expected one!");
        assertEquals(expected2, MOSHI.adapter(SetBanPickMessage.class).toJson(msg2), "Generated message does not match the expected one!");
        assertEquals(expected3, MOSHI.adapter(SetBanPickMessage.class).toJson(msg3), "Generated message does not match the expected one!");
        assertEquals(expected4, MOSHI.adapter(SetBanPickMessage.class).toJson(msg4), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test setBanPickMessageTest completed.");
    }

    @Test
    void setPickIntentMessageTest() {
        System.out.println("Starting setPickIntentMessageTest...");
        SetPickIntentMessage msg = new SetPickIntentMessage(1, "Yasuo");
        String expected = "{\"actorCellId\":1,\"championKey\":\"Yasuo\",\"messageType\":\"SetPickIntent\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(SetPickIntentMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test setPickIntentMessageTest completed.");
    }

    @Test
    void setSummonerSpellsMessageTest() {
        System.out.println("Starting setSummonerSpellsMessageTest...");
        SetSummonerSpellsMessage msg = new SetSummonerSpellsMessage(1, 2, 3L);
        String expected = "{\"cellId\":1,\"messageType\":\"SetSummonerSpells\",\"spellId\":3,\"spellSlot\":2}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(SetSummonerSpellsMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test setSummonerSpellsMessageTest completed.");
    }

    @Test
    void updateTimerStateMessageTest() {
        System.out.println("Starting updateTimerStateMessageTest...");
        Timer.Phase phase = Timer.Phase.BAN_PICK;
        long internalNow = System.currentTimeMillis();
        int adjustedTimeLeftInPhase = 154;
        UpdateTimerStateMessage msg = new UpdateTimerStateMessage(phase, internalNow, adjustedTimeLeftInPhase);
        String expected = "{\"adjustedTimeLeftInPhase\":" + adjustedTimeLeftInPhase +
                ",\"internalNow\":" + internalNow +
                ",\"messageType\":\"UpdateTimerState\"" +
                ",\"phase\":\"" + phase + "\"}";

        System.out.println("Testing MessageType status...");
        assertNotEquals("OverrideThis", msg.getMessageType(), "MessageType is not set!");
        System.out.println("MessageType was set.");

        System.out.println("Comparing the JSON generated by Moshi to the expected one...");
        assertEquals(expected, MOSHI.adapter(UpdateTimerStateMessage.class).toJson(msg), "Generated message does not match the expected one!");
        System.out.println("Passed.");

        System.out.println("Test updateTimerStateMessageTest completed.");
    }
}