package sn.messagerie.app_messagerie;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import sn.messagerie.app_messagerie.client.Client;
import sn.messagerie.app_messagerie.client.fx.LoginController;

import java.io.IOException;


public class HelloApplication extends Application {

    private static Client client = new Client();

    @Override
    public void start(Stage primaryStage) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/sn/messagerie/app_messagerie/fx/login.fxml"));
        Scene scene = new Scene(loader.load(), 800, 600);

        LoginController loginController = loader.getController();
        loginController.setClient(client);
        loginController.setPrimaryStage(primaryStage);

        primaryStage.setTitle("Messagerie — Connexion");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);

        primaryStage.setOnCloseRequest(e -> {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        });

        primaryStage.show();
    }

    public static Client getClient() { return client; }

    public static void main(String[] args) {
        launch(args);
    }
}
