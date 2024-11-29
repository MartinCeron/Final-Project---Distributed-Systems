import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class MusicStreamingClient {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             InputStream in = socket.getInputStream()) {

            System.out.println("Connected to music server. Streaming music...");

            // Play the audio from the stream
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
            System.out.println("Music streaming completed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
