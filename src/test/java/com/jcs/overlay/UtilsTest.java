package com.jcs.overlay;

import com.jcs.overlay.utils.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void parseLockfile() {
        String testString = "LeagueClient:15332:54875:FORISh-O8W_FhiXSG2qnfQ:https";
        String[] expectedArray = testString.split(":");
        assertArrayEquals(expectedArray, Utils.parseLockfile(testString));
        assertThrows(IllegalArgumentException.class, () -> {
            String testString2 = "LeagueClient";
            Utils.parseLockfile(testString2);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            String testString3 = "LeagueClient:15332:54t75:FORISh-O8W_FhiXSG2qnfQ:https";
            Utils.parseLockfile(testString3);
        });
    }
}