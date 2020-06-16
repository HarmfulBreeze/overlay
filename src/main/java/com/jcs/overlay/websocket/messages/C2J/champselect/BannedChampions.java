package com.jcs.overlay.websocket.messages.C2J.champselect;

import java.util.List;
import java.util.Objects;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
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
}
