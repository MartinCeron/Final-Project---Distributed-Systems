import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MusicStreamingServer {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Music Server running on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> streamMusic(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void streamMusic(Socket clientSocket) {
        try (OutputStream out = clientSocket.getOutputStream();
             FileInputStream musicFile = new FileInputStream("path/to/music.mp3")) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = musicFile.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead); // Send file data
            }
            System.out.println("Music streaming finished for " + clientSocket.getRemoteSocketAddress());
        } catch (IOException e) {
            System.out.println("Error streaming music to client: " + e.getMessage());
        }
    }
}
