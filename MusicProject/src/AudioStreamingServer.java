import java.io.*;
import java.net.*;
import java.util.*;

public class AudioStreamingServer {
    private static final int PORT = 12345;
    private static final String MUSIC_FOLDER = "C:/Users/Sam/Music";
    private static final List<String> trackList = new ArrayList<>();
    private static final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean isPlaying = false;
    private static volatile boolean isPaused = false;
    private static volatile String currentTrack = "";
    private static final Object pauseLock = new Object();

    public static void main(String[] args) {
        loadTracks();

        System.out.println("Audio Streaming Server is running on port " + PORT);

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

    private static void loadTracks() {
        File folder = new File(MUSIC_FOLDER);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("The folder " + MUSIC_FOLDER + " does not exist or is not a directory.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));

        if (files != null) {
            for (File file : files) {
                trackList.add(file.getName());
                System.out.println("Loaded track: " + file.getName());
            }
        } else {
            System.out.println("No .mp3 files found in " + MUSIC_FOLDER + ".");
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Send available tracks to the client
            for (String track : trackList) {
                out.println("TRACK_LIST " + track);
            }

            String command;
            while ((command = in.readLine()) != null) {
                if (command.startsWith("PLAY_TRACK")) {
                    String trackName = command.substring(11);
                    currentTrack = MUSIC_FOLDER + "/" + trackName;
                    isPlaying = true;
                    isPaused = false;
                    broadcast("PLAY_TRACK " + trackName);
                    streamAudio(currentTrack);
                } else if (command.equals("PAUSE")) {
                    isPaused = true;
                    broadcast("PAUSE");
                } else if (command.equals("STOP")) {
                    isPlaying = false;
                    isPaused = false;
                    broadcast("STOP");
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        } finally {
            clients.remove(clientSocket);
        }
    }

    private static void streamAudio(String trackPath) {
        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(trackPath))) {
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                synchronized (pauseLock) {
                    if (isPaused) {
                        pauseLock.wait(); // Wait until playback resumes
                    }
                }
                if (!isPlaying) {
                    break; // Stop playback if no longer playing
                }

                for (Socket client : clients) {
                    try {
                        OutputStream out = client.getOutputStream();
                        out.write(buffer, 0, bytesRead);
                        out.flush(); // Ensure data is sent immediately
                    } catch (IOException e) {
                        System.out.println("Error streaming to client: " + e.getMessage());
                    }
                }
                System.out.println("Streamed " + bytesRead + " bytes to clients.");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error streaming audio: " + e.getMessage());
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
