import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class AudioStreamingServer {
    // Server configurations
    private static final int CONTROL_PORT = 12345;
    private static final int AUDIO_PORT = 12346;
    private static final String MUSIC_FOLDER = "C:/Users/Sam/Music";
    private static final String DB_URL = "jdbc:sqlite:music_app.db";

    private static List<String> trackList = new ArrayList<>();
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();

    private static Connection dbConnection;

    public static void main(String[] args) {
        initDatabase();
        loadTracks();
        watchMusicFolder();
        System.out.println("Audio Streaming Server is running on control port " + CONTROL_PORT + " and audio port " + AUDIO_PORT);

        ExecutorService executor = Executors.newCachedThreadPool();
        startAudioServer(executor);

        try (ServerSocket controlSocket = new ServerSocket(CONTROL_PORT)) {
            while (true) {
                Socket clientControlSocket = controlSocket.accept();
                System.out.println("Client connected: " + clientControlSocket.getRemoteSocketAddress());
                ClientHandler clientHandler = new ClientHandler(clientControlSocket);
                executor.submit(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
            closeDatabase();
        }
    }

    private static void startAudioServer(ExecutorService executor) {
        executor.submit(() -> {
            try (ServerSocket audioServerSocket = new ServerSocket(AUDIO_PORT)) {
                while (true) {
                    Socket clientAudioSocket = audioServerSocket.accept();
                    executor.submit(new AudioStreamingHandler(clientAudioSocket));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void initDatabase() {
        try {
            dbConnection = DriverManager.getConnection(DB_URL);
            Statement stmt = dbConnection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS playlists (username TEXT, playlistName TEXT, tracks TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void closeDatabase() {
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void loadTracks() {
        File folder = new File(MUSIC_FOLDER);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("The folder " + MUSIC_FOLDER + " does not exist or is not a directory.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));

        if (files != null) {
            trackList.clear();
            for (File file : files) {
                trackList.add(file.getName());
                System.out.println("Loaded track: " + file.getName());
            }
        } else {
            System.out.println("No .mp3 files found in " + MUSIC_FOLDER + ".");
        }
    }

    private static void watchMusicFolder() {
        Thread watcherThread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Paths.get(MUSIC_FOLDER).register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                while (true) {
                    WatchKey key = watchService.take();
                    boolean newTrackAdded = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path newFile = (Path) event.context();
                            if (newFile.toString().toLowerCase().endsWith(".mp3")) {
                                trackList.add(newFile.toString());
                                System.out.println("New track detected: " + newFile.toString());
                                newTrackAdded = true;
                            }
                        }
                    }
                    if (newTrackAdded) {
                        broadcastNewTrack();
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private static void broadcastNewTrack() {
        for (ClientHandler client : clients.values()) {
            client.sendMessage("NEW_TRACK");
        }
    }

    static class ClientHandler implements Runnable {
        private Socket controlSocket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private Room currentRoom;
        private Map<String, List<String>> playlists = new HashMap<>();

        public ClientHandler(Socket controlSocket) {
            this.controlSocket = controlSocket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
                out = new PrintWriter(controlSocket.getOutputStream(), true);

                if (!authenticate()) {
                    controlSocket.close();
                    return;
                }

                clients.put(username, this);

                sendTrackList();

                loadPlaylists();
                sendAvailableRooms();

                String command;
                while ((command = in.readLine()) != null) {
                    processCommand(command);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + controlSocket.getRemoteSocketAddress());
            } finally {
                clients.remove(username);
                if (currentRoom != null) {
                    currentRoom.removeMember(this);
                }
                try {
                    if (controlSocket != null && !controlSocket.isClosed()) {
                        controlSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean authenticate() throws IOException {
            out.println("AUTH_REQUEST");
            String response = in.readLine();
            if (response != null && response.startsWith("LOGIN")) {
                String[] parts = response.split(" ");
                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];
                    if (validateLogin(username, password)) {
                        this.username = username;
                        out.println("AUTH_SUCCESS");
                        return true;
                    } else {
                        out.println("AUTH_FAILURE");
                        return false;
                    }
                }
            } else if (response != null && response.startsWith("CREATE_ACCOUNT")) {
                String[] parts = response.split(" ");
                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];
                    if (createAccount(username, password)) {
                        this.username = username;
                        out.println("AUTH_SUCCESS");
                        return true;
                    } else {
                        out.println("AUTH_FAILURE");
                        return false;
                    }
                }
            }
            out.println("AUTH_FAILURE");
            return false;
        }

        private boolean validateLogin(String username, String password) {
            try {
                PreparedStatement stmt = dbConnection.prepareStatement("SELECT password FROM users WHERE username = ?");
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    return storedPassword.equals(password);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        private boolean createAccount(String username, String password) {
            try {
                PreparedStatement stmt = dbConnection.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)");
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                out.println("ERROR Account creation failed: " + e.getMessage());
                return false;
            }
        }

        private void loadPlaylists() {
            try {
                PreparedStatement stmt = dbConnection.prepareStatement("SELECT playlistName, tracks FROM playlists WHERE username = ?");
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String playlistName = rs.getString("playlistName");
                    String tracks = rs.getString("tracks");
                    List<String> trackList = Arrays.asList(tracks.split(","));
                    playlists.put(playlistName, new ArrayList<>(trackList));
                    out.println("PLAYLIST " + playlistName + " " + tracks);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void processCommand(String command) throws IOException {
            if (command.startsWith("CREATE_ROOM")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 2) {
                    String roomName = parts[1];
                    String password = parts.length == 3 ? parts[2] : "";
                    createRoom(roomName, password);
                }
            } else if (command.startsWith("JOIN_ROOM")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 2) {
                    String roomName = parts[1];
                    String password = parts.length == 3 ? parts[2] : "";
                    joinRoom(roomName, password);
                }
            } else if (command.equals("LEAVE_ROOM")) {
                leaveRoom();
            } else if (command.startsWith("SEND_MESSAGE")) {
                String message = command.substring("SEND_MESSAGE ".length());
                if (currentRoom != null) {
                    currentRoom.broadcastMessage("CHAT " + username + ": " + message, null);
                }
            } else if (command.startsWith("PLAY_TRACK")) {
                String trackName = command.substring("PLAY_TRACK ".length()).trim();
                if (currentRoom != null) {
                    if (currentRoom.getLeader() == this) {
                        currentRoom.playTrack(trackName);
                    } else {
                        out.println("ERROR Only the room leader can play or stop the song");
                    }
                } else {
                    // Play track individually
                    out.println("PLAY_TRACK " + trackName);
                }
            } else if (command.equals("PAUSE")) {
                if (currentRoom != null) {
                    currentRoom.pauseTrack();
                } else {
                    // Pause individual playback
                    out.println("PAUSE");
                }
            } else if (command.equals("RESUME")) {
                if (currentRoom != null) {
                    currentRoom.resumeTrack();
                } else {
                    // Resume individual playback
                    out.println("RESUME");
                }
            } else if (command.equals("PAUSE_OR_RESUME")) {
                if (currentRoom != null) {
                    currentRoom.pauseOrResumeTrack();
                }
            } else if (command.equals("STOP")) {
                if (currentRoom != null) {
                    if (currentRoom.getLeader() == this) {
                        currentRoom.stopTrack();
                    } else {
                        out.println("ERROR Only the room leader can stop the song");
                    }
                } else {
                    // Stop individual playback
                    out.println("STOP");
                }
            } else if (command.startsWith("CREATE_PLAYLIST")) {
                String playlistName = command.substring("CREATE_PLAYLIST ".length()).trim();
                createPlaylist(playlistName);
            } else if (command.startsWith("DELETE_PLAYLIST")) {
                String playlistName = command.substring("DELETE_PLAYLIST ".length()).trim();
                deletePlaylist(playlistName);
            } else if (command.startsWith("RENAME_PLAYLIST")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 3) {
                    String oldName = parts[1];
                    String newName = parts[2];
                    renamePlaylist(oldName, newName);
                }
            } else if (command.startsWith("ADD_TO_PLAYLIST")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 3) {
                    String playlistName = parts[1];
                    String trackName = parts[2];
                    addToPlaylist(playlistName, trackName);
                }
            } else if (command.startsWith("REMOVE_FROM_PLAYLIST")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 3) {
                    String playlistName = parts[1];
                    String trackName = parts[2];
                    removeFromPlaylist(playlistName, trackName);
                }
            } else if (command.startsWith("PLAY_PLAYLIST")) {
                String playlistName = command.substring("PLAY_PLAYLIST ".length()).trim();
                if (currentRoom != null) {
                    if (currentRoom.getLeader() == this) {
                        currentRoom.playPlaylist(playlistName);
                    } else {
                        out.println("ERROR Only the room leader can change the song");
                    }
                } else {
                    // Play playlist individually
                    List<String> tracks = playlists.get(playlistName);
                    if (tracks != null && !tracks.isEmpty()) {
                        for (String track : tracks) {
                            out.println("PLAY_TRACK " + track);
                            // Wait or manage playback as needed
                        }
                    } else {
                        out.println("ERROR Playlist is empty or does not exist");
                    }
                }
            } else if (command.equals("NEXT_TRACK")) {
                if (currentRoom != null && currentRoom.getLeader() == this) {
                    currentRoom.nextTrack();
                }
            } else if (command.equals("PREVIOUS_TRACK")) {
                if (currentRoom != null && currentRoom.getLeader() == this) {
                    currentRoom.previousTrack();
                }
            } else if (command.equals("DELETE_ROOM")) {
                if (currentRoom != null && currentRoom.getLeader() == this) {
                    currentRoom.deleteRoom();
                    out.println("LEFT_ROOM " + currentRoom.getRoomName());
                    currentRoom = null;
                    broadcastRoomList();
                } else {
                    out.println("ERROR Not the leader or not in a room");
                }
            } else if (command.startsWith("KICK_USER")) {
                if (currentRoom != null && currentRoom.getLeader() == this) {
                    String[] parts = command.split(" ", 2);
                    if (parts.length >= 2) {
                        String usernameToKick = parts[1];
                        ClientHandler userToKick = clients.get(usernameToKick);
                        if (userToKick != null && currentRoom.isMember(userToKick)) {
                            currentRoom.kickUser(userToKick);
                        } else {
                            out.println("ERROR User not in room");
                        }
                    }
                } else {
                    out.println("ERROR Not the leader or not in a room");
                }
            } else if (command.startsWith("PASS_LEADERSHIP")) {
                if (currentRoom != null && currentRoom.getLeader() == this) {
                    String[] parts = command.split(" ", 2);
                    if (parts.length >= 2) {
                        String newLeaderUsername = parts[1];
                        passLeadership(newLeaderUsername);
                    }
                } else {
                    out.println("ERROR Not the leader or not in a room");
                }
            } else if (command.equals("GET_ROOMS")) {
                sendAvailableRooms();
            } else if (command.equals("GET_TRACKS")) {
                sendTrackList();
            } else if (command.startsWith("GET_ROOM_MEMBERS")) {
                String roomName = command.substring("GET_ROOM_MEMBERS ".length());
                Room room = rooms.get(roomName);
                if (room != null) {
                    room.sendMemberListTo(this);
                }
            }
        }

        private void sendTrackList() {
            out.println("TRACK_LIST_CLEAR");
            for (String track : trackList) {
                out.println("TRACK_LIST " + track);
            }
        }

        private void createRoom(String roomName, String password) {
            if (!rooms.containsKey(roomName)) {
                if (currentRoom != null) {
                    leaveRoom();
                }
                Room room = new Room(roomName, password, this);
                rooms.put(roomName, room);
                currentRoom = room;
                out.println("ROOM_CREATED " + roomName);
                broadcastRoomList();
            } else {
                out.println("ERROR Room already exists");
            }
        }

        private void joinRoom(String roomName, String password) {
            Room room = rooms.get(roomName);
            if (room != null) {
                if (room.getPassword().equals(password)) {
                    if (currentRoom != null && currentRoom != room) {
                        leaveRoom();
                    }
                    if (room.isMember(this)) {
                        out.println("ERROR You are already in this room");
                        return;
                    }
                    room.addMember(this);
                    currentRoom = room;
                    out.println("JOINED_ROOM " + roomName + " " + room.getPassword());
                    out.println("ROOM_HEAD " + room.getLeader().getUsername());
                    room.sendMemberList();
                } else {
                    out.println("ERROR Incorrect password");
                }
            } else {
                out.println("ERROR Room does not exist");
            }
        }

        private void leaveRoom() {
            if (currentRoom != null) {
                currentRoom.removeMember(this);
                out.println("LEFT_ROOM " + currentRoom.getRoomName());
                currentRoom = null;
                broadcastRoomList();
            } else {
                out.println("ERROR Not in a room");
            }
        }

        private void createPlaylist(String playlistName) {
            if (!playlists.containsKey(playlistName)) {
                playlists.put(playlistName, new ArrayList<>());
                savePlaylist(playlistName);
                out.println("PLAYLIST_CREATED " + playlistName);
            } else {
                out.println("ERROR Playlist already exists");
            }
        }

        private void deletePlaylist(String playlistName) {
            if (playlists.containsKey(playlistName)) {
                playlists.remove(playlistName);
                deletePlaylistFromDB(playlistName);
                out.println("PLAYLIST_DELETED " + playlistName);
            } else {
                out.println("ERROR Playlist does not exist");
            }
        }

        private void renamePlaylist(String oldName, String newName) {
            if (playlists.containsKey(oldName)) {
                if (playlists.containsKey(newName)) {
                    out.println("ERROR A playlist with the new name already exists");
                    return;
                }
                List<String> tracks = playlists.remove(oldName);
                playlists.put(newName, tracks);
                renamePlaylistInDB(oldName, newName);
                out.println("PLAYLIST_RENAMED " + oldName + " " + newName);
            } else {
                out.println("ERROR Playlist does not exist");
            }
        }

        private void renamePlaylistInDB(String oldName, String newName) {
            try {
                PreparedStatement stmt = dbConnection.prepareStatement(
                        "UPDATE playlists SET playlistName = ? WHERE username = ? AND playlistName = ?");
                stmt.setString(1, newName);
                stmt.setString(2, username);
                stmt.setString(3, oldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void deletePlaylistFromDB(String playlistName) {
            try {
                PreparedStatement stmt = dbConnection.prepareStatement(
                        "DELETE FROM playlists WHERE username = ? AND playlistName = ?");
                stmt.setString(1, username);
                stmt.setString(2, playlistName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void addToPlaylist(String playlistName, String trackName) {
            List<String> playlist = playlists.get(playlistName);
            if (playlist != null) {
                if (playlist.contains(trackName)) {
                    out.println("ERROR Track already exists in the playlist");
                    return;
                }
                playlist.add(trackName);
                savePlaylist(playlistName);
                out.println("TRACK_ADDED_TO_PLAYLIST " + playlistName + " " + trackName);
            } else {
                out.println("ERROR Playlist does not exist");
            }
        }

        private void removeFromPlaylist(String playlistName, String trackName) {
            List<String> playlist = playlists.get(playlistName);
            if (playlist != null) {
                if (playlist.contains(trackName)) {
                    playlist.remove(trackName);
                    savePlaylist(playlistName);
                    out.println("TRACK_REMOVED_FROM_PLAYLIST " + playlistName + " " + trackName);
                } else {
                    out.println("ERROR Track not in the playlist");
                }
            } else {
                out.println("ERROR Playlist does not exist");
            }
        }

        private void savePlaylist(String playlistName) {
            try {
                String tracks = String.join(",", playlists.get(playlistName));
                PreparedStatement stmt = dbConnection.prepareStatement(
                        "INSERT OR REPLACE INTO playlists (username, playlistName, tracks) VALUES (?, ?, ?)");
                stmt.setString(1, username);
                stmt.setString(2, playlistName);
                stmt.setString(3, tracks);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void sendAvailableRooms() {
            out.println("ROOM_LIST " + String.join(",", rooms.keySet()));
        }

        private void broadcastRoomList() {
            for (ClientHandler client : clients.values()) {
                client.sendAvailableRooms();
            }
        }

        private void passLeadership(String newLeaderUsername) {
            ClientHandler newLeader = clients.get(newLeaderUsername);
            if (newLeader != null && currentRoom.isMember(newLeader)) {
                currentRoom.setLeader(newLeader);
                out.println("LEADERSHIP_PASSED " + newLeaderUsername);
                newLeader.out.println("YOU_ARE_LEADER " + currentRoom.getRoomName());
                currentRoom.broadcastRoomHead();
            } else {
                out.println("ERROR User not in room");
            }
        }

        public String getUsername() {
            return username;
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }

    static class Room {
        private String roomName;
        private String password;
        private ClientHandler leader;
        private List<ClientHandler> members = new ArrayList<>();
        private String currentTrack;
        private Queue<String> songQueue = new LinkedList<>();
        private boolean isPaused = false;
        private List<String> playlistTracks = new ArrayList<>();
        private int currentTrackIndex = -1;

        public Room(String roomName, String password, ClientHandler leader) {
            this.roomName = roomName;
            this.password = password;
            this.leader = leader;
            addMember(leader);
        }

        public String getRoomName() {
            return roomName;
        }

        public String getPassword() {
            return password;
        }

        public ClientHandler getLeader() {
            return leader;
        }

        public void setLeader(ClientHandler leader) {
            this.leader = leader;
            broadcastMessage("NEW_LEADER " + leader.getUsername(), null);
            broadcastRoomHead();
        }

        public void addMember(ClientHandler member) {
            if (!members.contains(member)) {
                members.add(member);
                broadcastMessage("USER_JOINED " + member.getUsername(), null);
                member.sendMessage("ROOM_HEAD " + leader.getUsername());
                sendMemberList();
                if (currentTrack != null) {
                    member.sendMessage("PLAY_TRACK " + currentTrack);
                }
            }
        }

        public void removeMember(ClientHandler member) {
            members.remove(member);
            broadcastMessage("USER_LEFT " + member.getUsername(), null);
            if (leader == member) {
                if (!members.isEmpty()) {
                    setLeader(members.get(0));
                } else {
                    deleteRoom();
                    return;
                }
            }
            sendMemberList();
        }

        public boolean isMember(ClientHandler member) {
            return members.contains(member);
        }

        public void broadcastMessage(String message, ClientHandler sender) {
            for (ClientHandler member : members) {
                if (sender == null || member != sender) {
                    member.sendMessage(message);
                }
            }
        }

        public void playTrack(String trackName) {
            currentTrack = trackName;
            isPaused = false;
            for (ClientHandler member : members) {
                member.sendMessage("PLAY_TRACK " + trackName);
            }
        }

        public void pauseTrack() {
            isPaused = true;
            for (ClientHandler member : members) {
                member.sendMessage("PAUSE");
            }
        }

        public void resumeTrack() {
            isPaused = false;
            for (ClientHandler member : members) {
                member.sendMessage("RESUME");
            }
        }

        public void pauseOrResumeTrack() {
            if (isPaused) {
                resumeTrack();
            } else {
                pauseTrack();
            }
        }

        public void stopTrack() {
            currentTrack = null;
            isPaused = false;
            for (ClientHandler member : members) {
                member.sendMessage("STOP");
            }
        }

        public void deleteRoom() {
            stopTrack();
            for (ClientHandler member : new ArrayList<>(members)) {
                member.currentRoom = null;
                member.sendMessage("LEFT_ROOM " + roomName);
            }
            rooms.remove(roomName);
            for (ClientHandler client : clients.values()) {
                client.sendAvailableRooms();
            }
        }

        public void kickUser(ClientHandler user) {
            if (isMember(user)) {
                removeMember(user);
                user.currentRoom = null;
                user.sendMessage("LEFT_ROOM " + roomName);
            }
        }

        public void broadcastRoomHead() {
            for (ClientHandler member : members) {
                member.sendMessage("ROOM_HEAD " + leader.getUsername());
            }
        }

        public void sendMemberList() {
            for (ClientHandler member : members) {
                member.sendMessage("ROOM_MEMBERS " + getMemberUsernames());
            }
        }

        public void sendMemberListTo(ClientHandler client) {
            client.sendMessage("ROOM_MEMBERS " + getMemberUsernames());
        }

        private String getMemberUsernames() {
            List<String> usernames = new ArrayList<>();
            for (ClientHandler member : members) {
                usernames.add(member.getUsername());
            }
            return String.join(",", usernames);
        }

        public void playPlaylist(String playlistName) {
            List<String> tracks = leader.playlists.get(playlistName);
            if (tracks != null && !tracks.isEmpty()) {
                songQueue.clear();
                songQueue.addAll(tracks);
                playNextInQueue();
            } else {
                leader.out.println("ERROR Playlist is empty or does not exist");
            }
        }

        private void playNextInQueue() {
            if (!songQueue.isEmpty()) {
                String nextTrack = songQueue.poll();
                currentTrack = nextTrack;
                broadcastMessage("PLAY_TRACK " + nextTrack, null);
                broadcastMessage("UP_NEXT " + (songQueue.peek() != null ? songQueue.peek() : ""), null);
            } else {
                currentTrack = null;
                broadcastMessage("UP_NEXT ", null);
            }
        }

        public void nextTrack() {
            playNextInQueue();
        }

        public void previousTrack() {
            // Implement previous track functionality if desired
        }
    }

    static class AudioStreamingHandler implements Runnable {
        private Socket audioSocket;

        public AudioStreamingHandler(Socket audioSocket) {
            this.audioSocket = audioSocket;
        }

        @Override
        public void run() {
            try {
                BufferedReader audioIn = new BufferedReader(new InputStreamReader(audioSocket.getInputStream()));
                String request = audioIn.readLine();
                String[] parts = request.split(":");
                String username = parts[0];
                String trackName = parts[1];
                int startFrame = Integer.parseInt(parts[2]);

                ClientHandler client = clients.get(username);
                if (client == null) {
                    audioSocket.close();
                    return;
                }

                OutputStream audioOutStream = audioSocket.getOutputStream();
                File audioFile = new File(MUSIC_FOLDER, trackName);
                if (!audioFile.exists()) {
                    client.sendMessage("ERROR Track not found");
                    audioSocket.close();
                    return;
                }

                FileInputStream fis = new FileInputStream(audioFile);
                fis.skip(startFrame); // Simplified; in practice, you need to handle MP3 frame skipping properly
                byte[] buffer = new byte[4096];
                int bytesRead;
                try {
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        audioOutStream.write(buffer, 0, bytesRead);
                        audioOutStream.flush();
                    }
                } catch (IOException e) {
                    // Handle client disconnect
                    System.out.println("Client disconnected during audio streaming.");
                }
                fis.close();
                audioOutStream.close();
                audioSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
