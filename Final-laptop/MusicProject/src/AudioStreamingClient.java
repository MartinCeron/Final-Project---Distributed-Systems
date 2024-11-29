import javax.sound.sampled.*;
import java.io.*;
import java.net.*;

public class AudioStreamingClient {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // Send command to play a specific track
            PrintWriter writer = new PrintWriter(out, true);
            writer.println("PLAY_TRACK /path/to/your/audiofile.mp3");

            // Receive and play the audio stream
            playAudioStream(in);

        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            e.printStackTrace();
        }
    }

    private static void playAudioStream(InputStream in) throws IOException, LineUnavailableException, UnsupportedAudioFileException {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(in));
        AudioFormat format = audioStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);

        audioLine.open(format);
        audioLine.start();

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1) {
            audioLine.write(buffer, 0, bytesRead);
        }

        audioLine.drain();
        audioLine.close();
        System.out.println("Audio playback completed.");
    }
}
