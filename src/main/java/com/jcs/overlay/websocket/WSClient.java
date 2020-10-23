package com.jcs.overlay.websocket;

import com.jcs.overlay.App;
import com.jcs.overlay.utils.SettingsManager;
import com.jcs.overlay.utils.Uint64Adapter;
import com.jcs.overlay.utils.Utils;
import com.jcs.overlay.websocket.holders.Bans;
import com.jcs.overlay.websocket.holders.Player;
import com.jcs.overlay.websocket.messages.C2J.champselect.Timer;
import com.jcs.overlay.websocket.messages.C2J.champselect.*;
import com.jcs.overlay.websocket.messages.C2J.summoner.SummonerIdAndName;
import com.jcs.overlay.websocket.messages.J2W.*;
import com.merakianalytics.orianna.types.core.staticdata.Champion;
import com.merakianalytics.orianna.types.core.staticdata.Champions;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.jcs.overlay.websocket.messages.C2J.champselect.Action.ActionType.*;

public class WSClient extends WebSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WSClient.class);
    private final SettingsManager settingsManager = SettingsManager.getManager();
    private final WSServer wsServer = WSServer.getInstance();
    private final Moshi moshi = new Moshi.Builder()
            .add(new Uint64Adapter())
            .add(Timer.Phase.class, EnumJsonAdapter.create(Timer.Phase.class).withUnknownFallback(Timer.Phase.UNKNOWN))
            .add(Action.ActionType.class, EnumJsonAdapter.create(Action.ActionType.class).withUnknownFallback(UNKNOWN))
            .build();
    private final List<Player> playerList = new ArrayList<>();
    private final Queue<Session> updateMessagesQueue = new ConcurrentLinkedQueue<>();
    private Session previousSession = null;
    private boolean championSelectStarted;
    private boolean receivedSummonerNamesUpdate;
    private boolean myTeamIsBlueTeam;
    private int previousActiveActionGroup = -1;
    private String summonerNamesCallId = null;
    private String chatCallId = null;
    private Bans bans;
    public WSClient(URI uri, Map<String, String> httpHeaders) {
        // Setup WebSocketClient
        super(uri, httpHeaders);

        // Setup SSL context
        try {
            SSLContext sslContext = Utils.getSslContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();
            this.setSocketFactory(factory);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Could not get a proper SSL context!", e);
            App.stop(true);
        }
    }

    /**
     * Extracts the data from the WAMP message through RegEx matching.
     *
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

    /**
     * Extracts the chat session state {@link String} from the JSON data through RegEx matching.
     *
     * @param json The JSON data coming from the WAMP response message to the chat session request.
     * @return The chat session state {@link String}, or {@code null} if the matching failed.
     */
    @Nullable
    private static String getChatSessionStateFromJson(@NotNull String json) {
        Pattern pattern = Pattern.compile("(?:\"sessionState\":\"(.*)\"})");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    /**
     * Sets the adjustedCellId of every player in the supplied {@code playerList}.
     *
     * @param playerList A {@code List} of {@link Player} containing every player in the draft.
     */
    private static void adjustCellIds(List<Player> playerList) {
        int blueCounter = 0;
        int redCounter = 5;
        for (Player player : playerList) {
            int teamId = player.getPlayerSelection().getTeam();
            if (teamId == 2) {
                player.setAdjustedCellId(redCounter);
                redCounter++;
            } else {
                player.setAdjustedCellId(blueCounter);
                blueCounter++;
            }
        }
    }

    public boolean championSelectHasStarted() {
        return this.championSelectStarted;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (code != CloseFrame.NEVER_CONNECTED && code != CloseFrame.NORMAL) {
            if (code == CloseFrame.ABNORMAL_CLOSE) { // Code sent by the League Client when closing
                LOGGER.info("Disconnected from client.");
            } else {
                LOGGER.error(String.format("Connection closed, code %d\nReason: %s\nInitiated by remote: %b\n", code, reason, remote));
            }
            this.handleChampSelectDelete();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("Connected to the client!");
        this.requestChatSessionState();
    }

    @Override
    public void onMessage(String message) {
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
            LOGGER.warn("Unknown message received: " + message);
        }
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            LOGGER.error("Connection error: " + ex.getMessage());
        } else {
            LOGGER.error("Exception caught: ", ex);
        }
    }

    private void handleChampSelectMessage(String message) {
        String json = WSClient.getDataFromWampMessage(message);
        if (json == null) {
            return;
        }

        if (!this.settingsManager.getConfig().getBoolean("debug.printAllClientMessages")) {
            JsonAdapter<SessionMessage> jsonAdapter = this.moshi.adapter(SessionMessage.class);

            SessionMessage jsonMessage;
            try {
                jsonMessage = jsonAdapter.fromJson(json);
                if (jsonMessage == null) {
                    throw new JsonDataException("jsonMessage is null!");
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
                return;
            }

            switch (jsonMessage.getEventType()) {
                case CREATE:
                    this.handleChampSelectCreate();
                    break;
                case UPDATE:
                    this.preHandleChampSelectUpdate(jsonMessage);
                    break;
                case DELETE:
                    this.handleChampSelectDelete();
                    break;
            }
        } else {
            System.out.println(json); // print JSON to console for debug purposes
        }
    }

    private void preHandleChampSelectUpdate(SessionMessage jsonMessage) {
        Session session = jsonMessage.getSession();
        if (this.summonerNamesCallId == null) {
            // Set myTeamIsBlueTeam for the rest of the draft
            if (session.getMyTeam() != null) {
                this.myTeamIsBlueTeam = this.isMyTeamBlueTeam(session.getMyTeam());
            }
            this.sendUpdateNamesRequest(session);
            this.updateMessagesQueue.add(session);
            adjustCellIds(this.playerList);
        } else if (!this.receivedSummonerNamesUpdate) {
            LOGGER.debug("Added update message to queue while waiting for names.");
            this.updateMessagesQueue.add(session);
        } else {
            this.handleChampSelectUpdate(session);
        }
    }

    private void handleChampSelectCreate() {
        LOGGER.info("Champion select has started!");
        this.previousSession = null;
        this.championSelectStarted = true;
        this.previousActiveActionGroup = -1;
        this.playerList.clear();
        this.updateMessagesQueue.clear();
        this.receivedSummonerNamesUpdate = false;
        this.bans = new Bans();

        SetupWebappMessage setupMessage = new SetupWebappMessage();
        this.wsServer.broadcastWebappMessage(SetupWebappMessage.class, setupMessage);

        List<String> championKeys = new ArrayList<>();
        Champions.get().forEach(champ -> championKeys.add(champ.getKey()));
        PreloadSplashImagesMessage preloadMessage = new PreloadSplashImagesMessage(championKeys);
        this.wsServer.broadcastWebappMessage(PreloadSplashImagesMessage.class, preloadMessage);
    }

    private void handleSummonerNamesUpdate(String message) {
        String json = WSClient.getDataFromWampMessage(message);
        if (json == null) {
            return;
        }
        LOGGER.debug("Received summoner names message!");

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
            LOGGER.error(e.getMessage(), e);
            return;
        }

        // We update our playerList with the summoner names
        for (SummonerIdAndName idAndName : summonerIdsAndNames) {
            Long summonerId = idAndName.getSummonerId();
            String summonerName = idAndName.getDisplayName();
            this.playerList.stream().filter(player -> player.getPlayerSelection().getSummonerId().equals(summonerId))
                    .forEach(player -> player.setSummonerName(summonerName));
        }
        this.playerList.forEach(player -> {
            if (player.getSummonerName() == null || player.getSummonerName().isEmpty()) {
                player.setSummonerName("Summoner " + (player.getAdjustedCellId() + 1));
            }
        });

        // Finally we communicate the summoner names to the webapp...
        Map<Integer, String> playerMap = new HashMap<>();
        for (Player player : this.playerList) {
            String summonerName = player.getSummonerName();
            playerMap.put((int) player.getAdjustedCellId(), summonerName);
        }
        PlayerNamesMessage playerNamesMessage = new PlayerNamesMessage(playerMap);
        this.wsServer.broadcastWebappMessage(PlayerNamesMessage.class, playerNamesMessage);

        // And handle the messages waiting in the queue
        while (!this.updateMessagesQueue.isEmpty()) {
            this.handleChampSelectUpdate(this.updateMessagesQueue.poll());
        }
        LOGGER.debug("Update messages queue is cleared.");
        this.receivedSummonerNamesUpdate = true;
    }

    private void handleChampSelectUpdate(final Session session) {
        boolean isFirstUpdate = this.previousSession == null;

        List<List<Action>> newActions = session.getActions();
        List<List<Action>> oldActions = null;           // Used for actions
        List<PlayerSelection> oldPSelections = null;    // Used for summoner spells and trades
        if (isFirstUpdate) { // First update
            if (session.isSpectating()) {
                LOGGER.debug("Currently spectating the game.");
            } else {
                LOGGER.debug("Not a spectator!");
            }
        } else { // any other update
            oldActions = this.previousSession.getActions();
            oldPSelections = this.getPlayerSelectionList(this.previousSession.getMyTeam(), this.previousSession.getTheirTeam());
        }

        // Update the player list with the new player selections
        List<PlayerSelection> newPSelections = this.getPlayerSelectionList(session.getMyTeam(), session.getTheirTeam());
        for (int i = 0; i < newPSelections.size(); i++) {
            this.playerList.get(i).setPlayerSelection(newPSelections.get(i));
        }

        // Get the current active action group, if -1 then newActions is null or empty
        int activeActionGroupIndex = this.getActiveActionGroupIndex(newActions);
        // Check if there are new actions
        if (!newActions.equals(oldActions) && activeActionGroupIndex != -1) {
            // Active action group changed
            if (isFirstUpdate || activeActionGroupIndex != this.previousActiveActionGroup) {
                if (!isFirstUpdate) { // first update -> no oldActions
                    // At least one newly completed action needs to be handled
                    this.getNewlyCompletedActions(oldActions, newActions).forEach(this::handleStartedOrCompletedAction);
                }
                // Handle new active group actions
                newActions.get(activeActionGroupIndex).forEach(this::handleStartedOrCompletedAction);
            } else { // Active action group did not change, let's handle action updates
                List<Action> activeActionGroup = newActions.get(activeActionGroupIndex);
                List<Action> oldActiveActionGroup = oldActions.get(activeActionGroupIndex);
                if (activeActionGroup.size() != oldActiveActionGroup.size()) {
                    LOGGER.warn("newActions action group size is different!! might throw a lot");
                }
                for (int i = 0; i < activeActionGroup.size(); i++) {
                    Action updatedAction = activeActionGroup.get(i);
                    Action oldAction = oldActiveActionGroup.get(i);
                    // No handling of ten bans reveal
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
                                LOGGER.debug("New champion selected by " + player.getSummonerName() + "! " + championName);
                                if (updatedAction.getType() == PICK) {
                                    SetPickIntentMessage msg = new SetPickIntentMessage(player.getAdjustedCellId(), championKey);
                                    this.wsServer.broadcastWebappMessage(SetPickIntentMessage.class, msg);
                                }
                            } else {
                                LOGGER.error("Unknown player with cellId " + updatedAction.getActorCellId());
                            }
                        }
                        if (updatedAction.isCompleted() != oldAction.isCompleted()) { // Action just completed
                            this.handleStartedOrCompletedAction(updatedAction);
                        }
                    }
                }
            }
        }

        // Handle timer
        Timer timer = session.getTimer();
        Timer.Phase timerPhase = timer.getPhase();
        // TODO: handle unknown phase better
        if (timerPhase != Timer.Phase.UNKNOWN) { // If phase is known, we can assume that we have info on the timer
            if (!isFirstUpdate && timerPhase != this.previousSession.getTimer().getPhase()) {
                LOGGER.debug("New phase: " + timerPhase);
            }

            // Update timer in webapp
            UpdateTimerStateMessage updateTimerStateMessage = new UpdateTimerStateMessage(timerPhase, timer.getInternalNowInEpochMs(), timer.getAdjustedTimeLeftInPhase());
            this.wsServer.broadcastWebappMessage(UpdateTimerStateMessage.class, updateTimerStateMessage);
        }

        // Setup summoner spells
        if (isFirstUpdate) {
            for (Player player : this.playerList) {
                PlayerSelection ps = player.getPlayerSelection();
                Long spell1Id = ps.getSpell1Id();
                Long spell2Id = ps.getSpell2Id();
                if (spell1Id != 0 && spell2Id != 0) { // If we have info on the enemy team sums
                    String summonerName = player.getSummonerName();
                    LOGGER.debug(summonerName + " has summoner spells "
                            + SummonerSpell.withId(spell1Id.intValue()).get().getName()
                            + " and " + SummonerSpell.withId(spell2Id.intValue()).get().getName());

                    long adjustedCellId = player.getAdjustedCellId();
                    SetSummonerSpellsMessage msg1 = new SetSummonerSpellsMessage(adjustedCellId, 1, spell1Id);
                    SetSummonerSpellsMessage msg2 = new SetSummonerSpellsMessage(adjustedCellId, 2, spell2Id);
                    this.wsServer.broadcastWebappMessage(SetSummonerSpellsMessage.class, msg1, msg2);
                }
            }
        } else { // Handle summoner spell changes
            for (int i = 0; i < this.playerList.size(); i++) {
                PlayerSelection newPs = this.playerList.get(i).getPlayerSelection();
                PlayerSelection oldPs = oldPSelections.get(i);
                String summonerName = this.playerList.get(i).getSummonerName();
                Long newSpell1Id = newPs.getSpell1Id();
                Long newSpell2Id = newPs.getSpell2Id();
                long adjustedCellId = this.playerList.get(i).getAdjustedCellId();
                if (!newSpell1Id.equals(oldPs.getSpell1Id()) && newSpell1Id != 0) {
                    LOGGER.debug(summonerName + " changed summoner spell 1 to "
                            + SummonerSpell.withId(newSpell1Id.intValue()).get().getName());

                    SetSummonerSpellsMessage msg = new SetSummonerSpellsMessage(adjustedCellId, 1, newSpell1Id);
                    this.wsServer.broadcastWebappMessage(SetSummonerSpellsMessage.class, msg);
                }
                if (!newSpell2Id.equals(oldPs.getSpell2Id()) && newSpell2Id != 0) {
                    LOGGER.debug(summonerName + " changed summoner spell 2 to "
                            + SummonerSpell.withId(newSpell2Id.intValue()).get().getName());

                    SetSummonerSpellsMessage msg = new SetSummonerSpellsMessage(adjustedCellId, 2, newSpell2Id);
                    this.wsServer.broadcastWebappMessage(SetSummonerSpellsMessage.class, msg);
                }
            }
        }

        // Picks are over, so we need to watch champion trades
        if (timerPhase == Timer.Phase.FINALIZATION) {
            assert oldPSelections != null; // Will be non-null for sure as isFirstUpdate = false
            oldPSelections.addAll(this.previousSession.getMyTeam());
            oldPSelections.addAll(this.previousSession.getTheirTeam());
            for (int i = 0; i < this.playerList.size(); i++) {
                PlayerSelection newPs = this.playerList.get(i).getPlayerSelection();
                PlayerSelection oldPs = oldPSelections.get(i);
                long adjustedCellId = this.playerList.get(i).getAdjustedCellId();
                if (newPs.getChampionId() != oldPs.getChampionId()) {
                    SetPickIntentMessage msg = new SetPickIntentMessage(adjustedCellId,
                            Champion.withId(newPs.getChampionId()).get().getKey());
                    this.wsServer.broadcastWebappMessage(SetPickIntentMessage.class, msg);
                }
            }
        }

        this.previousActiveActionGroup = activeActionGroupIndex;
        this.previousSession = session;
    }

    private void handleChampSelectDelete() {
        if (this.previousSession != null) {
            this.summonerNamesCallId = null;
            this.championSelectStarted = false;
            this.previousSession = null;
            // Send the request to the web component asking to close champion select.
            LOGGER.info("Champion select has ended.");

            ResetWebappMessage msg = new ResetWebappMessage();
            this.wsServer.broadcastWebappMessage(ResetWebappMessage.class, msg);
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
            // Chat plugin isn't loaded, most likely
            try {
                LOGGER.debug("Waiting for chat plugin to load...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
            this.requestChatSessionState(); // We request the chat state again
            return;
        }

        String sessionState = WSClient.getChatSessionStateFromJson(json);
        if (sessionState != null && (sessionState.equals("loaded") || sessionState.equals("connected"))) {
            this.send("[5, \"OnJsonApiEvent_lol-champ-select_v1_session\"]");
            LOGGER.debug("Subscribed to champ select events.");
        } else {
            // Chat plugin is probably still not loaded
            try {
                LOGGER.debug("Waiting for chat plugin to load... (part 2)");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
            this.requestChatSessionState();
        }
    }

    private void handleStartedOrCompletedAction(Action action) {
        /*
         * Every action needs to be logged. This builder should have debug text appended to it and the output is fed
         * to the logger.
         */
        StringBuilder debug = new StringBuilder();

        // Handle every type of action
        switch (action.getType()) {
            case VOTE: {
                // Stub
                if (!action.isCompleted()) {
                    debug.append("Received a vote message.");
                } else {
                    debug.append("Action of type vote completed.");
                }
                break;
            }
            case TEN_BANS_REVEAL: {
                // Stub
                if (!action.isCompleted()) {
                    debug.append("Ten bans reveal started.");
                } else {
                    debug.append("Ten bans reveal completed.");
                }
                break;
            }
            case BAN: {
                // TODO: remove duplicate code
                // Handle player not found
                Player player = this.getPlayerByCellId(action.getActorCellId());
                if (player == null) {
                    LOGGER.error("Unknown player with cellId " + action.getActorCellId());
                    return;
                }

                // Set useful variables
                String summonerName = player.getSummonerName();
                String championName, championKey;
                if (action.getChampionId() != 0) {
                    Champion champion = Champion.withId(action.getChampionId()).get();
                    championName = champion.getName();
                    championKey = champion.getKey();
                } else { // Hack for no ban
                    championName = "None";
                    championKey = "None";
                }

                // Start handling
                if (!action.isCompleted()) {
                    debug.append(summonerName).append(" is banning... ");

                    // Show banning text in the webapp
                    SetBanPickMessage msg = new SetBanPickMessage(player.getAdjustedCellId(), false, true);
                    this.wsServer.broadcastWebappMessage(SetBanPickMessage.class, msg);

                    // Set the selected champion in the webapp
                    if (!championName.equals("None")) {
                        debug.append("Currently chosen: ").append(championName);

                        SetBanIntentMessage banIntentMessage = new SetBanIntentMessage(championKey, player.getAdjustedCellId());
                        this.wsServer.broadcastWebappMessage(SetBanIntentMessage.class, banIntentMessage);
                    }
                    LOGGER.debug(debug.toString());
                } else {
                    debug.append(summonerName).append(" banned ").append(championName);

                    // Add the ban to the holder
                    int teamId = player.getPlayerSelection().getTeam();
                    if (this.bans.canAdd(teamId)) {
                        int banId = this.bans.addBan(player, championKey);

                        // Send the ban to the webapp
                        NewBanMessage msg = new NewBanMessage(championKey, banId);
                        this.wsServer.broadcastWebappMessage(NewBanMessage.class, msg);
                    }

                    // Hide the banning text
                    SetBanPickMessage msg = new SetBanPickMessage(player.getAdjustedCellId(), false, false);
                    this.wsServer.broadcastWebappMessage(SetBanPickMessage.class, msg);
                }
                break;
            }
            case PICK: {
                // TODO: remove duplicate code
                // Handle player not found
                Player player = this.getPlayerByCellId(action.getActorCellId());
                if (player == null) {
                    LOGGER.error("Unknown player with cellId " + action.getActorCellId());
                    return;
                }

                // Set useful variables
                String summonerName = player.getSummonerName();
                String championName, championKey;
                if (action.getChampionId() != 0) {
                    Champion champion = Champion.withId(action.getChampionId()).get();
                    championName = champion.getName();
                    championKey = champion.getKey();
                } else { // Hack for no ban
                    championName = "None";
                    championKey = "None";
                }

                // Start handling
                if (!action.isCompleted()) {
                    debug.append(summonerName).append(" is picking... ");

                    // Show the picking text in the webapp
                    SetBanPickMessage msg = new SetBanPickMessage(player.getAdjustedCellId(), true, false);
                    this.wsServer.broadcastWebappMessage(SetBanPickMessage.class, msg);

                    // Set the selected champion in the webapp
                    if (!championName.equals("None")) {
                        debug.append("Currently chosen: ").append(championName);

                        SetPickIntentMessage msg2 = new SetPickIntentMessage(player.getAdjustedCellId(), championKey);
                        this.wsServer.broadcastWebappMessage(SetPickIntentMessage.class, msg2);
                    }
                } else {
                    debug.append(summonerName).append(" picked ").append(championName);

                    // Set the selected champion in the webapp
                    SetPickIntentMessage msg1 = new SetPickIntentMessage(player.getAdjustedCellId(), championKey);
                    this.wsServer.broadcastWebappMessage(SetPickIntentMessage.class, msg1);

                    // Hide the picking text in the webapp
                    SetBanPickMessage message = new SetBanPickMessage(player.getAdjustedCellId(), false, false);
                    this.wsServer.broadcastWebappMessage(SetBanPickMessage.class, message);
                }
                break;
            }
            case UNKNOWN: {
                if (!action.isCompleted()) {
                    debug.append("Received unknown action type!!");
                } else {
                    debug.append("Action of unknown type completed.");
                }
                break;
            }
            // No need for a default case, it's already handled by ActionType.UNKNOWN
        }

        // Send the built debug string to the logger
        LOGGER.debug(debug.toString());
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

    /**
     * Sends the WebSocket query message to retrieve all players names from the provided SessionMessage.
     *
     * @param session SessionMessage object containing the players to retrieve the names of.
     */
    private void sendUpdateNamesRequest(Session session) {
        this.summonerNamesCallId = RandomStringUtils.randomAlphanumeric(10);

        StringBuilder builder = new StringBuilder();
        builder.append("[2, \"").append(this.summonerNamesCallId).append("\", \"/lol-summoner/v2/summoner-names\", [");

        List<PlayerSelection> allPlayers = this.getPlayerSelectionList(session.getMyTeam(), session.getTheirTeam());

        for (PlayerSelection player : allPlayers) {
            Player wrapper = new Player(player);
            if (this.playerList.stream().noneMatch(plWrapper -> plWrapper.getPlayerSelection().getCellId() == player.getCellId())) {
                this.playerList.add(wrapper);
                builder.append(player.getSummonerId()).append(", ");
            }
        }
        builder.delete(builder.length() - 2, builder.length()); // Removes the trailing ", "
        builder.append("]]");
        String query = builder.toString();

        this.send(query);
        LOGGER.debug("Sent update name request: " + query);
    }

    /**
     * Checks if <i>myTeam</i> is blue team (Team 100/Team 1/Order).
     *
     * @param myTeam A <i>non-null</i> list of {@link PlayerSelection} objects from {@link Session#getMyTeam()}.
     * @return {@code true} if myTeam is blue team, else false.
     */
    private boolean isMyTeamBlueTeam(@NotNull List<PlayerSelection> myTeam) {
        return myTeam.get(0).getTeam() == 1;
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
            if (this.myTeamIsBlueTeam) { // If myTeam is blue team then it's really easy
                return this.playerList.get((int) cellId);
            } else { // Else we go and find the player with the same cellID with the Stream API
                Stream<Player> stream = this.playerList.stream();
                Optional<Player> player = stream.filter(p -> p.getPlayerSelection().getCellId() == cellId).findFirst();
                return player.orElse(null);
            }
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Gets all the PlayerSelection from the provided {@link List}s and puts them in the same order as the player list.
     *
     * @param myTeam    A {@link List} of {@link PlayerSelection} containing all the selections from {@code myTeam}.
     * @param theirTeam A {@link List} of {@link PlayerSelection} containing all the selections from {@code theirTeam}.
     * @return The resulting {@link List}.
     */
    private List<PlayerSelection> getPlayerSelectionList(List<PlayerSelection> myTeam, List<PlayerSelection> theirTeam) {
        List<PlayerSelection> psList = new ArrayList<>();
        // Always have blue team before red team
        if (this.myTeamIsBlueTeam) {
            psList.addAll(myTeam);
            psList.addAll(theirTeam);
        } else {
            psList.addAll(theirTeam);
            psList.addAll(myTeam);
        }
        return psList;
    }
}
