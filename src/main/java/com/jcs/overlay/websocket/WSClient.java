package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;
import com.jcs.overlay.websocket.messages.C2J.champselect.*;
import com.jcs.overlay.websocket.messages.C2J.summoner.SummonerIdAndName;
import com.jcs.overlay.websocket.messages.J2W.*;
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
    private boolean receivedSummonerNamesUpdate;
    private final List<Player> playerList = new ArrayList<>();
    private Integer previousActiveActionGroup = null;
    private String summonerNamesCallId = null;
    private String chatCallId = null;
    private List<SessionMessage> updateMessagesQueue;

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

    /**
     * @param message The WAMP message received.
     * @return If there is a json object, it gets returned. Else, the method returns null.
     */
    @Nullable
    private static String getDataFromWampMessage(@NotNull String message) {
        Pattern pattern = Pattern.compile("(?:\",(.*)])");
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) { // If the message has no json object, return null.
            return null;
        }

        return matcher.group(1);
    }

    @Nullable
    private static String getSessionStateFromJson(@NotNull String json) {
        Pattern pattern = Pattern.compile("(?:\"sessionState\":\"(.*)\"})");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
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

    @Override
    public void onOpen(ServerHandshake handshakedata) {
//        this.send("[5, \"OnJsonApiEvent_lol-champ-select_v1_session\"]");
//        this.send("[5, \"OnJsonApiEvent\"]");
//        this.send("[2, \"12345\", \"/lol-summoner/v2/summoner-names\", [33968983, 33968984]]");
//        this.send("[2, \"12345\", \"GetLolChatV1Session\"]");
        this.logger.info("Connected to the client!");
        this.requestChatSessionState();
    }

    @Override
    public void onMessage(@NotNull String message) {
        if (message.isEmpty()) {
            return;
        }

        if (message.startsWith("[3,\"" + this.summonerNamesCallId + "\"")) {
            this.handleSummonerNamesUpdate(message);
        } else if (message.startsWith("[3,\"" + this.chatCallId + "\"")) {
            this.handleChatSessionMessage(message);
        } else if (message.startsWith("[8,\"OnJsonApiEvent_lol-champ-select_v1_session\"")) {
            this.handleChampSelectMessage(message);
        } else {
            this.logger.warn("Unknown message received: " + message);
        }
    }

    private void requestChatSessionState() {
        this.chatCallId = RandomStringUtils.randomAlphanumeric(10);
        String query = "[2, \"" + this.chatCallId + "\", \"GetLolChatV1Session\"]";
        this.send(query);
    }

    private void handleChatSessionMessage(String message) {
        String json = WSClient.getDataFromWampMessage(message);
        if (json == null) {
            // chat plugin isn't loaded, most likely
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                this.logger.error(e.getMessage(), e);
            }
            this.requestChatSessionState(); // we request the chat state again
            return;
        }

        String sessionState = WSClient.getSessionStateFromJson(json);
        if (sessionState != null && (sessionState.equals("loaded") || sessionState.equals("connected"))) {
            this.send("[5, \"OnJsonApiEvent_lol-champ-select_v1_session\"]");
            this.logger.debug("Subscribed to champ select events.");
        } else {
            // chat plugin probably still not loaded
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                this.logger.error(e.getMessage(), e);
            }
            this.requestChatSessionState();
        }
    }

    private void handleChampSelectCreate(SessionMessage message) {
        this.logger.info("Champion select has started!");
        this.previousSession = message.getSession();
        this.isFirstUpdate = true;
        this.previousActiveActionGroup = null;
        this.updateMessagesQueue = new ArrayList<>();
        this.receivedSummonerNamesUpdate = false;

        // TODO: make it customizable
        TeamNames teamNames = new TeamNames("Blue team", "Red team");
        ChampSelectCreateMessage createMessage = new ChampSelectCreateMessage(teamNames);
        JsonAdapter<ChampSelectCreateMessage> adapter = this.moshi.adapter(ChampSelectCreateMessage.class);
        String json = adapter.toJson(createMessage);
        this.wsServer.broadcast(json);
    }

    private void handleSummonerNamesUpdate(String message) {
        String json = WSClient.getDataFromWampMessage(message);
        if (json == null) {
            return;
        }
        this.logger.debug("Received summoner names message!");

        // Deserialize JSON
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

        // We update our playerList with the summoner names
        Optional<Player> playerFound;
        for (SummonerIdAndName idAndName : summonerIdsAndNames) {
            Long summonerId = idAndName.getSummonerId();
            playerFound = this.playerList.stream().filter(player -> player.getPlayerSelection().getSummonerId().equals(summonerId)).findFirst();
            playerFound.ifPresent(player -> player.setSummonerName(idAndName.getDisplayName()));
        }
        this.playerList.forEach(player -> {
            if (player.getSummonerName() == null || player.getSummonerName().isEmpty()) {
                player.setSummonerName("Player " + (player.getPlayerSelection().getCellId() + 1));
            }
        });

        // Finally we communicate the summoner names to the webapp
        Map<Integer, String> playerMap = new HashMap<>();
        for (Player player : this.playerList) {
            int cellId = (int) player.getPlayerSelection().getCellId(); // cast is okay since cellId should be 0-9
            String summonerName = player.getSummonerName();
            playerMap.put(cellId, summonerName);
        }
        PlayerNames playerNames = new PlayerNames(playerMap);
        JsonAdapter<PlayerNames> adapter = this.moshi.adapter(PlayerNames.class);
        String jsonToWebapp = adapter.toJson(playerNames);
        this.wsServer.broadcast(jsonToWebapp);

        for (SessionMessage msg : this.updateMessagesQueue) {
            this.handleChampSelectUpdate(msg);
        }
        this.updateMessagesQueue.clear();
        this.logger.debug("Update messages queue is cleared.");
        this.receivedSummonerNamesUpdate = true;
    }

    private void handleChampSelectMessage(String message) {
        String json = WSClient.getDataFromWampMessage(message);
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
                this.preHandleChampSelectUpdate(jsonMessage);
                break;
            case DELETE:
                this.handleChampSelectDelete();
                break;
        }
    }

    private void preHandleChampSelectUpdate(SessionMessage jsonMessage) {
        if (this.summonerNamesCallId == null) {
            this.sendUpdateNamesRequest(jsonMessage.getSession());
            this.updateMessagesQueue.add(jsonMessage);
        } else if (!this.receivedSummonerNamesUpdate) {
            this.logger.debug("Added update message to queue while waiting for names.");
            this.updateMessagesQueue.add(jsonMessage);
        } else {
            this.handleChampSelectUpdate(jsonMessage);
        }
    }

    private void handleChampSelectUpdate(SessionMessage message) {
        this.logger.info("Updated!");

        Session session = message.getSession();

        // Check if this we're spectating or not. Unused for now.
        if (this.isFirstUpdate) {
            if (session.isSpectating()) {
                this.logger.debug("Currently spectating the game.");
            } else {
                this.logger.debug("Not a spectator!");
            }
        }

        List<List<Action>> newActions = session.getActions();
        List<List<Action>> oldActions = this.previousSession.getActions();

        Integer activeActionGroupIndex = this.getActiveActionGroupIndex(newActions);
        if ((!newActions.equals(oldActions) || this.isFirstUpdate) && activeActionGroupIndex != -1) {
            // Action group activation
            if (!activeActionGroupIndex.equals(this.previousActiveActionGroup)) {
                this.logger.debug("New action group activated!");
                if (this.previousActiveActionGroup != null) {
                    // there is at least one newly completed action since that's why we changed active action group
                    List<Action> newlyCompletedActions = this.getNewlyCompletedActions(oldActions, newActions);
                    for (Action action : newlyCompletedActions) {
                        this.handleCompletedAction(action);
                    }
                }
                // handle incomplete actions
                for (Action action : newActions.get(activeActionGroupIndex)) {
                    if (action.getId() >= 100) {
                        this.logger.debug("Ten bans reveal started.");
                        continue;
                    }
                    StringBuilder builder = new StringBuilder(this.playerList.get((int) action.getActorCellId()).getSummonerName());
                    if (action.getType().equals("ban")) {
                        builder.append(" is banning... ");
                        if (action.getChampionId() != 0) {
                            builder.append("Currently chosen: ").append(Champion.withId(action.getChampionId()).get().getName());
                        }
                    } else if (action.getType().equals("pick")) {
                        builder.append(" is picking... ");
                        if (action.getChampionId() != 0) {
                            builder.append("Currently chosen: ").append(Champion.withId(action.getChampionId()).get().getName());
                        }
                    }
                    this.logger.debug(builder.toString());
                }
            } else { // Active action group did not change, let's handle action updates
                List<Action> activeActionGroup = newActions.get(activeActionGroupIndex);
                List<Action> oldActiveActionGroup = oldActions.get(activeActionGroupIndex);
                if (activeActionGroup.size() != oldActions.get(activeActionGroupIndex).size()) {
                    this.logger.warn("newActions action group size is different!! might throw a lot");
                }
                for (int i = 0; i < activeActionGroup.size(); i++) {
                    Action updatedAction = activeActionGroup.get(i);
                    Action oldAction = oldActiveActionGroup.get(i);
                    // handle ten bans reveal
                    if (updatedAction.getId() >= 100) {
                        continue;
                    }
                    if (!updatedAction.equals(oldAction)) {
                        this.logger.debug("New action update!");
                        if (updatedAction.getChampionId() != oldAction.getChampionId()) {
                            this.logger.debug("New champion selected by "
                                    + this.playerList.get((int) updatedAction.getActorCellId()).getSummonerName()
                                    + "! " + Champion.withId(updatedAction.getChampionId()).get().getName());
                        }
                        if (updatedAction.isCompleted() != oldAction.isCompleted()) { // action just completed
                            this.handleCompletedAction(updatedAction);
                        }
                    }
                }
            }
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
        if (newPhase != Phase.UNKNOWN) { // If phase is known, we can consider that we have info on the timer
            if (newPhase != this.previousSession.getTimer().getPhase()) {
                this.logger.debug("New phase: " + newPhase);
            }

            // Update timer in webapp
            SetTimerMessage setTimerMessage = new SetTimerMessage(timer.getInternalNowInEpochMs(), timer.getTimeLeftInPhase());
            JsonAdapter<SetTimerMessage> adapter = this.moshi.adapter(SetTimerMessage.class);
            String jsonToWebapp = adapter.toJson(setTimerMessage);
            this.wsServer.broadcast(jsonToWebapp);
        }

        List<PlayerSelection> newPSelections = new ArrayList<>();
        newPSelections.addAll(session.getMyTeam());
        newPSelections.addAll(session.getTheirTeam());
        if (this.isFirstUpdate) {

        } else {
            List<PlayerSelection> oldPSelections = new ArrayList<>();
            oldPSelections.addAll(this.previousSession.getMyTeam());
            oldPSelections.addAll(this.previousSession.getTheirTeam());
            for (int i = 0; i < newPSelections.size(); i++) {
                PlayerSelection newPs = newPSelections.get(i);
                PlayerSelection oldPs = oldPSelections.get(i);
            }
        }


        this.previousActiveActionGroup = activeActionGroupIndex;
        this.previousSession = message.getSession();
        if (this.isFirstUpdate) {
            this.isFirstUpdate = false;
        }
    }

    private void handleCompletedAction(Action action) {
        if (action.getId() >= 100) {
            this.logger.debug("Ten bans reveal completed.");
            return;
        }
        StringBuilder builder = new StringBuilder(this.playerList.get((int) action.getActorCellId()).getSummonerName());
        if (action.getType().equals("ban")) {
            builder.append(" banned ").append(Champion.withId(action.getChampionId()).get().getName());
        } else if (action.getType().equals("pick")) {
            String championName = Champion.withId(action.getChampionId()).get().getName();
            builder.append(" picked ").append(championName);

            // Broadcast it to webapp
            NewPick newPick = new NewPick(championName, action.getActorCellId());
            JsonAdapter<NewPick> adapter = this.moshi.adapter(NewPick.class);
            String json = adapter.toJson(newPick);
            this.wsServer.broadcast(json);
        }

        this.logger.debug(builder.toString());
    }

    @NotNull
    private Integer getActiveActionGroupIndex(List<List<Action>> actions) {
        for (int i = 0; i < actions.size(); i++) {
            if (!actions.get(i).stream().allMatch(Action::isCompleted)) {
                return i;
            }
        }
        return actions.size() - 1;
    }

    @NotNull
    private List<Action> getNewlyCompletedActions(List<List<Action>> oldActions, List<List<Action>> newActions) {
        // If they are equal, we just return an empty list
        if (oldActions.equals(newActions)) {
            return new ArrayList<>();
        }

        // Let's use a list with all the actions to make our task much easier
        List<Action> simpleOldActions = new ArrayList<>(), simpleNewActions = new ArrayList<>();
        oldActions.forEach(simpleOldActions::addAll);
        newActions.forEach(simpleNewActions::addAll);

        List<Action> newlyCompletedActions = new ArrayList<>();

        for (int i = 0; i < simpleOldActions.size() && i < simpleNewActions.size(); i++) {
            if (!simpleOldActions.get(i).isCompleted() && simpleNewActions.get(i).isCompleted()) {
                newlyCompletedActions.add(simpleNewActions.get(i));
            }
        }
        return newlyCompletedActions;
    }

    private void handleChampSelectDelete() {
        this.summonerNamesCallId = null;
        this.playerList.clear();
        this.previousSession = null;
        // Send the request to the web component asking to close champion select.
        this.logger.info("Champion select has ended.");

        ChampSelectDeleteMessage deleteMessage = new ChampSelectDeleteMessage();
        String json = this.moshi.adapter(ChampSelectDeleteMessage.class).toJson(deleteMessage);
        this.wsServer.broadcast(json);
    }

    /**
     * Sends the WebSocket query message to retrieve all teams from the provided SessionMessage.
     *
     * @param session SessionMessage object containing the teams to retrieve the names of.
     */
    private void sendUpdateNamesRequest(Session session) {
        this.summonerNamesCallId = RandomStringUtils.randomAlphanumeric(10);

        StringBuilder builder = new StringBuilder();
        builder.append("[2, \"").append(this.summonerNamesCallId).append("\", \"/lol-summoner/v2/summoner-names\", [");

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
