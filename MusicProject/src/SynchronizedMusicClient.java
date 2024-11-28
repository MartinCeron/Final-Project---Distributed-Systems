import java.io.*;
import java.net.*;

public class SynchronizedMusicClient {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;
    private static volatile boolean isPlaying = false;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            new Thread(() -> {
                try {
                    String command;
                    while ((command = in.readLine()) != null) {
                        if (command.startsWith("PLAY")) {
                            isPlaying = true;
                            System.out.println("Playing from timestamp: " + command.split(" ")[1]);
                        } else if (command.startsWith("PAUSE")) {
                            isPlaying = false;
                            System.out.println("Paused");
                        } else if (command.startsWith("SEEK")) {
                            System.out.println("Seeked to timestamp: " + command.split(" ")[1]);
                        } else if (command.startsWith("UPDATE_TIMESTAMP")) {
                            System.out.println("Current timestamp: " + command.split(" ")[1]);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Simulate user commands
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while ((input = userInput.readLine()) != null) {
                out.println(input); // Send commands to the server
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
