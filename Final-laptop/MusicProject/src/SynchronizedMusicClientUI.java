import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;

public class SynchronizedMusicClientUI extends Application {
    private PrintWriter out; // For sending commands to the server
    private BufferedReader in; // For receiving commands from the server
    private Label statusLabel = new Label("Status: Disconnected");
    private ListView<String> trackView = new ListView<>();
    private volatile boolean isPlaying = false;
    private volatile long playbackPosition = 0;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Music Client");

        // Playback Controls
        Button playButton = new Button("Play");
        Button pauseButton = new Button("Pause");
        Button loadTrackButton = new Button("Load Track");

        playButton.setOnAction(e -> sendCommand("PLAY " + playbackPosition));
        pauseButton.setOnAction(e -> sendCommand("PAUSE"));
        loadTrackButton.setOnAction(e -> {
            String selectedTrack = trackView.getSelectionModel().getSelectedItem();
            if (selectedTrack != null) {
                sendCommand("LOAD_TRACK " + selectedTrack);
            }
        });

        VBox controls = new VBox(10, playButton, pauseButton, loadTrackButton);
        controls.setPadding(new Insets(10));

        VBox trackList = new VBox(10, new Label("Tracks:"), trackView);
        trackList.setPadding(new Insets(10));

        HBox layout = new HBox(10, controls, trackList);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Connect to the server
        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try (Socket socket = new Socket("127.0.0.1", 12345)) {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            statusLabel.setText("Status: Connected");

            // Listen for updates from the server
            String command;
            while ((command = in.readLine()) != null) {
                if (command.startsWith("PLAY")) {
                    isPlaying = true;
                    playbackPosition = Long.parseLong(command.split(" ")[1]);
                    statusLabel.setText("Playing from: " + playbackPosition);
                } else if (command.startsWith("PAUSE")) {
                    isPlaying = false;
                    statusLabel.setText("Paused");
                } else if (command.startsWith("LOAD_TRACK")) {
                    String track = command.substring(11);
                    statusLabel.setText("Loaded track: " + track);
                } else if (command.startsWith("UPDATE_TIMESTAMP")) {
                    playbackPosition = Long.parseLong(command.split(" ")[1]);
                    statusLabel.setText("Current position: " + playbackPosition);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: Disconnected");
        }
    }

    private void sendCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
