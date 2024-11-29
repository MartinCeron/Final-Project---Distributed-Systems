import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PlaylistServer {
    private static final int PORT = 12345;
    private static final ConcurrentLinkedQueue<Socket> clients = new ConcurrentLinkedQueue<>();
    private static volatile boolean isPlaying = false;
    private static volatile long playbackPosition = 0;
    private static volatile String currentTrack = "";
    private static List<String> playlist = new ArrayList<>();
    private static int currentTrackIndex = -1;

    public static void main(String[] args) {
        System.out.println("Music Server running on port " + PORT);

        // Scheduled playback updates
        ScheduledExecutorService playbackUpdater = Executors.newScheduledThreadPool(1);
        playbackUpdater.scheduleAtFixedRate(() -> {
            if (isPlaying) {
                playbackPosition += 1000; // Increment by 1 second
                if (playbackPosition >= getTrackDuration(currentTrack)) { // Simulate track duration
                    nextTrack();
                } else {
                    broadcast("UPDATE_TIMESTAMP " + playbackPosition);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clients.add(clientSocket);
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            if (currentTrackIndex >= 0) {
                out.println("LOAD_TRACK " + currentTrack);
                out.println(isPlaying ? "PLAY " + playbackPosition : "PAUSE " + playbackPosition);
            }

            String command;
            while ((command = in.readLine()) != null) {
                if (command.startsWith("PLAY")) {
                    isPlaying = true;
                    playbackPosition = Long.parseLong(command.split(" ")[1]);
                    broadcast("PLAY " + playbackPosition);
                } else if (command.startsWith("PAUSE")) {
                    isPlaying = false;
                    broadcast("PAUSE " + playbackPosition);
                } else if (command.startsWith("ADD_TRACK")) {
                    String track = command.substring(10);
                    playlist.add(track);
                    if (playlist.size() == 1) {
                        currentTrackIndex = 0;
                        loadCurrentTrack();
                    }
                } else if (command.equals("NEXT_TRACK")) {
                    nextTrack();
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        } finally {
            clients.remove(clientSocket);
        }
    }

    private static void loadCurrentTrack() {
        if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
            currentTrack = playlist.get(currentTrackIndex);
            playbackPosition = 0;
            broadcast("LOAD_TRACK " + currentTrack);
        }
    }

    private static void nextTrack() {
        if (currentTrackIndex + 1 < playlist.size()) {
            currentTrackIndex++;
            loadCurrentTrack();
            isPlaying = true;
            broadcast("PLAY " + playbackPosition);
        } else {
            isPlaying = false;
            broadcast("PAUSE " + playbackPosition);
            System.out.println("End of playlist.");
        }
    }

    private static long getTrackDuration(String track) {
        // Simulate track duration: 5 minutes (300 seconds)
        return 300000;
    }

    private static void broadcast(String message) {
        for (Socket client : clients) {
            try {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                out.println(message);
            } catch (IOException e) {
                clients.remove(client);
            }
        }
    }
}
