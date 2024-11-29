import java.sql.*;

public class PlaylistDatabase {
    private static final String DB_URL = "jdbc:sqlite:playlists.db";

    static {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String createTable = "CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY, track TEXT)";
            stmt.execute(createTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addTrack(String track) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO playlists (track) VALUES (?)")) {
            stmt.setString(1, track);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void loadPlaylist() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT track FROM playlists")) {
            while (rs.next()) {
                System.out.println("Track: " + rs.getString("track"));
                // Add tracks to in-memory playlist
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
