import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

public class MusicClientUI extends Application {
    private PlaylistManager playlistManager = new PlaylistManager();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Distributed Music Streaming");

        // UI Elements
        TextField playlistNameField = new TextField();
        playlistNameField.setPromptText("Enter playlist name");

        ListView<String> playlistView = new ListView<>();
        Button loadPlaylistButton = new Button("Load Playlist");
        Button createPlaylistButton = new Button("Create Playlist");

        createPlaylistButton.setOnAction(e -> {
            String playlistName = playlistNameField.getText();
            if (!playlistName.isEmpty()) {
                playlistManager.createPlaylist(playlistName, Arrays.asList("song1.mp3", "song2.mp3"));
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Playlist created successfully!");
                alert.show();
            }
        });

        loadPlaylistButton.setOnAction(e -> {
            String playlistName = playlistNameField.getText();
            if (!playlistName.isEmpty()) {
                List<String> tracks = playlistManager.getPlaylist(playlistName);
                playlistView.getItems().setAll(tracks);
            }
        });

        VBox layout = new VBox(10, playlistNameField, createPlaylistButton, loadPlaylistButton, playlistView);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
