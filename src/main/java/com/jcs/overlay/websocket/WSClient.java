package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.champselect.PlayerSelection;
import com.jcs.overlay.websocket.messages.champselect.Player;
import com.jcs.overlay.websocket.messages.champselect.SessionMessage;
import com.jcs.overlay.websocket.messages.summoner.SummonerIdAndName;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import org.apache.commons.lang3.RandomStringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
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

    private final List<Player> playerList = new ArrayList<>();
    private String callId = null;
    private final Moshi moshi = new Moshi.Builder().build();;

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
        if (code != CloseFrame.NEVER_CONNECTED) {
            if (code == CloseFrame.ABNORMAL_CLOSE) {
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

        this.logger.info("Summoner Names Update message received: " + json);

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
            case Create:
                this.logger.info("Champion select has started!");
                break;
            case Update:
                this.logger.info("Received an update!");
                break;
            case Delete:
                this.handleChampSelectEnded();
                break;
        }

        // If don't already have names, we request them.
        if (this.callId == null) {
            this.sendUpdateNamesRequest(jsonMessage);
        }
    }

    private void handleChampSelectEnded() {
         this.playerList.clear();
         // Send the request to the web component asking to close champion select.
         this.logger.info("Champion select has ended.");
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
     * @param message SessionMessage object containing the teams to retrieve the names of.
     */
    private void sendUpdateNamesRequest(@NotNull SessionMessage message) {
        StringBuilder builder = new StringBuilder();
        this.callId = RandomStringUtils.randomAlphanumeric(10);
        builder.append("[2, \"").append(this.callId).append("\", \"/lol-summoner/v2/summoner-names\", [");

        List<PlayerSelection> allPlayers = new ArrayList<>();
        allPlayers.addAll(message.getData().getMyTeam());
        allPlayers.addAll(message.getData().getTheirTeam());

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
        this.logger.info("Sent update name request: " + query);
    }
}
