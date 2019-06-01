package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.champselect.PlayerSelection;
import com.jcs.overlay.websocket.messages.champselect.Player;
import com.jcs.overlay.websocket.messages.champselect.SessionMessage;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
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
            e.printStackTrace();
            Thread.currentThread().interrupt();
            return;
        }

        try {
            sslContext.init(null, trustAllCerts, null);
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        SSLSocketFactory factory = sslContext.getSocketFactory();

        this.setSocketFactory(factory);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.send("[5,\"OnJsonApiEvent_lol-champ-select_v1_session\"]");
//        this.send("[2, \"12345\", \"/lol-summoner/v2/summoner-names\", [33968983, 33968984]]");
        logger.info("Connecté au client !");
    }

    @Override
    public void onMessage(String message) {
        if (message.isEmpty() || !message.contains("OnJsonApiEvent_lol-champ-select_v1_session")) {
            return;
        }

        if (true) {
            logger.info(message);
            return;
        }

        if (message.startsWith("[3,")) {
            //réponse à notre call pour récupérer les noms des joueurs
        }

        Pattern pattern = Pattern.compile("(\\{.*})");
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) { // Si le message ne comporte pas d'objet JSON, on return
            return;
        }

        String json = matcher.group();
        logger.info(json);
        Moshi moshi = new Moshi.Builder().build();

        JsonAdapter<SessionMessage> jsonAdapter = moshi.adapter(SessionMessage.class);

        SessionMessage jsonMessage;
        try {
            jsonMessage = jsonAdapter.fromJson(json);
            if (jsonMessage == null) {
                throw new JsonDataException("jsonMessage est null !");
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return;
        }

        switch (jsonMessage.getEventType()) {
            case Create:
                logger.info("La sélection des champions a commencé !");
                break;
            case Update:
                logger.info("Mise à jour reçue !");
                break;
            case Delete:
                logger.info("La sélection des champions s'est terminée.");
                // Envoyer la requête pour terminer la champ select.
                break;
        }

        sendUpdateNamesRequest(jsonMessage);
    }

    /**
     * Sends the WebSocket query message to retrieve all teams from the provided SessionMessage.
     * @param message SessionMessage object containing the teams to retrieve the names of.
     */
    private void sendUpdateNamesRequest(@NotNull SessionMessage message) {
        StringBuilder builder = new StringBuilder();
        builder.append("[2, \"12345\", \"/lol-summoner/v2/summoner-names\", [");

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
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (code != CloseFrame.NEVER_CONNECTED) {
            if (code == CloseFrame.ABNORMAL_CLOSE) {
                logger.info("Déconnecté du client.");
            } else {
                logger.error(String.format("Connexion fermée, code %d\nRaison : %s\nInitiated by remote: %b\n", code, reason, remote));
            }
        }
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            logger.error("Erreur de connexion : " + ex.getMessage());
        } else {
            logger.error("Une exception a été levée : ", ex);
        }
    }
}

