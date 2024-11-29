import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javazoom.jl.player.advanced.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class AudioStreamingClientUI extends Application {
    // Server configurations
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int CONTROL_PORT = 12345;
    private static final int AUDIO_PORT = 12346;

    private PrintWriter out;
    private BufferedReader in;
    private Socket controlSocket;
    private Socket audioSocket;
    private List<String> trackList = new ArrayList<>();
    private ListView<String> trackListView = new ListView<>();
    private Label statusLabel = new Label("Status: Disconnected");
    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private AdvancedPlayer player;
    private int pausedOnFrame = 0;
    private String username;

    // UI elements
    private ListView<HBox> chatListView = new ListView<>();
    private TextField messageField = new TextField();
    private ObservableList<String> roomList = FXCollections.observableArrayList();
    private ListView<String> roomListView = new ListView<>(roomList);
    private TextField roomSearchField = new TextField();
    private ListView<String> playlistListView = new ListView<>();
    private ListView<String> playlistTracksView = new ListView<>();
    private ListView<String> roomMembersView = new ListView<>();
    private Label roomHeadLabel = new Label("Room Leader: N/A");
    private Label roomPasswordLabel = new Label("Room Password: ");

    private TextField roomNameField = new TextField();
    private TextField roomPasswordField = new TextField();

    private Button playButton = new Button("Play");
    private Button playSelectedTrackButton = new Button("Play Selected Track");
    private Button nextTrackButton = new Button("Next Track");
    private Button previousTrackButton = new Button("Previous Track");
    private Button addToQueueButton = new Button("Add to Queue");
    private Button removeFromQueueButton = new Button("Remove from Queue");
    private Button pauseButton = new Button("Pause/Resume");
    private Button stopButton = new Button("Stop");
    private Button passLeadershipButton = new Button("Pass Leadership");
    private Button deleteRoomButton = new Button("Delete Room");
    private Button kickUserButton = new Button("Kick User");
    private Button refreshRoomsButton = new Button("Refresh Rooms");
    private Button refreshTracksButton = new Button("Refresh Tracks");
    private Button loopButton = new Button("Loop");
    private Button deleteFromPlaylistButton = new Button("Delete Track From Playlist");
    private Button createPlaylistButton = new Button("Create Playlist");
    private Button deletePlaylistButton = new Button("Delete Playlist");
    private Button renamePlaylistButton = new Button("Rename Playlist");
    private Button sharePlaylistButton = new Button("Share Playlist");
    private Button addTrackToPlaylistButton = new Button("Add Track to Playlist");
    private Button addPlaylistToQueueButton = new Button("Add Playlist to Queue");
    private Button clearQueueButton = new Button("Clear Queue");
    private boolean isLooping = false;

    private Map<String, List<String>> playlists = new HashMap<>();
    private String currentTrack;
    private boolean isRoomLeader = false;
    private boolean inRoom = false;
    private Deque<String> songQueue = new ArrayDeque<>();
    private Deque<String> previousTracks = new ArrayDeque<>();

    private Map<String, List<String>> chatHistories = new HashMap<>();
    private String currentRoomName = "";

    private ListView<String> queueListView = new ListView<>();
    private TextField playlistNameField = new TextField();

    private Label upNextLabel = new Label("Up Next: ");
    private Deque<String> personalQueue = new ArrayDeque<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Audio Streaming Client");
        showLoginScreen(primaryStage);
    }

    private void showLoginScreen(Stage primaryStage) {
        Label titleLabel = new Label("Welcome to Music Streaming App");
        Label usernameLabel = new Label("Username:");
        Label passwordLabel = new Label("Password:");
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button("Login");
        Button createAccountButton = new Button("Create Account");
        Label messageLabel = new Label();

        VBox loginLayout = new VBox(10, titleLabel, usernameLabel,
                usernameField, passwordLabel, passwordField, loginButton,
                createAccountButton, messageLabel);
        loginLayout.setPadding(new Insets(20));
        Scene loginScene = new Scene(loginLayout, 400, 300);
        primaryStage.setScene(loginScene);
        primaryStage.show();

        loginButton.setOnAction(e -> {
            username = usernameField.getText();
            String password = passwordField.getText();
            if (!username.isEmpty() && !password.isEmpty()) {
                connectToServer("LOGIN " + username + " " + password,
                        primaryStage, messageLabel);
            } else {
                messageLabel.setText("Please enter username and password");
            }
        });

        createAccountButton.setOnAction(e -> {
            username = usernameField.getText();
            String password = passwordField.getText();
            if (!username.isEmpty() && !password.isEmpty()) {
                connectToServer("CREATE_ACCOUNT " + username + " " + password,
                        primaryStage, messageLabel);
            } else {
                messageLabel.setText("Please enter username and password");
            }
        });
    }

    private void showMainUI(Stage primaryStage) {
        // Build the main UI
        BorderPane mainLayout = new BorderPane();

        // Left pane: Rooms and controls
        Button createRoomButton = new Button("Create Room");
        Button joinRoomButton = new Button("Join Room");
        Button leaveRoomButton = new Button("Leave Room");
        roomPasswordField.setPromptText("Password (optional)");

        roomSearchField.setPromptText("Search Rooms...");
        roomSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterRoomList(newVal);
        });

        HBox roomControlButtons = new HBox(10, passLeadershipButton, kickUserButton);

        VBox leftPane = new VBox(10, new Label("Rooms"), roomSearchField,
                roomListView, roomNameField, roomPasswordField,
                createRoomButton, joinRoomButton,
                leaveRoomButton, refreshRoomsButton, roomHeadLabel,
                roomPasswordLabel, roomControlButtons, deleteRoomButton,
                new Label("Room Members"), roomMembersView);
        leftPane.setPadding(new Insets(10));

        // Center pane: Tracks and playback controls

        // Arrange buttons in grid
        GridPane buttonGrid = new GridPane();
        buttonGrid.setHgap(10);
        buttonGrid.setVgap(10);
        buttonGrid.add(addToQueueButton, 0, 0);
        buttonGrid.add(removeFromQueueButton, 1, 0);
        buttonGrid.add(playButton, 2, 0);
        buttonGrid.add(playSelectedTrackButton, 0, 1);
        buttonGrid.add(previousTrackButton, 1, 1);
        buttonGrid.add(nextTrackButton, 2, 1);
        buttonGrid.add(loopButton, 0, 2);
        buttonGrid.add(pauseButton, 1, 2);
        buttonGrid.add(stopButton, 2, 2);

        HBox playlistButtonBox = new HBox(10, createPlaylistButton, deletePlaylistButton, renamePlaylistButton, sharePlaylistButton);
        HBox playlistTrackButtonBox = new HBox(10, addTrackToPlaylistButton, deleteFromPlaylistButton, addPlaylistToQueueButton);
        VBox centerPane = new VBox(10, new Label("Tracks"), trackListView,
                refreshTracksButton, new Label("Playlists"), playlistListView,
                playlistNameField, playlistButtonBox, playlistTracksView,
                playlistTrackButtonBox, buttonGrid, upNextLabel, statusLabel);
        centerPane.setPadding(new Insets(10));

        // Right pane: Chat area and Queue
        chatListView.setFocusTraversable(false);
        VBox rightPane = new VBox(10, new Label("Chat"), chatListView,
                messageField, new Label("Queue"), queueListView, clearQueueButton);
        rightPane.setPadding(new Insets(10));

        mainLayout.setLeft(leftPane);
        mainLayout.setCenter(centerPane);
        mainLayout.setRight(rightPane);

        Scene scene = new Scene(mainLayout, 1200, 700);

        // Load styles.css
        URL cssUrl = getClass().getResource("styles.css");
        if (cssUrl == null) {
            System.err.println("styles.css not found");
        } else {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        // Set up event handlers
        setupEventHandlers(createRoomButton, joinRoomButton, leaveRoomButton,
                roomNameField, roomPasswordField,
                createPlaylistButton, deletePlaylistButton, renamePlaylistButton,
                playlistNameField, addToQueueButton, removeFromQueueButton);
    }

    private void setupEventHandlers(Button createRoomButton,
                                    Button joinRoomButton, Button leaveRoomButton,
                                    TextField roomNameField, TextField roomPasswordField,
                                    Button createPlaylistButton, Button deletePlaylistButton, Button renamePlaylistButton,
                                    TextField playlistNameField, Button addToQueueButton, Button removeFromQueueButton) {

        playButton.setOnAction(e -> handlePlayButton());
        playSelectedTrackButton.setOnAction(e -> handlePlaySelectedTrackButton());
        nextTrackButton.setOnAction(e -> handleNextTrackButton());
        previousTrackButton.setOnAction(e -> handlePreviousTrackButton());
        loopButton.setOnAction(e -> {
            isLooping = !isLooping;
            loopButton.setText(isLooping ? "Looping" : "Loop");
        });
        deleteFromPlaylistButton.setOnAction(e -> handleDeleteFromPlaylist());
        addToQueueButton.setOnAction(e -> handleAddToQueue());
        removeFromQueueButton.setOnAction(e -> handleRemoveFromQueue());
        pauseButton.setOnAction(e -> handlePauseButton());
        stopButton.setOnAction(e -> handleStopButton());
        clearQueueButton.setOnAction(e -> handleClearQueue());

        createRoomButton.setOnAction(e -> handleCreateRoom(roomNameField,
                roomPasswordField));
        joinRoomButton.setOnAction(e -> handleJoinRoom(roomNameField,
                roomPasswordField));
        leaveRoomButton.setOnAction(e -> handleLeaveRoom());

        messageField.setOnAction(e -> handleSendMessage());
        createPlaylistButton.setOnAction(e -> handleCreatePlaylist(
                playlistNameField));
        deletePlaylistButton.setOnAction(e -> handleDeletePlaylist());
        renamePlaylistButton.setOnAction(e -> handleRenamePlaylist());
        sharePlaylistButton.setOnAction(e -> handleSharePlaylist());
        addTrackToPlaylistButton.setOnAction(e -> handleAddTrackToPlaylist());
        addPlaylistToQueueButton.setOnAction(e -> handleAddPlaylistToQueue());

        playlistListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    playlistTracksView.getItems().clear();
                    if (newVal != null) {
                        playlistTracksView.getItems().addAll(playlists.get(newVal));
                    }
                    // Unselect other lists
                    trackListView.getSelectionModel().clearSelection();
                    queueListView.getSelectionModel().clearSelection();
                });

        trackListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Unselect other lists
            playlistListView.getSelectionModel().clearSelection();
            playlistTracksView.getSelectionModel().clearSelection();
            queueListView.getSelectionModel().clearSelection();
        });

        playlistTracksView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Unselect other lists
            trackListView.getSelectionModel().clearSelection();
            queueListView.getSelectionModel().clearSelection();
        });

        queueListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Unselect other lists
            trackListView.getSelectionModel().clearSelection();
            playlistTracksView.getSelectionModel().clearSelection();
        });

        passLeadershipButton.setOnAction(e -> handlePassLeadership());
        deleteRoomButton.setOnAction(e -> handleDeleteRoom());
        kickUserButton.setOnAction(e -> handleKickUser());
        refreshRoomsButton.setOnAction(e -> sendCommand("GET_ROOMS"));
        refreshTracksButton.setOnAction(e -> sendCommand("GET_TRACKS"));
        roomListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    roomNameField.setText(newVal);
                });
    }

    private void connectToServer(String authCommand, Stage primaryStage,
                                 Label messageLabel) {
        new Thread(() -> {
            try {
                controlSocket = new Socket(SERVER_ADDRESS, CONTROL_PORT);
                out = new PrintWriter(controlSocket.getOutputStream(), true);
                in = new BufferedReader(
                        new InputStreamReader(controlSocket.getInputStream()));

                String response = in.readLine();
                if ("AUTH_REQUEST".equals(response)) {
                    out.println(authCommand);
                    response = in.readLine();
                    if ("AUTH_SUCCESS".equals(response)) {
                        Platform.runLater(() -> {
                            showMainUI(primaryStage);
                            statusLabel.setText("Connected as " + username);
                        });
                        handleServerResponses();
                    } else {
                        Platform.runLater(() -> messageLabel.setText(
                                "Authentication failed"));
                        controlSocket.close();
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> messageLabel.setText(
                        "Connection failed: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    private void handleServerResponses() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    Platform.runLater(() -> processServerMessage(finalLine));
                }
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText(
                        "Disconnected from Server"));
                e.printStackTrace();
            }
        }).start();
    }

    private void processServerMessage(String message) {
        if (message.startsWith("TRACK_LIST")) {
            String track = message.substring("TRACK_LIST ".length());
            if (!trackList.contains(track)) {
                trackList.add(track);
                trackListView.getItems().add(track);
            }
        } else if (message.startsWith("NEW_TRACK")) {
            String track = message.substring("NEW_TRACK ".length());
            showAlert("New track added: " + track);
        } else if (message.startsWith("PLAYLIST")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String playlistName = parts[1];
                String tracks = parts[2];
                List<String> trackNames = Arrays.asList(tracks.split(","));
                playlists.put(playlistName, new ArrayList<>(trackNames));
                if (!playlistListView.getItems().contains(playlistName)) {
                    playlistListView.getItems().add(playlistName);
                }
            }
        } else if (message.startsWith("PLAY_TRACK")) {
            String track = message.substring("PLAY_TRACK ".length());
            if (currentTrack != null && player != null) {
                player.close(); // Stop previous track
            }
            if (currentTrack != null && !currentTrack.equals(track)) {
                previousTracks.push(currentTrack);
            }
            currentTrack = track;
            isPlaying = true;
            isPaused = false;
            updateQueueList();
            updateUpNextLabel();
            statusLabel.setText("Playing: " + track);
            new Thread(() -> playAudioStream(track)).start();
        } else if (message.equals("PAUSE")) {
            statusLabel.setText("Paused");
            if (player != null) {
                isPaused = true;
                player.stop();
            }
        } else if (message.equals("RESUME")) {
            statusLabel.setText("Resumed");
            new Thread(() -> playAudioStream(currentTrack)).start();
        } else if (message.equals("STOP")) {
            statusLabel.setText("Stopped");
            isPlaying = false;
            isPaused = false;
            pausedOnFrame = 0;
            if (player != null) {
                player.close();
            }
        } else if (message.startsWith("CHAT")) {
            String chatMessage = message.substring("CHAT ".length());
            addChatMessage(chatMessage);
        } else if (message.startsWith("ROOM_LIST")) {
            String[] rooms = message.substring("ROOM_LIST ".length()).split(",");
            roomList.setAll(rooms);
        } else if (message.startsWith("ROOM_MEMBERS")) {
            String[] members = message.substring("ROOM_MEMBERS ".length())
                    .split(",");
            roomMembersView.getItems().setAll(members);
        } else if (message.startsWith("ROOM_CREATED")) {
            String roomName = message.substring("ROOM_CREATED ".length());
            if (!roomList.contains(roomName)) {
                roomList.add(roomName);
            }
            statusLabel.setText("Room created: " + roomName);
            inRoom = true;
            isRoomLeader = true;
            roomHeadLabel.setText("Room Leader: " + username);
            passLeadershipButton.setVisible(true);
            deleteRoomButton.setVisible(true);
            kickUserButton.setVisible(true);
            roomPasswordLabel.setText("Room Password: " + roomPasswordField.getText());
            currentRoomName = roomName;
            chatListView.getItems().clear();
            if (chatHistories.containsKey(currentRoomName)) {
                for (String msg : chatHistories.get(currentRoomName)) {
                    addChatMessage(msg);
                }
            }
            // Sync the queue
            sendCommand("UPDATE_QUEUE " + serializeQueue());
        } else if (message.startsWith("JOINED_ROOM")) {
            String[] parts = message.split(" ", 3);
            String roomName = parts[1];
            String roomPassword = parts.length == 3 ? parts[2] : "";
            statusLabel.setText("Joined room: " + roomName);
            inRoom = true;
            isRoomLeader = false;
            passLeadershipButton.setVisible(false);
            deleteRoomButton.setVisible(false);
            kickUserButton.setVisible(false);
            roomMembersView.getItems().clear();
            roomPasswordLabel.setText("Room Password: " + roomPassword);
            sendCommand("GET_ROOM_MEMBERS " + roomName);
            currentRoomName = roomName;
            chatListView.getItems().clear();
            if (chatHistories.containsKey(currentRoomName)) {
                for (String msg : chatHistories.get(currentRoomName)) {
                    addChatMessage(msg);
                }
            }
        } else if (message.startsWith("LEFT_ROOM")) {
            String roomName = message.substring("LEFT_ROOM ".length());
            statusLabel.setText("Left room: " + roomName);
            inRoom = false;
            isRoomLeader = false;
            roomHeadLabel.setText("Room Leader: N/A");
            passLeadershipButton.setVisible(false);
            deleteRoomButton.setVisible(false);
            kickUserButton.setVisible(false);
            roomMembersView.getItems().clear();
            roomPasswordLabel.setText("Room Password: ");
            currentRoomName = "";
            chatListView.getItems().clear();

            // Restore personal queue
            songQueue.clear();
            songQueue.addAll(personalQueue);
            updateQueueList();
            updateUpNextLabel();
            enableQueueControls(true);
        } else if (message.startsWith("PLAYLIST_CREATED")) {
            String playlistName = message.substring(
                    "PLAYLIST_CREATED ".length());
            if (!playlists.containsKey(playlistName)) {
                playlists.put(playlistName, new ArrayList<>());
                if (!playlistListView.getItems().contains(playlistName)) {
                    playlistListView.getItems().add(playlistName);
                }
            }
        } else if (message.startsWith("PLAYLIST_DELETED")) {
            String playlistName = message.substring("PLAYLIST_DELETED ".length());
            playlists.remove(playlistName);
            playlistListView.getItems().remove(playlistName);
            if (playlistName.equals(playlistListView.getSelectionModel().getSelectedItem())) {
                playlistTracksView.getItems().clear();
            }
        } else if (message.startsWith("PLAYLIST_RENAMED")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String oldName = parts[1];
                String newName = parts[2];
                List<String> tracks = playlists.remove(oldName);
                playlists.put(newName, tracks);
                int index = playlistListView.getItems().indexOf(oldName);
                if (index != -1) {
                    playlistListView.getItems().set(index, newName);
                }
                if (playlistListView.getSelectionModel().getSelectedItem() != null &&
                        playlistListView.getSelectionModel().getSelectedItem().equals(oldName)) {
                    playlistListView.getSelectionModel().select(newName);
                }
            }
        } else if (message.startsWith("TRACK_ADDED_TO_PLAYLIST")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String playlistName = parts[1];
                String trackName = parts[2];
                if (playlists.containsKey(playlistName)) {
                    if (!playlists.get(playlistName).contains(trackName)) {
                        playlists.get(playlistName).add(trackName);
                    }
                    if (playlistListView.getSelectionModel()
                            .getSelectedItem() != null &&
                            playlistListView.getSelectionModel().getSelectedItem()
                                    .equals(playlistName)) {
                        if (!playlistTracksView.getItems().contains(trackName)) {
                            playlistTracksView.getItems().add(trackName);
                        }
                    }
                }
            }
        } else if (message.startsWith("TRACK_REMOVED_FROM_PLAYLIST")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String playlistName = parts[1];
                String trackName = parts[2];
                if (playlists.containsKey(playlistName)) {
                    playlists.get(playlistName).remove(trackName);
                    if (playlistListView.getSelectionModel()
                            .getSelectedItem() != null &&
                            playlistListView.getSelectionModel().getSelectedItem()
                                    .equals(playlistName)) {
                        playlistTracksView.getItems().remove(trackName);
                    }
                }
            }
        } else if (message.startsWith("SHARE_PLAYLIST_REQUEST")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String senderUsername = parts[1];
                String playlistName = parts[2];
                handleSharePlaylistRequest(senderUsername, playlistName);
            }
        } else if (message.startsWith("SHARE_PLAYLIST")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String playlistName = parts[1];
                String tracks = parts[2];
                List<String> trackNames = Arrays.asList(tracks.split(","));
                playlists.put(playlistName, new ArrayList<>(trackNames));
                if (!playlistListView.getItems().contains(playlistName)) {
                    playlistListView.getItems().add(playlistName);
                }
                showAlert("You have received a new playlist: " + playlistName);
            }
        } else if (message.startsWith("UPDATE_QUEUE")) {
            String queueData = message.substring("UPDATE_QUEUE ".length());
            songQueue.clear();
            if (!queueData.isEmpty()) {
                String[] tracks = queueData.split(",");
                Collections.addAll(songQueue, tracks);
            }
            updateQueueList();
            updateUpNextLabel();
        } else if (message.startsWith("JOIN_REQUEST")) {
            String requester = message.substring("JOIN_REQUEST ".length());
            handleJoinRequest(requester);
        } else if (message.startsWith("NEW_LEADER")) {
            String newLeader = message.substring("NEW_LEADER ".length());
            statusLabel.setText("New leader: " + newLeader);
            roomHeadLabel.setText("Room Leader: " + newLeader);
            updateLeadership(newLeader);
        } else if (message.startsWith("LEADERSHIP_PASSED")) {
            String newLeader = message.substring(
                    "LEADERSHIP_PASSED ".length());
            statusLabel.setText("Leadership passed to: " + newLeader);
            roomHeadLabel.setText("Room Leader: " + newLeader);
            updateLeadership(newLeader);
        } else if (message.startsWith("YOU_ARE_LEADER")) {
            String roomName = message.substring("YOU_ARE_LEADER ".length());
            statusLabel.setText("You are now the leader of room: " + roomName);
            roomHeadLabel.setText("Room Leader: " + username);
            isRoomLeader = true;
            passLeadershipButton.setVisible(true);
            deleteRoomButton.setVisible(true);
            kickUserButton.setVisible(true);
            enableQueueControls(true);
            // Sync the queue
            sendCommand("UPDATE_QUEUE " + serializeQueue());
        } else if (message.startsWith("ROOM_HEAD")) {
            String leaderName = message.substring("ROOM_HEAD ".length());
            roomHeadLabel.setText("Room Leader: " + leaderName);
            updateLeadership(leaderName);
        } else if (message.equals("CLEAR_ROOM_MEMBERS")) {
            roomMembersView.getItems().clear();
        } else if (message.startsWith("USER_JOINED")) {
            String userName = message.substring("USER_JOINED ".length());
            if (!roomMembersView.getItems().contains(userName)) {
                roomMembersView.getItems().add(userName);
            }
        } else if (message.startsWith("USER_LEFT")) {
            String userName = message.substring("USER_LEFT ".length());
            roomMembersView.getItems().remove(userName);
        } else if (message.startsWith("UP_NEXT")) {
            updateUpNextLabel();
        } else if (message.startsWith("ERROR")) {
            showAlert(message.substring("ERROR ".length()));
        }
    }

    private void playAudioStream(String trackName) {
        try {
            // Connect to audio streaming port
            audioSocket = new Socket(SERVER_ADDRESS, AUDIO_PORT);
            OutputStream outToServer = audioSocket.getOutputStream();
            PrintWriter audioOut = new PrintWriter(outToServer, true);
            InputStream inputStream = audioSocket.getInputStream();
            BufferedInputStream bufferedIn = new BufferedInputStream(
                    inputStream);

            // Send the track name to the server
            audioOut.println(username + ":" + trackName + ":" + pausedOnFrame);

            player = new AdvancedPlayer(bufferedIn);
            player.setPlayBackListener(new PlaybackListener() {
                @Override
                public void playbackFinished(PlaybackEvent evt) {
                    if (!isPaused) {
                        if (isLooping) {
                            pausedOnFrame = 0;
                            Platform.runLater(() -> playAudioStream(currentTrack));
                        } else {
                            isPlaying = false;
                            pausedOnFrame = 0;
                            Platform.runLater(() -> {
                                if (!songQueue.isEmpty()) {
                                    String nextTrack = songQueue.poll();
                                    if (inRoom && !isRoomLeader) {
                                        // Do nothing, wait for leader's command
                                    } else {
                                        sendCommand("PLAY_TRACK " + nextTrack);
                                    }
                                    updateQueueList();
                                    updateUpNextLabel();
                                }
                            });
                        }
                    } else {
                        pausedOnFrame = evt.getFrame();
                    }
                }
            });
            player.play(pausedOnFrame, Integer.MAX_VALUE);

            audioSocket.close();
        } catch (Exception e) {
            System.err.println("Error playing audio: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> statusLabel.setText("Error playing audio"));
        } finally {
            if (player != null) {
                player.close();
            }
            if (!isPaused) {
                isPlaying = false;
                pausedOnFrame = 0;
            }
        }
    }

    private void sendCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addChatMessage(String message) {
        if (currentRoomName.isEmpty()) return;

        if (!chatHistories.containsKey(currentRoomName)) {
            chatHistories.put(currentRoomName, new ArrayList<>());
        }
        chatHistories.get(currentRoomName).add(message);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        HBox messageBox = new HBox();
        messageBox.setPadding(new Insets(5));

        if (message.startsWith(username + ":")) {
            messageLabel.setStyle("-fx-background-color: #6a1b9a; -fx-text-fill: white; -fx-padding: 10px; -fx-background-radius: 10;");
            messageBox.setAlignment(Pos.TOP_LEFT);
            messageBox.getChildren().add(messageLabel);
        } else {
            messageLabel.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-padding: 10px; -fx-background-radius: 10;");
            messageBox.setAlignment(Pos.TOP_RIGHT);
            messageBox.getChildren().add(messageLabel);
        }

        chatListView.getItems().add(messageBox);
    }

    // Event handler methods
    private void handlePlayButton() {
        if (!isPlaying) {
            if (!songQueue.isEmpty()) {
                String nextTrack = songQueue.peek();
                sendCommand("PLAY_TRACK " + nextTrack);
                updateQueueList();
                updateUpNextLabel();
            } else {
                showAlert("Queue is empty.");
            }
        } else {
            sendCommand("STOP");
            if (!songQueue.isEmpty()) {
                String nextTrack = songQueue.peek();
                sendCommand("PLAY_TRACK " + nextTrack);
                updateQueueList();
                updateUpNextLabel();
            } else {
                showAlert("Queue is empty.");
            }
        }
    }

    private void handlePlaySelectedTrackButton() {
        String selectedTrack = null;

        if (!trackListView.getSelectionModel().isEmpty()) {
            selectedTrack = trackListView.getSelectionModel().getSelectedItem();
        } else if (!playlistTracksView.getSelectionModel().isEmpty()) {
            selectedTrack = playlistTracksView.getSelectionModel().getSelectedItem();
        } else if (!queueListView.getSelectionModel().isEmpty()) {
            selectedTrack = queueListView.getSelectionModel().getSelectedItem();
        }

        if (selectedTrack != null) {
            if (inRoom && !isRoomLeader) {
                showAlert("Only the room leader can play or stop the song in a room.");
            } else {
                // Check if selectedTrack is at the top of the queue
                if (!songQueue.isEmpty() && songQueue.peek().equals(selectedTrack)) {
                    // Do nothing, track is already at the top
                } else if (songQueue.contains(selectedTrack)) {
                    // Move it to the top
                    songQueue.remove(selectedTrack);
                    songQueue.addFirst(selectedTrack);
                } else {
                    // Add to the top
                    songQueue.addFirst(selectedTrack);
                }
                updateQueueList();
                sendCommand("STOP");
                sendCommand("PLAY_TRACK " + selectedTrack);
                updateUpNextLabel();
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            }
        }
    }

    private void handleNextTrackButton() {
        if (inRoom && !isRoomLeader) {
            showAlert("Only the room leader can change tracks in a room.");
        } else {
            if (!isPlaying && !songQueue.isEmpty()) {
                String nextTrack = songQueue.poll();
                sendCommand("PLAY_TRACK " + nextTrack);
                updateQueueList();
                updateUpNextLabel();
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            } else if (isPlaying) {
                sendCommand("STOP");
                if (!songQueue.isEmpty()) {
                    String nextTrack = songQueue.poll();
                    sendCommand("PLAY_TRACK " + nextTrack);
                    updateQueueList();
                    updateUpNextLabel();
                    if (inRoom && isRoomLeader) {
                        sendCommand("UPDATE_QUEUE " + serializeQueue());
                    }
                } else {
                    showAlert("Queue is empty.");
                }
            } else {
                showAlert("Queue is empty.");
            }
        }
    }

    private void handlePreviousTrackButton() {
        if (inRoom && !isRoomLeader) {
            showAlert("Only the room leader can change tracks in a room.");
        } else {
            if (!previousTracks.isEmpty()) {
                String prevTrack = previousTracks.pop();
                if (currentTrack != null && !currentTrack.equals(prevTrack)) {
                    songQueue.addFirst(currentTrack);
                }
                sendCommand("STOP");
                sendCommand("PLAY_TRACK " + prevTrack);
                updateQueueList();
                updateUpNextLabel();
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            } else {
                showAlert("No previous track available.");
            }
        }
    }

    private void handleAddToQueue() {
        String selectedTrack = null;

        if (!trackListView.getSelectionModel().isEmpty()) {
            selectedTrack = trackListView.getSelectionModel().getSelectedItem();
        } else if (!playlistTracksView.getSelectionModel().isEmpty()) {
            selectedTrack = playlistTracksView.getSelectionModel().getSelectedItem();
        }

        if (selectedTrack != null) {
            if (inRoom && !isRoomLeader) {
                showAlert("Only the room leader can modify the queue in a room.");
            } else {
                songQueue.offer(selectedTrack);
                updateQueueList();
                updateUpNextLabel();
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            }
        }
    }

    private void handleRemoveFromQueue() {
        String selectedTrack = queueListView.getSelectionModel()
                .getSelectedItem();
        if (selectedTrack != null) {
            if (inRoom && !isRoomLeader) {
                showAlert("Only the room leader can modify the queue in a room.");
            } else {
                songQueue.remove(selectedTrack);
                updateQueueList();
                updateUpNextLabel();
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            }
        }
    }

    private void handleAddPlaylistToQueue() {
        String selectedPlaylist = playlistListView.getSelectionModel()
                .getSelectedItem();
        if (selectedPlaylist != null) {
            List<String> tracks = playlists.get(selectedPlaylist);
            if (tracks != null && !tracks.isEmpty()) {
                if (inRoom && !isRoomLeader) {
                    showAlert("Only the room leader can modify the queue in a room.");
                } else {
                    songQueue.addAll(tracks);
                    updateQueueList();
                    updateUpNextLabel();
                    if (inRoom && isRoomLeader) {
                        sendCommand("UPDATE_QUEUE " + serializeQueue());
                    }
                }
            }
        }
    }

    private void handleCreateRoom(TextField roomNameField,
                                  TextField roomPasswordField) {
        String roomName = roomNameField.getText();
        String password = roomPasswordField.getText();
        if (!roomName.isEmpty()) {
            sendCommand("CREATE_ROOM " + roomName + " " + password);
        }
    }

    private void handleJoinRoom(TextField roomNameField,
                                TextField roomPasswordField) {
        String roomName = roomNameField.getText();
        String password = roomPasswordField.getText();
        if (!roomName.isEmpty()) {
            sendCommand("JOIN_ROOM " + roomName + " " + password);
        }
    }

    private void handleLeaveRoom() {
        sendCommand("LEAVE_ROOM");
    }

    private void handleSendMessage() {
        String message = messageField.getText();
        if (!message.isEmpty()) {
            sendCommand("SEND_MESSAGE " + message);
            messageField.clear();
        }
    }

    private void handleCreatePlaylist(TextField playlistNameField) {
        String playlistName = playlistNameField.getText();
        if (!playlistName.isEmpty()) {
            if (playlists.containsKey(playlistName)) {
                showAlert("Playlist already exists.");
                return;
            }
            sendCommand("CREATE_PLAYLIST " + playlistName);
            playlistNameField.clear();
            // Update playlistListView immediately
            playlists.put(playlistName, new ArrayList<>());
            if (!playlistListView.getItems().contains(playlistName)) {
                playlistListView.getItems().add(playlistName);
            }
        }
    }

    private void handleDeletePlaylist() {
        String selectedPlaylist = playlistListView.getSelectionModel()
                .getSelectedItem();
        if (selectedPlaylist != null) {
            sendCommand("DELETE_PLAYLIST " + selectedPlaylist);
            // Update playlistListView immediately
            playlists.remove(selectedPlaylist);
            playlistListView.getItems().remove(selectedPlaylist);
            playlistTracksView.getItems().clear();
        }
    }

    private void handleRenamePlaylist() {
        String selectedPlaylist = playlistListView.getSelectionModel()
                .getSelectedItem();
        if (selectedPlaylist != null) {
            TextInputDialog dialog = new TextInputDialog(selectedPlaylist);
            dialog.setTitle("Rename Playlist");
            dialog.setHeaderText("Rename Playlist");
            dialog.setContentText("Enter new name:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newName -> {
                if (!newName.isEmpty()) {
                    sendCommand("RENAME_PLAYLIST " + selectedPlaylist + " " + newName);
                    // Update immediately
                    List<String> tracks = playlists.remove(selectedPlaylist);
                    playlists.put(newName, tracks);
                    int index = playlistListView.getItems().indexOf(selectedPlaylist);
                    if (index != -1) {
                        playlistListView.getItems().set(index, newName);
                    }
                    if (playlistListView.getSelectionModel().getSelectedItem() != null &&
                            playlistListView.getSelectionModel().getSelectedItem().equals(selectedPlaylist)) {
                        playlistListView.getSelectionModel().select(newName);
                    }
                }
            });
        }
    }

    private void handleSharePlaylist() {
        if (playlists.isEmpty()) {
            showAlert("No playlists available to share.");
            return;
        }
        if (!inRoom) {
            showAlert("You must be in a room to share a playlist.");
            return;
        }
        List<String> playlistNames = new ArrayList<>(playlists.keySet());
        ChoiceDialog<String> playlistDialog = new ChoiceDialog<>(playlistNames.get(0), playlistNames);
        playlistDialog.setTitle("Share Playlist");
        playlistDialog.setHeaderText("Select Playlist to Share");
        playlistDialog.setContentText("Choose a playlist:");

        Optional<String> playlistResult = playlistDialog.showAndWait();
        if (playlistResult.isPresent()) {
            String selectedPlaylist = playlistResult.get();
            ObservableList<String> roomMembers = roomMembersView.getItems();
            List<String> otherMembers = new ArrayList<>(roomMembers);
            otherMembers.remove(username);

            if (otherMembers.isEmpty()) {
                showAlert("No other members in the room to share with.");
                return;
            }

            ChoiceDialog<String> userDialog = new ChoiceDialog<>(otherMembers.get(0), otherMembers);
            userDialog.setTitle("Share Playlist");
            userDialog.setHeaderText("Select User to Share With");
            userDialog.setContentText("Choose a user:");

            Optional<String> userResult = userDialog.showAndWait();
            if (userResult.isPresent()) {
                String selectedUser = userResult.get();
                sendCommand("SHARE_PLAYLIST_REQUEST " + selectedUser + " " + selectedPlaylist);
                showAlert("Playlist share request sent to " + selectedUser);
            }
        }
    }

    private void handleSharePlaylistRequest(String senderUsername, String playlistName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Playlist Share Request");
        alert.setHeaderText("Playlist Share Request");
        alert.setContentText(senderUsername + " wants to share playlist '" + playlistName + "' with you. Accept?");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            sendCommand("ACCEPT_PLAYLIST " + senderUsername + " " + playlistName);
        } else {
            sendCommand("DECLINE_PLAYLIST " + senderUsername + " " + playlistName);
        }
    }

    private void handleAddTrackToPlaylist() {
        String selectedTrack = null;

        if (!trackListView.getSelectionModel().isEmpty()) {
            selectedTrack = trackListView.getSelectionModel().getSelectedItem();
        } else if (!playlistTracksView.getSelectionModel().isEmpty()) {
            selectedTrack = playlistTracksView.getSelectionModel().getSelectedItem();
        }

        if (selectedTrack != null) {
            if (playlists.isEmpty()) {
                showAlert("No playlists available. Please create a playlist first.");
                return;
            }
            List<String> playlistNames = new ArrayList<>(playlists.keySet());
            ChoiceDialog<String> dialog = new ChoiceDialog<>(playlistNames.get(0), playlistNames);
            dialog.setTitle("Select Playlist");
            dialog.setHeaderText("Add Track to Playlist");
            dialog.setContentText("Choose a playlist:");
            Optional<String> result = dialog.showAndWait();
            final String trackToAdd = selectedTrack; // Make it final for use in lambda
            result.ifPresent(playlistName -> {
                sendCommand("ADD_TO_PLAYLIST " + playlistName + " " + trackToAdd);
            });
        } else {
            showAlert("Please select a track to add to a playlist.");
        }
    }

    private void handleDeleteFromPlaylist() {
        String selectedPlaylist = playlistListView.getSelectionModel()
                .getSelectedItem();
        String selectedTrack = playlistTracksView.getSelectionModel()
                .getSelectedItem();
        if (selectedPlaylist != null && selectedTrack != null) {
            sendCommand("REMOVE_FROM_PLAYLIST " + selectedPlaylist + " "
                    + selectedTrack);
        }
    }

    private void handlePassLeadership() {
        if (isRoomLeader) {
            String selectedUser = roomMembersView.getSelectionModel()
                    .getSelectedItem();
            if (selectedUser != null && !selectedUser.equals(username)) {
                sendCommand("PASS_LEADERSHIP " + selectedUser);
            } else {
                showAlert("Select a user to pass leadership to.");
            }
        }
    }

    private void handleDeleteRoom() {
        if (isRoomLeader) {
            sendCommand("DELETE_ROOM");
        }
    }

    private void handleKickUser() {
        if (isRoomLeader) {
            String selectedUser = roomMembersView.getSelectionModel()
                    .getSelectedItem();
            if (selectedUser != null && !selectedUser.equals(username)) {
                sendCommand("KICK_USER " + selectedUser);
            } else {
                showAlert("Select a user to kick.");
            }
        }
    }

    private void handlePauseButton() {
        if (isPlaying) {
            if (inRoom) {
                sendCommand("PAUSE_OR_RESUME");
            } else {
                // Individual playback
                if (!isPaused) {
                    sendCommand("PAUSE");
                    isPaused = true;
                } else {
                    sendCommand("RESUME");
                    isPaused = false;
                }
            }
        }
    }

    private void handleStopButton() {
        if (isPlaying) {
            if (inRoom && !isRoomLeader) {
                showAlert("Only the room leader can stop the song in a room.");
            } else {
                sendCommand("STOP");
                isPlaying = false;
                isPaused = false;
                pausedOnFrame = 0;
                if (player != null) {
                    player.close();
                }
                if (inRoom && isRoomLeader) {
                    sendCommand("UPDATE_QUEUE " + serializeQueue());
                }
            }
        }
    }

    private void handleClearQueue() {
        if (inRoom && !isRoomLeader) {
            showAlert("Only the room leader can modify the queue in a room.");
        } else {
            songQueue.clear();
            updateQueueList();
            updateUpNextLabel();
            if (inRoom && isRoomLeader) {
                sendCommand("UPDATE_QUEUE " + serializeQueue());
            }
        }
    }

    private void handleJoinRequest(String requester) {
        // Handle join request if implemented
    }

    private void updateLeadership(String leaderName) {
        if (leaderName.equals(username)) {
            isRoomLeader = true;
            passLeadershipButton.setVisible(true);
            deleteRoomButton.setVisible(true);
            kickUserButton.setVisible(true);
            enableQueueControls(true);
        } else {
            isRoomLeader = false;
            passLeadershipButton.setVisible(false);
            deleteRoomButton.setVisible(false);
            kickUserButton.setVisible(false);
            enableQueueControls(false);
        }
    }

    private void filterRoomList(String filter) {
        sendCommand("GET_ROOMS");
        if (filter.isEmpty()) {
            roomListView.setItems(roomList);
        } else {
            ObservableList<String> filteredList = FXCollections
                    .observableArrayList();
            for (String room : roomList) {
                if (room.toLowerCase().contains(filter.toLowerCase())) {
                    filteredList.add(room);
                }
            }
            roomListView.setItems(filteredList);
        }
    }

    private void updateUpNextLabel() {
        if (!songQueue.isEmpty()) {
            upNextLabel.setText("Up Next: " + songQueue.peek());
        } else {
            upNextLabel.setText("Up Next: ");
        }
    }

    private void updateQueueList() {
        queueListView.getItems().setAll(songQueue);
    }

    private String serializeQueue() {
        return String.join(",", songQueue);
    }

    private void enableQueueControls(boolean enable) {
        addToQueueButton.setDisable(!enable);
        removeFromQueueButton.setDisable(!enable);
        clearQueueButton.setDisable(!enable);
        addPlaylistToQueueButton.setDisable(!enable);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
