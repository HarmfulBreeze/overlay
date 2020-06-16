package com.jcs.overlay.websocket.holders;

import com.merakianalytics.orianna.types.core.staticdata.Champion;

public class Bans {
    private final String[] banArray;
    private final int[] playerOffset;
    private int blueTeamOffset;
    private int redTeamOffset;

    public Bans() {
        this(10);
    }

    public Bans(int size) {
        this.banArray = new String[size];
        this.playerOffset = new int[size];
        this.blueTeamOffset = 0;
        this.redTeamOffset = size / 2;
    }

    /**
     * Adds a ban to the array. You should first check if it can be added with {@link Bans#canAdd}.
     *
     * @param player      The banning actor.
     * @param championKey The {@link Champion} key to be added.
     * @return The array index at which the {@link Champion} key was added.
     * @throws IndexOutOfBoundsException If the array is already full.
     */
    public int addBan(Player player, String championKey) {
        int teamId = player.getPlayerSelection().getTeam();
        if (!this.canAdd(teamId)) {
            throw new IndexOutOfBoundsException("Ban array is full!");
        }
        int adjustedCellId = (int) player.getAdjustedCellId();
        int banId = adjustedCellId + this.playerOffset[adjustedCellId];
        this.banArray[banId] = championKey;
        this.playerOffset[adjustedCellId]++;
        if (teamId == 1) {
            this.blueTeamOffset++;
        } else {
            this.redTeamOffset++;
        }
        return banId;
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
