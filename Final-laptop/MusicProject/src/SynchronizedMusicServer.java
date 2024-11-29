import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SynchronizedMusicServer {
    private static final int PORT = 12345;
    private static final ConcurrentLinkedQueue<Socket> clients = new ConcurrentLinkedQueue<>();
    private static volatile boolean isPlaying = false;
    private static volatile long playbackPosition = 0; // Playback position in milliseconds
    private static volatile String currentTrack = "";

    public static void main(String[] args) {
        System.out.println("Music Server running on port " + PORT);

        ScheduledExecutorService playbackUpdater = Executors.newScheduledThreadPool(1);
        playbackUpdater.scheduleAtFixedRate(() -> {
            if (isPlaying) {
                playbackPosition += 1000; // Simulate 1 second of playback
                broadcast("UPDATE_TIMESTAMP " + playbackPosition);
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

            out.println(isPlaying ? "PLAY " + playbackPosition : "PAUSE " + playbackPosition);

            String command;
            while ((command = in.readLine()) != null) {
                if (command.startsWith("PLAY")) {
                    isPlaying = true;
                    playbackPosition = Long.parseLong(command.split(" ")[1]);
                    broadcast("PLAY " + playbackPosition);
                } else if (command.startsWith("PAUSE")) {
                    isPlaying = false;
                    broadcast("PAUSE " + playbackPosition);
                } else if (command.startsWith("LOAD_TRACK")) {
                    currentTrack = command.substring(11); // Extract track name
                    playbackPosition = 0;
                    broadcast("LOAD_TRACK " + currentTrack);
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        } finally {
            clients.remove(clientSocket);
        }
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
