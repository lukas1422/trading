package sound;

import api.TradingConstants;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.time.LocalTime;

public class SoundPlayer {

    private String musicString;
    private static MediaPlayer player;

    private static void initAndShowGUI() {

        JFrame frame = new JFrame("Swing and JavaFX");
        final JFXPanel fxPanel = new JFXPanel();

        JButton startButton = new JButton("Start");
        JButton stopButton = new JButton("Stop");
        startButton.addActionListener(l->{
            System.out.println(" playing ");
            player.play();
        });

        stopButton.addActionListener(l->{
            System.out.println(" stopped ");
            player.stop();
        });

        frame.setLayout(new FlowLayout());
        frame.add(startButton);
        frame.add(stopButton);
        frame.add(fxPanel);
        frame.setSize(1000, 500);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
        String fileName = TradingConstants.DESKTOPPATH + "hello.wav";
        player = fileNameToURIString(fileName);
        player.setOnEndOfMedia(()->{
            System.out.println(" playing @ " + LocalTime.now());
            player.seek(Duration.ZERO);
        });
        MediaView mediaView = new MediaView(player);
        Group root = new Group(mediaView);
        //player.play();

        return (new Scene(root, 500, 200));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SoundPlayer::initAndShowGUI);
    }

}
