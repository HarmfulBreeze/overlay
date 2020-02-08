package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;
import com.jcs.overlay.websocket.messages.C2J.champselect.*;
import com.jcs.overlay.websocket.messages.C2J.summoner.SummonerIdAndName;
import com.jcs.overlay.websocket.messages.J2W.*;
import com.merakianalytics.orianna.types.core.staticdata.Champion;
import com.merakianalytics.orianna.types.core.staticdata.SummonerSpell;
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

import static com.jcs.overlay.websocket.messages.C2J.champselect.Action.ActionType.*;

public class WSClient extends WebSocketClient {
    private final Logger logger = LoggerFactory.getLogger(WSClient.class);

    private final WebSocketServer wsServer = App.getApp().getWsServer();

    private final Moshi moshi = new Moshi.Builder()
            .add(Timer.Phase.class, EnumJsonAdapter.create(Timer.Phase.class).withUnknownFallback(Timer.Phase.UNKNOWN))
            .add(Action.ActionType.class, EnumJsonAdapter.create(Action.ActionType.class).withUnknownFallback(UNKNOWN))
            .build();
    private final List<Player> playerList = new ArrayList<>();
    private final List<SessionMessage> updateMessagesQueue = new ArrayList<>();
    private Session previousSession = null;
    private boolean isFirstUpdate;
    private boolean receivedSummonerNamesUpdate;
    private boolean myTeamIsBlueTeam;
    private int previousActiveActionGroup = -1;
    private String summonerNamesCallId = null;
    private String chatCallId = null;
    private Bans bans;

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
            this.handleChampSelectDelete();
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
        } else if (message.contains("\"" + this.chatCallId + "\"")) {
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
        // If there is no JSON data or we get a CALLERROR
        if (json == null || message.startsWith("[4")) {
            // chat plugin isn't loaded, most likely
            try {
                this.logger.debug("Waiting for chat plugin to load...");
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
                this.logger.debug("Waiting for chat plugin to load... (part 2)");
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
        this.previousActiveActionGroup = -1;
        this.playerList.clear();
        this.updateMessagesQueue.clear();
        this.receivedSummonerNamesUpdate = false;
        this.bans = new Bans();

        // TODO: make it customizable
        TeamNames teamNames = new TeamNames("Blue team", "Red team");
        ChampSelectCreateMessage createMessage = new ChampSelectCreateMessage(teamNames);
        this.sendMessagesToWebapp(ChampSelectCreateMessage.class, createMessage);
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
                player.setSummonerName("Player " + (player.getAdjustedCellId() + 1));
            }
        });

        // Finally we communicate the summoner names to the webapp
        Map<Integer, String> playerMap = new HashMap<>();
        for (Player player : this.playerList) {
            String summonerName = player.getSummonerName();
            playerMap.put((int) player.getAdjustedCellId(), summonerName);
        }
        PlayerNamesMessage playerNamesMessage = new PlayerNamesMessage(playerMap);
        this.sendMessagesToWebapp(PlayerNamesMessage.class, playerNamesMessage);
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
            this.adjustCellIds();
        } else if (!this.receivedSummonerNamesUpdate) {
            this.logger.debug("Added update message to queue while waiting for names.");
            this.updateMessagesQueue.add(jsonMessage);
        } else {
            this.handleChampSelectUpdate(jsonMessage);
        }
    }

    private void handleChampSelectUpdate(SessionMessage message) {
        Session session = message.getSession();

        // Check if this we're spectating or not. Unused for now.
        if (this.isFirstUpdate) {
            if (session.isSpectating()) {
                this.logger.debug("Currently spectating the game.");
            } else {
                this.logger.debug("Not a spectator!");
            }
            this.myTeamIsBlueTeam = this.isMyTeamBlueTeam(session);
        }

        this.updatePlayerList(session);

        List<List<Action>> oldActions = this.previousSession.getActions();
        List<List<Action>> newActions = session.getActions();

        int activeActionGroupIndex = this.getActiveActionGroupIndex(newActions);

        // Check if there are new actions
        if ((!newActions.equals(oldActions) || this.isFirstUpdate) && activeActionGroupIndex != -1) {
            // Active action group changed
            if (activeActionGroupIndex != this.previousActiveActionGroup) {
                if (this.previousActiveActionGroup != -1) {
                    // there is at least one newly completed action since that's why we changed active action group
                    List<Action> newlyCompletedActions = this.getNewlyCompletedActions(oldActions, newActions);
                    for (Action action : newlyCompletedActions) {
                        this.handleCompletedAction(action);
                    }
                }
                // handle new active group actions
                for (Action action : newActions.get(activeActionGroupIndex)) {
                    if (action.getType() == TEN_BANS_REVEAL) {
                        // Don't handle ten bans reveal, we don't care about it
                        this.logger.debug("Ten bans reveal started.");
                        continue;
                    }
                    Player player = this.getPlayerByCellId(action.getActorCellId());
                    if (player == null) {
                        this.logger.error("Unknown player with cellId " + action.getActorCellId());
                        continue;
                    }
                    StringBuilder builder = new StringBuilder(player.getSummonerName());
                    switch (action.getType()) {
                        case VOTE:
                            this.logger.debug("Received a vote message.");
                            break;
                        case BAN: {
                            builder.append(" is banning... ");

                            SetBanPickMessage message1 = new SetBanPickMessage(player.getAdjustedCellId(), false, true);
                            this.sendMessagesToWebapp(SetBanPickMessage.class, message1);
                            if (action.getChampionId() != 0) {
                                Champion champion = Champion.withId(action.getChampionId()).get();
                                builder.append("Currently chosen: ").append(champion.getName());

                                SetBanIntentMessage banIntentMessage = new SetBanIntentMessage(champion.getKey(), player.getAdjustedCellId());
                                this.sendMessagesToWebapp(SetBanIntentMessage.class, banIntentMessage);
                            }
                            this.logger.debug(builder.toString());
                            break;
                        }
                        case PICK: {
                            builder.append(" is picking... ");

                            SetBanPickMessage message1 = new SetBanPickMessage(player.getAdjustedCellId(), true, false);
                            this.sendMessagesToWebapp(SetBanPickMessage.class, message1);

                            if (action.getChampionId() != 0) {
                                Champion champion = Champion.withId(action.getChampionId()).get();
                                builder.append("Currently chosen: ").append(champion.getName());

                                SetPickIntentMessage msg2 = new SetPickIntentMessage(player.getAdjustedCellId(), champion.getKey());
                                this.sendMessagesToWebapp(SetPickIntentMessage.class, msg2);
                            }
                            this.logger.debug(builder.toString());
                            break;
                        }
                        case UNKNOWN:
                            this.logger.warn("Received unknown action type!!");
                            break;
                        // No need for a default case, it is already handled by ActionType.UNKNOWN
                    }
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
                    if (updatedAction.getType() == TEN_BANS_REVEAL) {
                        continue;
                    }
                    if (!updatedAction.equals(oldAction)) {
                        if (updatedAction.getChampionId() != oldAction.getChampionId()) {
                            Player player = this.getPlayerByCellId(updatedAction.getActorCellId());
                            if (player != null) {
                                String championName;
                                String championKey;
                                if (updatedAction.getChampionId() != 0) {
                                    Champion champion = Champion.withId(updatedAction.getChampionId()).get();
                                    championName = champion.getName();
                                    championKey = champion.getKey();
                                } else {
                                    championName = "None";
                                    championKey = "None";
                                }
                                this.logger.debug("New champion selected by " + player.getSummonerName() + "! " + championName);
                                if (updatedAction.getType() == PICK) {
                                    SetPickIntentMessage msg = new SetPickIntentMessage(player.getAdjustedCellId(), championKey);
                                    this.sendMessagesToWebapp(SetPickIntentMessage.class, msg);
                                }
                            } else {
                                this.logger.error("Unknown player with cellId " + updatedAction.getActorCellId());
                            }
                        }
                        if (updatedAction.isCompleted() != oldAction.isCompleted()) { // action just completed
                            this.handleCompletedAction(updatedAction);
                        }
                    }
                }
            }
        }

        Timer timer = session.getTimer();
        Timer.Phase newPhase = timer.getPhase();
        // TODO: handle unknown phase better
        if (newPhase != Timer.Phase.UNKNOWN) { // If phase is known, we can consider that we have info on the timer
            if (newPhase != this.previousSession.getTimer().getPhase()) {
                this.logger.debug("New phase: " + newPhase);
            }

            // Update timer in webapp
            SetTimerMessage setTimerMessage = new SetTimerMessage(timer.getInternalNowInEpochMs(), timer.getAdjustedTimeLeftInPhase());
            this.sendMessagesToWebapp(SetTimerMessage.class, setTimerMessage);
        }

        if (this.isFirstUpdate) {
            for (Player player : this.playerList) {
                PlayerSelection ps = player.getPlayerSelection();
                Long spell1Id = ps.getSpell1Id();
                Long spell2Id = ps.getSpell2Id();
                if (spell1Id != 0 && spell2Id != 0) { // if we have info on the enemy team sums
                    String summonerName = player.getSummonerName();
                    this.logger.debug(summonerName + " has summoner spells "
                            + SummonerSpell.withId(spell1Id.intValue()).get().getName()
                            + " and " + SummonerSpell.withId(spell2Id.intValue()).get().getName());

                    long adjustedCellId = player.getAdjustedCellId();
                    SetSummonerSpellsMessage msg1 = new SetSummonerSpellsMessage(adjustedCellId, 1, spell1Id);
                    SetSummonerSpellsMessage msg2 = new SetSummonerSpellsMessage(adjustedCellId, 2, spell2Id);
                    this.sendMessagesToWebapp(SetSummonerSpellsMessage.class, msg1, msg2);
                }
            }
        } else {
            List<PlayerSelection> oldPSelections = new ArrayList<>();
            oldPSelections.addAll(this.previousSession.getMyTeam());
            oldPSelections.addAll(this.previousSession.getTheirTeam());
            for (int i = 0; i < this.playerList.size(); i++) {
                PlayerSelection newPs = this.playerList.get(i).getPlayerSelection();
                PlayerSelection oldPs = oldPSelections.get(i);
                String summonerName = this.playerList.get(i).getSummonerName();
                Long newSpell1Id = newPs.getSpell1Id();
                Long newSpell2Id = newPs.getSpell2Id();
                long adjustedCellId = this.getAdjustedCellId(newPs.getCellId());
                if (!newSpell1Id.equals(oldPs.getSpell1Id()) && newSpell1Id != 0) {
                    this.logger.debug(summonerName + " changed summoner spell 1 to "
                            + SummonerSpell.withId(newSpell1Id.intValue()).get().getName());

                    SetSummonerSpellsMessage msg = new SetSummonerSpellsMessage(adjustedCellId, 1, newSpell1Id);
                    this.sendMessagesToWebapp(SetSummonerSpellsMessage.class, msg);
                }
                if (!newSpell2Id.equals(oldPs.getSpell2Id()) && newSpell2Id != 0) {
                    this.logger.debug(summonerName + " changed summoner spell 2 to "
                            + SummonerSpell.withId(newSpell2Id.intValue()).get().getName());

                    SetSummonerSpellsMessage msg = new SetSummonerSpellsMessage(adjustedCellId, 2, newSpell2Id);
                    this.sendMessagesToWebapp(SetSummonerSpellsMessage.class, msg);
                }
            }
        }


        this.previousActiveActionGroup = activeActionGroupIndex;
        this.previousSession = message.getSession();
        if (this.isFirstUpdate) {
            this.isFirstUpdate = false;
        }
    }

    @SafeVarargs
    private final <T> void sendMessagesToWebapp(Class<T> type, T... messages) {
        JsonAdapter<T> adapter = this.moshi.adapter(type);
        for (T message : messages) {
            this.wsServer.broadcast(adapter.toJson(message));
        }
    }

    private void updatePlayerList(Session session) {
        List<PlayerSelection> newPSelections = new ArrayList<>();
        newPSelections.addAll(session.getMyTeam());
        newPSelections.addAll(session.getTheirTeam());
        for (int i = 0; i < newPSelections.size(); i++) {
            this.playerList.get(i).setPlayerSelection(newPSelections.get(i));
        }
    }

    private void handleCompletedAction(Action action) {
        // Handle ten bans reveal
        if (action.getType() == TEN_BANS_REVEAL) {
            this.logger.debug("Ten bans reveal completed.");
            return;
        }

        Player player = this.getPlayerByCellId(action.getActorCellId());
        if (player == null) {
            this.logger.error("Unknown player with cellId " + action.getActorCellId());
            return;
        }
        StringBuilder builder = new StringBuilder(player.getSummonerName());

        String championName, championKey;
        if (action.getChampionId() != 0) {
            Champion champion = Champion.withId(action.getChampionId()).get();
            championName = champion.getName();
            championKey = champion.getKey();
        } else { // Hack for no ban
            championName = "None";
            championKey = "None";
        }

        switch (action.getType()) {
            case BAN: {
                builder.append(" banned ").append(championName);

                int teamId = this.getTeamIdFromCellId(action.getActorCellId());
                if (this.bans.canAdd(teamId)) {
                    int banId = this.bans.addBan(teamId, player.getAdjustedCellId(), championKey);
                    NewBanMessage msg1 = new NewBanMessage(championKey, banId);
                    this.sendMessagesToWebapp(NewBanMessage.class, msg1);
                }
                SetBanPickMessage message = new SetBanPickMessage(player.getAdjustedCellId(), false, false);
                this.sendMessagesToWebapp(SetBanPickMessage.class, message);
                break;
            }
            case PICK: {
                builder.append(" picked ").append(championName);

                SetPickIntentMessage msg1 = new SetPickIntentMessage(player.getAdjustedCellId(), championKey);
                this.sendMessagesToWebapp(SetPickIntentMessage.class, msg1);
                SetBanPickMessage message = new SetBanPickMessage(player.getAdjustedCellId(), false, false);
                this.sendMessagesToWebapp(SetBanPickMessage.class, message);
                break;
            }
            case UNKNOWN:
                builder.append("Action of unknown type completed.");
                break;
            case VOTE:
                builder.append("Action of type vote completed.");
                break;
        }

        this.logger.debug(builder.toString());
    }

    /**
     * Returns the active action group index.<br>
     * For example, let's see these actions from a matchmade 5v5 draft:
     * <pre>
     * [
     *     [
     *         {
     *             "actorCellId": 0,
     *             "championId": 0,
     *             "completed": true,
     *             "id": 0,
     *             "type": "ban"
     *         },
     *         (...)
     *         {
     *             "actorCellId": 9,
     *             "championId": 0,
     *             "completed": false,
     *             "id": 9,
     *             "type": "ban"
     *         }
     *     ],
     *     [
     *         {
     *             "actorCellId": -1,
     *             "championId": 0,
     *             "completed": false,
     *             "id": 100,
     *             "type": "ten_bans_reveal"
     *         }
     *     ]
     * ]</pre>
     * Here you can see that not all actions from the first group are marked as complete which means that not all bans
     * have been cast.<br>
     * Therefore, the active action group is the first one (the ban group) and its index is 0,
     * so the method will return 0.
     *
     * @param actions Actions to get the index from. (can be retrieved with {@link Session#getActions()})
     * @return The index of the active action group, or -1 if the provided actions are null or empty.
     */
    private int getActiveActionGroupIndex(List<List<Action>> actions) {
        if (actions == null || actions.isEmpty()) {
            return -1;
        }
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
        this.sendMessagesToWebapp(ChampSelectDeleteMessage.class, deleteMessage);
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

    private void adjustCellIds() {
        int c = 5;
        for (Player player : this.playerList) {
            int teamId = player.getPlayerSelection().getTeam();
            if (teamId == 2) {
                player.setAdjustedCellId(c);
                c++;
            } else {
                player.setAdjustedCellId(player.getPlayerSelection().getCellId());
            }
        }
    }

    private int getTeamIdFromCellId(long cellId) {
        Optional<Player> opt = this.playerList.stream()
                .filter(player -> player.getPlayerSelection().getCellId() == cellId)
                .findFirst();
        return opt.map(player -> player.getPlayerSelection().getTeam()).orElse(-1);
    }

    private boolean isMyTeamBlueTeam(Session session) {
        return session.getMyTeam().get(0).getTeam() == 1;
    }

    private long getAdjustedCellId(long actorCellId) {
        return this.playerList.get((int) actorCellId).getAdjustedCellId();
    }

    /**
     * Gets the {@link Player} with the specified cellId.
     *
     * @param cellId The cellId from the {@link Session}.
     * @return The {@link Player} with the specified cellId, or null if there isn't any.
     */
    @Nullable
    private Player getPlayerByCellId(long cellId) {
        try {
            return this.playerList.get((int) cellId);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Gets the {@link Player} with the specified adjustedCellId.
     *
     * @param adjustedCellId The adjustedCellId from the player list.
     * @return The {@link Player} with the specified adjustedCellId, or null if there isn't any.
     */
    @Nullable
    private Player getPlayerByAdjustedCellId(long adjustedCellId) {
        Optional<Player> opt = this.playerList.stream().filter(player -> player.getAdjustedCellId() == adjustedCellId)
                .findFirst();
        return opt.orElse(null);
    }
}
