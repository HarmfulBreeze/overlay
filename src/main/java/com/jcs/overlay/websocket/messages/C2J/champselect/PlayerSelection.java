package com.jcs.overlay.websocket.messages.C2J.champselect;

import java.util.Objects;

public class PlayerSelection {
    private long cellId;
    private int championId;
    private int selectedSkinId;
    private long wardSkinId;
    private Long spell1Id; // uint64
    private Long spell2Id; // uint64
    private int team;
    private String assignedPosition;
    private int championPickIntent;
    private String playerType;
    private Long summonerId;
    private String entitledFeatureType;

    public long getCellId() {
        return this.cellId;
    }

    public int getChampionId() {
        return this.championId;
    }

    public Long getSpell1Id() {
        return this.spell1Id;
    }

    public Long getSpell2Id() {
        return this.spell2Id;
    }

    public int getTeam() {
        return this.team;
    }

    public Long getSummonerId() {
        return this.summonerId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PlayerSelection) {
            PlayerSelection ps = ((PlayerSelection) obj);
            return this.cellId == ps.cellId
                    && this.championId == ps.championId
                    && this.selectedSkinId == ps.selectedSkinId
                    && this.wardSkinId == ps.wardSkinId
                    && this.spell1Id.equals(ps.spell1Id)
                    && this.spell2Id.equals(ps.spell2Id)
                    && this.team == ps.team
                    && this.assignedPosition.equals(ps.assignedPosition)
                    && this.championPickIntent == ps.championPickIntent
                    && this.playerType.equals(ps.playerType)
                    && this.summonerId.equals(ps.summonerId)
                    && this.entitledFeatureType.equals(ps.entitledFeatureType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.cellId, this.championId, this.selectedSkinId, this.wardSkinId, this.spell1Id, this.spell2Id,
                this.team, this.assignedPosition, this.championPickIntent, this.playerType, this.summonerId, this.entitledFeatureType);
    }
}
