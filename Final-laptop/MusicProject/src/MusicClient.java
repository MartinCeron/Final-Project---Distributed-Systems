import java.io.*;
import java.net.*;

public class MusicClient {
    private static final String SERVER_ADDRESS = "127.0.0.1"; // Server IP address
    private static final int SERVER_PORT = 12345; // Server port

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Connected to Music Server");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Receive and display music data
            String musicData;
            while ((musicData = in.readLine()) != null) {
                System.out.println("Music Data: " + musicData);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }
}
