import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javazoom.jl.player.Player;

import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class AudioStreamingClientUI extends Application {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    private PrintWriter out;
    private BufferedReader in;
    private List<String> trackList = new ArrayList<>();
    private ListView<String> trackListView = new ListView<>();
    private Label statusLabel = new Label("Status: Disconnected");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Audio Streaming Client");

        Button playButton = new Button("Play");
        Button pauseButton = new Button("Pause");
        Button stopButton = new Button("Stop");

        playButton.setOnAction(e -> {
            String selectedTrack = trackListView.getSelectionModel().getSelectedItem();
            if (selectedTrack != null) {
                sendCommand("PLAY_TRACK " + selectedTrack);
            }
        });

        pauseButton.setOnAction(e -> sendCommand("PAUSE"));
        stopButton.setOnAction(e -> sendCommand("STOP"));

        VBox controls = new VBox(10, playButton, pauseButton, stopButton);
        VBox layout = new VBox(10, trackListView, controls, statusLabel);
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Platform.runLater(() -> statusLabel.setText("Connected to Server"));

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("TRACK_LIST")) {
                        String track = line.substring(11);
                        Platform.runLater(() -> {
                            trackList.add(track);
                            trackListView.getItems().add(track);
                        });
                    } else if (line.startsWith("PLAY_TRACK")) {
                        String track = line.substring(11);
                        Platform.runLater(() -> statusLabel.setText("Playing: " + track));
                        new Thread(() -> playAudioStream(socket)).start();
                    } else if (line.equals("PAUSE")) {
                        Platform.runLater(() -> statusLabel.setText("Paused"));
                    } else if (line.equals("STOP")) {
                        Platform.runLater(() -> statusLabel.setText("Stopped"));
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Disconnected from Server"));
                e.printStackTrace();
            }
        }).start();
    }

    private void playAudioStream(Socket socket) {
        try (InputStream inputStream = socket.getInputStream();
             BufferedInputStream bufferedIn = new BufferedInputStream(inputStream)) {

            Player player = new Player(bufferedIn); // JLayer's Player
            player.play(); // Play the MP3 audio stream

        } catch (Exception e) {
            System.err.println("Error playing audio: " + e.getMessage());
            e.printStackTrace();
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
