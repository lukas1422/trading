package sound;

import api.TradingConstants;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.time.LocalTime;

public class EmbeddedSoundPlayer {

    private static MediaPlayer player;

    public EmbeddedSoundPlayer() {
        SwingUtilities.invokeLater(EmbeddedSoundPlayer::initAndShowGUI);
    }

    public void stopIfPlaying() {
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.stop();
        }
    }

    public void playClip() {
        if (LocalTime.now().isAfter(LocalTime.of(8, 59))
                && LocalTime.now().isBefore(LocalTime.of(15, 0))) {
            try {
                player.play();
            } catch (MediaException ex) {
                System.out.println(" media not available ");
                ex.printStackTrace();
            }
        } else {
            System.out.println(" too late cannot play music ");
        }
    }

    private static void initAndShowGUI() {
        final JFXPanel fxPanel = new JFXPanel();
        Platform.runLater(() -> initFX(fxPanel));
    }

    private static void initFX(JFXPanel fxPanel) {
        Scene scene = createScene();
        fxPanel.setScene(scene);
    }

    private static MediaPlayer fileNameToURIString(String s) {
        File file = new File(s);
        URI uri = file.toURI();
        Media pick = new Media((uri.toString()));
        return new MediaPlayer(pick);
    }

    private static Scene createScene() {
        //String fileName = TradingConstants.GLOBALPATH + "suju.wav";
        //player = fileNameToURIString(fileName);
        //player.setOnEndOfMedia(() -> player.seek(Duration.ZERO));
        MediaView mediaView = new MediaView(player);
        Group root = new Group(mediaView);
        return (new Scene(root, 500, 200));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EmbeddedSoundPlayer::initAndShowGUI);
    }
}
