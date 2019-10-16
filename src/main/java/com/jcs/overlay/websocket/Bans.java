package com.jcs.overlay.websocket;

import com.merakianalytics.orianna.types.core.staticdata.Champion;

public class Bans {
    private String[] banArray;
    private int blueTeamOffset;
    private int redTeamOffset;

    public Bans() {
        this(10);
    }

    public Bans(int size) {
        this.banArray = new String[size];
        this.blueTeamOffset = 0;
        this.redTeamOffset = size / 2;
    }

    /**
     * Adds a ban to the array.
     *
     * @param teamId      1 = blue, 2 = red
     * @param championKey The {@link Champion} key to be added.
     * @return The array index at which the {@link Champion} key was added.
     */
    public int addBan(int teamId, String championKey) {
        if (!this.canAdd(teamId)) {
            throw new IndexOutOfBoundsException("Ban array is full!");
        }
        if (teamId == 1) {
            this.banArray[this.blueTeamOffset] = championKey;
            this.blueTeamOffset++;
            return this.blueTeamOffset - 1;
        } else {
            this.banArray[this.redTeamOffset] = championKey;
            this.redTeamOffset++;
            return this.redTeamOffset - 1;
        }
    }

    /**
     * Gets the last ban for a specific teamId.
     *
     * @param teamId 1 = blue, 2 = red
     * @return The last banned {@link Champion} key if any, else null.
     */
    public String getLastBan(int teamId) {
        if (teamId == 1) {
            if (this.blueTeamOffset != 0) {
                return this.banArray[this.blueTeamOffset - 1];
            } else {
                return null;
            }
        } else {
            if (this.redTeamOffset != this.banArray.length / 2) {
                return this.banArray[this.redTeamOffset - 1];
            } else {
                return null;
            }
        }
    }

    /**
     * Checks if a {@link Champion} key can be added to the array.
     *
     * @param teamId 1 = blue, 2 = red
     * @return true if it can be added; else if it cannot
     */
    public boolean canAdd(int teamId) {
        if (teamId == 1) {
            return this.blueTeamOffset != this.banArray.length / 2;
        } else {
            return this.redTeamOffset != this.banArray.length;
        }
    }
}
