package sound;

import api.TradingConstants;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URI;

public class SoundPlayer2 extends Application {

        @Override
        public void start(Stage primaryStage) {

            String fileName = TradingConstants.GLOBALPATH+"suju.wav";
            File file = new File(fileName);
            URI uri = file.toURI();
            Media pick = new Media(uri.toString()); // replace this with your own audio file
            MediaPlayer player = new MediaPlayer(pick);

            player.setOnEndOfMedia(()->{
                player.seek(Duration.ZERO);
            });

            // Add a mediaView, to display the media. Its necessary !
            // This mediaView is added to a Pane
            MediaView mediaView = new MediaView(player);

            // Add to scene
            Group root = new Group(mediaView);
            Scene scene = new Scene(root, 500, 200);

            // Show the stage
            primaryStage.setTitle("Media Player");
            primaryStage.setScene(scene);
            primaryStage.show();

            // Play the media once the stage is shown
            player.play();
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
