import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.*;

public class PlaylistManagerUI extends Application {
    private Map<String, List<String>> playlists = new HashMap<>();
    private ListView<String> playlistView = new ListView<>();
    private ListView<String> trackView = new ListView<>();
    private TextField playlistNameField = new TextField();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Music Client with Playlist Management");

        // Playlist Controls
        Button createPlaylistButton = new Button("Create Playlist");
        Button addTrackButton = new Button("Add Track");
        Button loadPlaylistButton = new Button("Load Playlist");

        playlistNameField.setPromptText("Enter playlist name");

        createPlaylistButton.setOnAction(e -> createPlaylist());
        addTrackButton.setOnAction(e -> addTrack(primaryStage));
        loadPlaylistButton.setOnAction(e -> loadPlaylist());

        // Layout
        VBox playlistControls = new VBox(10, playlistNameField, createPlaylistButton, addTrackButton, loadPlaylistButton);
        playlistControls.setPadding(new Insets(10));
        playlistControls.setPrefWidth(200);

        VBox playlistDisplay = new VBox(10, new Label("Playlists:"), playlistView, new Label("Tracks:"), trackView);
        playlistDisplay.setPadding(new Insets(10));

        HBox layout = new HBox(10, playlistControls, playlistDisplay);
        Scene scene = new Scene(layout, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void createPlaylist() {
        String playlistName = playlistNameField.getText();
        if (!playlistName.isEmpty() && !playlists.containsKey(playlistName)) {
            playlists.put(playlistName, new ArrayList<>());
            playlistView.getItems().add(playlistName);
            playlistNameField.clear();
            showAlert("Success", "Playlist created successfully!");
        } else {
            showAlert("Error", "Playlist already exists or name is empty!");
        }
    }

    private void addTrack(Stage stage) {
        String selectedPlaylist = playlistView.getSelectionModel().getSelectedItem();
        if (selectedPlaylist == null) {
            showAlert("Error", "Please select a playlist first!");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav"));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            playlists.get(selectedPlaylist).add(selectedFile.getAbsolutePath());
            trackView.getItems().add(selectedFile.getName());
            showAlert("Success", "Track added successfully!");
        }
    }

    private void loadPlaylist() {
        String selectedPlaylist = playlistView.getSelectionModel().getSelectedItem();
        if (selectedPlaylist != null) {
            trackView.getItems().setAll(playlists.get(selectedPlaylist).stream().map(this::getFileName).toList());
            showAlert("Success", "Playlist loaded successfully!");
        } else {
            showAlert("Error", "No playlist selected!");
        }
    }

    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
