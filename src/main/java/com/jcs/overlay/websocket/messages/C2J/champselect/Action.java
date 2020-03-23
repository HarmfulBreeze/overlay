package com.jcs.overlay.websocket.messages.C2J.champselect;

import com.squareup.moshi.Json;

import java.util.Objects;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
public class Action {
    private long id;
    private long actorCellId;
    private int championId;
    private ActionType type;
    private boolean completed;

    public long getId() {
        return this.id;
    }

    public long getActorCellId() {
        return this.actorCellId;
    }

    public int getChampionId() {
        return this.championId;
    }

    public ActionType getType() {
        return this.type;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Action) {
            Action anAction = ((Action) obj);
            return this.id == anAction.id && this.actorCellId == anAction.actorCellId && this.championId == anAction.championId && this.type.equals(anAction.type) && this.completed == anAction.completed;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.actorCellId, this.championId, this.type, this.completed);
    }

    public enum ActionType {
        @Json(name = "pick") PICK,
        @Json(name = "vote") VOTE,
        @Json(name = "ban") BAN,
        @Json(name = "ten_bans_reveal") TEN_BANS_REVEAL,
        UNKNOWN
    }
}
