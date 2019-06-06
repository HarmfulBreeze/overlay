package com.jcs.overlay.websocket.messages.champselect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BannedChampionsTest {
    private BannedChampions bannedChampions;
    private BannedChampions previous;

    @BeforeEach
    void setUp() {
        this.bannedChampions = new BannedChampions();
        this.bannedChampions.myTeamBans = new ArrayList<>(Arrays.asList(132, 45));
        this.bannedChampions.theirTeamBans = new ArrayList<>(Arrays.asList(49, 53));
        this.bannedChampions.numBans = 10;

        this.previous = new BannedChampions();
        this.previous.myTeamBans = new ArrayList<>(Arrays.asList(132, 45));
        this.previous.theirTeamBans = new ArrayList<>(Arrays.asList(49, 53));
        this.previous.numBans = 10;
    }

    @Test
    void getLatestBan() {
        this.bannedChampions.myTeamBans.add(42);
        assertArrayEquals(new int[]{1, 42}, this.bannedChampions.getLatestBan(this.previous));
        this.previous.myTeamBans.add(42);

        this.bannedChampions.theirTeamBans.add(22);
        assertArrayEquals(new int[]{2, 22}, this.bannedChampions.getLatestBan(this.previous));
        this.previous.theirTeamBans.add(22);

        assertNull(this.bannedChampions.getLatestBan(this.previous));
    }
}