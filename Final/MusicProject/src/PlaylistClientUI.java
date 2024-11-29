import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;

public class PlaylistClientUI extends Application {
    private PrintWriter out;
    private BufferedReader in;
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
        Button nextTrackButton = new Button("Next Track");
        Button addTrackButton = new Button("Add Track");

        playButton.setOnAction(e -> sendCommand("PLAY " + playbackPosition));
        pauseButton.setOnAction(e -> sendCommand("PAUSE"));
        nextTrackButton.setOnAction(e -> sendCommand("NEXT_TRACK"));
        addTrackButton.setOnAction(e -> addTrack());

        VBox controls = new VBox(10, playButton, pauseButton, nextTrackButton, addTrackButton);
        controls.setPadding(new Insets(10));

        VBox trackList = new VBox(10, new Label("Tracks:"), trackView);
        trackList.setPadding(new Insets(10));

        HBox layout = new HBox(10, controls, trackList);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try (Socket socket = new Socket("127.0.0.1", 12345)) {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            statusLabel.setText("Status: Connected");

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
                    trackView.getItems().add(track);
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

    private void addTrack() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Track");
        dialog.setHeaderText("Add a new track to the playlist");
        dialog.setContentText("Track name:");
        dialog.showAndWait().ifPresent(track -> sendCommand("ADD_TRACK " + track));
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
