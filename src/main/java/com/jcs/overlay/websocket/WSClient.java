package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;
import com.jcs.overlay.websocket.messages.C2J.champselect.*;
import com.jcs.overlay.websocket.messages.C2J.summoner.SummonerIdAndName;
import com.jcs.overlay.websocket.messages.J2O.ChampSelectCreateMessage;
import com.jcs.overlay.websocket.messages.J2O.ChampSelectDeleteMessage;
import com.jcs.overlay.websocket.messages.J2O.TeamNames;
import com.merakianalytics.orianna.types.core.staticdata.Champion;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.adapters.EnumJsonAdapter;
import org.apache.commons.lang3.RandomStringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WSClient extends WebSocketClient {
    private final Logger logger = LoggerFactory.getLogger(WSClient.class);

    private final WebSocketServer wsServer = App.getApp().getWsServer();

    private final Moshi moshi = new Moshi.Builder()
            .add(Phase.class, EnumJsonAdapter.create(Phase.class).withUnknownFallback(Phase.UNKNOWN))
            .build();

    private Session previousSession = null;
    private boolean isFirstUpdate;
    private final List<Player> playerList = new ArrayList<>();
    private String callId = null;

    // Accept self-signed certificate. (if anyone has a better solution, please PR)
    public WSClient(URI uri, Map<String, String> httpHeaders) {
        super(uri, httpHeaders);
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            this.logger.error("Exception caught: ", e);
            Thread.currentThread().interrupt();
            return;
        }

        try {
            sslContext.init(null, trustAllCerts, null);
        } catch (KeyManagementException e) {
            this.logger.error("Exception caught: ", e);
        }
        SSLSocketFactory factory = sslContext.getSocketFactory();

        this.setSocketFactory(factory);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.send("[5, \"OnJsonApiEvent_lol-champ-select_v1_session\"]");
//        this.send("[5, \"OnJsonApiEvent\"]");
//        this.send("[2, \"12345\", \"/lol-summoner/v2/summoner-names\", [33968983, 33968984]]");
        this.logger.info("Connected to the client!");
    }

    @Override
    public void onMessage(@NotNull String message) {
        if (message.isEmpty()) {
            return;
        }

        if (message.startsWith("[3,\"" + this.callId + "\"")) {
            this.handleSummonerNamesUpdate(message);
        } else if (message.startsWith("[8,\"OnJsonApiEvent_lol-champ-select_v1_session\"")) {
            this.handleChampSelectMessage(message);
        } else {
            this.logger.warn("Unknown message received: " + message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (code != CloseFrame.NEVER_CONNECTED && code != CloseFrame.NORMAL) {
            if (code == CloseFrame.ABNORMAL_CLOSE) { // Code sent by the League Client when closing
                this.logger.info("Disconnected from client.");
            } else {
                this.logger.error(String.format("Connection closed, code %d\nReason: %s\nInitiated by remote: %b\n", code, reason, remote));
            }
        }
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            this.logger.error("Connection error: " + ex.getMessage());
        } else {
            this.logger.error("Exception caught: ", ex);
        }
    }

    private void handleSummonerNamesUpdate(String message) {
        String json = this.getDataFromWampMessage(message);
        if (json == null) {
            return;
        }

        this.logger.debug("Summoner Names Update message received: " + json);

        Type type = Types.newParameterizedType(List.class, SummonerIdAndName.class);
        JsonAdapter<List<SummonerIdAndName>> jsonAdapter = this.moshi.adapter(type);
        List<SummonerIdAndName> summonerIdsAndNames;
        try {
            summonerIdsAndNames = jsonAdapter.fromJson(json);
            if (summonerIdsAndNames == null || summonerIdsAndNames.isEmpty()) {
                throw new JsonDataException("summonerIdsAndNames is null or empty!");
            }
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
            return;
        }

        Optional<Player> playerFound;
        for (SummonerIdAndName idAndName : summonerIdsAndNames) {
            Long summonerId = idAndName.getSummonerId();
            playerFound = this.playerList.stream().filter(player -> player.getPlayerSelection().getSummonerId().equals(summonerId)).findFirst();
            playerFound.ifPresent(player -> player.setSummonerName(idAndName.getDisplayName()));
        }
    }

    private void handleChampSelectMessage(String message) {
        String json = this.getDataFromWampMessage(message);
        if (json == null) {
            return;
        }

        JsonAdapter<SessionMessage> jsonAdapter = this.moshi.adapter(SessionMessage.class);

        SessionMessage jsonMessage;
        try {
            jsonMessage = jsonAdapter.fromJson(json);
            if (jsonMessage == null) {
                throw new JsonDataException("jsonMessage is null!");
            }
        } catch (IOException e) {
            this.logger.error(e.getMessage(), e);
            return;
        }

        switch (jsonMessage.getEventType()) {
            case CREATE:
                this.handleChampSelectCreate(jsonMessage);
                break;
            case UPDATE:
                this.handleChampSelectUpdate(jsonMessage);
                break;
            case DELETE:
                this.handleChampSelectDelete();
                break;
        }
    }

    private void handleChampSelectCreate(SessionMessage message) {
        this.logger.info("Champion select has started!");
        this.previousSession = message.getSession();
        this.isFirstUpdate = true;

        // TODO: make it customizable
        TeamNames teamNames = new TeamNames("Blue team", "Red team");
        ChampSelectCreateMessage createMessage = new ChampSelectCreateMessage(teamNames);
        JsonAdapter<ChampSelectCreateMessage> adapter = this.moshi.adapter(ChampSelectCreateMessage.class);
        String json = adapter.toJson(createMessage);
        this.wsServer.broadcast(json);
    }

    private void handleChampSelectUpdate(SessionMessage message) {
        this.logger.info("Updated!");

        Session session = message.getSession();

        // If we don't already have names, we request them.
        if (this.callId == null) {
            this.sendUpdateNamesRequest(session);
        }

        if (this.isFirstUpdate) {
            if (session.isSpectating()) {
                this.logger.debug("Currently spectating the game.");
            } else {
                this.logger.debug("Not a spectator!");
            }

            this.isFirstUpdate = false;
        }

        List<List<Action>> newActions = session.getActions();
        List<List<Action>> oldActions = this.previousSession.getActions();
        if (!newActions.equals(oldActions)) {
            this.logger.debug("New action detected!");

            List<Action> actionGroup = newActions.get(newActions.size() - 1);
            Action latestAction = actionGroup.get(actionGroup.size() - 1);

            StringBuilder builder = new StringBuilder(this.playerList.get((int) latestAction.getActorCellId()).getSummonerName());
            if (latestAction.getType().equals("ban")) { // User is banning
                if (!latestAction.isCompleted()) {
                    builder.append(" is banning... ");
                    if (latestAction.getChampionId() != 0) {
                        builder.append("Currently chosen: ").append(Champion.withId(latestAction.getChampionId()).get().getName());
                    }
                } else {
                    builder.append(" banned ").append(Champion.withId(latestAction.getChampionId()).get().getName());
                }
            } else if (latestAction.getType().equals("pick")) { // User is picking
                if (!latestAction.isCompleted()) {
                    builder.append(" is picking... ");
                    if (latestAction.getChampionId() != 0) {
                        builder.append("Currently chosen: ").append(Champion.withId(latestAction.getChampionId()).get().getName());
                    }
                } else {
                    builder.append(" picked ").append(Champion.withId(latestAction.getChampionId()).get().getName());
                }
            }
            // TODO: handle 10 bans reveal

            this.logger.debug(builder.toString());
        }

        if (!session.getBans().equals(this.previousSession.getBans())) {
            int[] newBan = session.getBans().getLatestBan(this.previousSession.getBans());
            if (newBan != null) {
                StringBuilder builder = new StringBuilder("New banned champion! Team: ");
                if (newBan[0] == 1) {
                    builder.append("blue, ");
                } else {
                    builder.append("red, ");
                }
                Champion champion = Champion.withId(newBan[1]).get();
                builder.append("champion: ").append(champion.getName());
                this.logger.debug(builder.toString());
            }
        }

        Timer timer = session.getTimer();
        Phase newPhase = timer.getPhase();
        // TODO: handle unknown phase better
        if (newPhase != Phase.UNKNOWN) { // If phase is known, that also means that timer is loaded properly
            if (newPhase != this.previousSession.getTimer().getPhase()) {
                this.logger.debug("New phase: " + newPhase);
            }

            // logic to implement in the web component to reduce timer lag as much as possible
            Date date = new Date();
            long delta = date.getTime() - timer.getInternalNowInEpochMs();
            long timeToSetTo = timer.getTimeLeftInPhase() - delta;
            this.logger.debug("Should set timer to " + timeToSetTo);
        }

        this.previousSession = message.getSession();
    }

    private void handleChampSelectDelete() {
        this.playerList.clear();
        this.previousSession = null;
        // Send the request to the web component asking to close champion select.
        this.logger.info("Champion select has ended.");

        ChampSelectDeleteMessage deleteMessage = new ChampSelectDeleteMessage();
        String json = this.moshi.adapter(ChampSelectDeleteMessage.class).toJson(deleteMessage);
        this.wsServer.broadcast(json);
    }

    /**
     * @param message The WAMP message received.
     * @return If there is a json object, it gets returned. Else, the method returns null.
     */
    @Nullable
    private String getDataFromWampMessage(String message) {
        Pattern pattern = Pattern.compile("(?:\",(.*)])");
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) { // If the message has no json object, return null.
            return null;
        }

        return matcher.group(1);
    }

    /**
     * Sends the WebSocket query message to retrieve all teams from the provided SessionMessage.
     *
     * @param session SessionMessage object containing the teams to retrieve the names of.
     */
    private void sendUpdateNamesRequest(Session session) {
        this.callId = RandomStringUtils.randomAlphanumeric(10);

        StringBuilder builder = new StringBuilder();
        builder.append("[2, \"").append(this.callId).append("\", \"/lol-summoner/v2/summoner-names\", [");

        List<PlayerSelection> allPlayers = new ArrayList<>();
        allPlayers.addAll(session.getMyTeam());
        allPlayers.addAll(session.getTheirTeam());

        for (PlayerSelection player : allPlayers) {
            Player wrapper = new Player(player);
            if (this.playerList.stream().noneMatch(plWrapper -> plWrapper.getPlayerSelection().getCellId() == player.getCellId())) {
                this.playerList.add(wrapper);
                builder.append(player.getSummonerId()).append(", ");
            }
        }
        builder.delete(builder.length() - 2, builder.length()); // On enlève le dernier ", "
        builder.append("]]");
        String query = builder.toString();

        this.send(query);
        this.logger.debug("Sent update name request: " + query);
    }
}
