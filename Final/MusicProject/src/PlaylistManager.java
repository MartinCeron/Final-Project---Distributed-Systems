import java.sql.*;
import java.util.*;

public class PlaylistManager {
    private static final String DB_URL = "jdbc:sqlite:playlists.db";

    public static void createPlaylist(String name, List<String> tracks) {
        String trackList = String.join(",", tracks);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO playlists (name, tracks) VALUES (?, ?)")) {
            stmt.setString(1, name);
            stmt.setString(2, trackList);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getPlaylist(String name) {
        List<String> tracks = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT tracks FROM playlists WHERE name = ?")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                tracks = Arrays.asList(rs.getString("tracks").split(","));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tracks;
    }
}
