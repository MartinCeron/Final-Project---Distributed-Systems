import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicServer {
    private static final int PORT = 12345; // Server port
    private static final List<Socket> clientSockets = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Music Server is running on port " + PORT);

            // Thread to handle broadcasting
            ScheduledExecutorService broadcaster = Executors.newScheduledThreadPool(1);
            broadcaster.scheduleAtFixedRate(MusicServer::broadcastMusicData, 0, 1, TimeUnit.SECONDS);

            // Accepting client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());

                // Handle client disconnection
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void broadcastMusicData() {
        String musicData = "Track: Song Title | Timestamp: " + System.currentTimeMillis() % 60000 + " ms";
        for (Socket socket : clientSockets) {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(musicData);
            } catch (IOException e) {
                System.out.println("Error sending data to client: " + socket.getRemoteSocketAddress());
                clientSockets.remove(socket);
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (clientSocket) {
            // Just keeping the connection alive for broadcasting
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            while (in.readLine() != null) {
                // Client can send control messages if needed
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        } finally {
            clientSockets.remove(clientSocket);
        }
    }
}
