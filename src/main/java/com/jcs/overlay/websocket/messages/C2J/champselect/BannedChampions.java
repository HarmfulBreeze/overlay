package com.jcs.overlay.websocket.messages.C2J.champselect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class BannedChampions {
    List<Integer> myTeamBans;
    List<Integer> theirTeamBans;
    int numBans;

    public List<Integer> getMyTeamBans() {
        return this.myTeamBans;
    }

    public List<Integer> getTheirTeamBans() {
        return this.theirTeamBans;
    }

    public int getNumBans() {
        return this.numBans;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.myTeamBans, this.theirTeamBans, this.numBans);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BannedChampions) {
            BannedChampions bcObj = ((BannedChampions) obj);
            return this.myTeamBans.equals(bcObj.myTeamBans)
                    && this.theirTeamBans.equals(bcObj.theirTeamBans)
                    && this.numBans == bcObj.numBans;
        }
        return false;
    }

    /**
     * Gets the latest banned champion and its banning team.
     *
     * @param previous Previous {@link BannedChampions} object containing bans to compare against.
     * @return A possibly-null array of integers with two values:<br>
     * - The team that last banned (1 is myTeam, 2 is theirTeam)<br>
     * - The champion ID that was last banned<br>
     * A null return value indicates that no new ban was found.
     */
    @Nullable
    @Contract(pure = true, value = "null -> null")
    public int[] getLatestBan(BannedChampions previous) {
        if (previous == null) {
            return null;
        }
        if (!previous.myTeamBans.containsAll(this.myTeamBans)) {
            return new int[]{1, this.myTeamBans.get(this.myTeamBans.size() - 1)};
        } else if (!previous.theirTeamBans.containsAll(this.theirTeamBans)) {
            return new int[]{2, this.theirTeamBans.get(this.theirTeamBans.size() - 1)};
        }
        return null;
    }
}
