package sn.messagerie.app_messagerie.client.fx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import sn.messagerie.app_messagerie.client.Client;
import sn.messagerie.app_messagerie.util.Protocol;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {


    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button loginButton;

    
    @FXML private Label statusLabel;

    
    private Client client;
    private Stage primaryStage;

    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }

    public void setClient(Client client) {
        this.client = client;

        
        try {
            client.connect();
        } catch (IOException e) {
            setStatus("❌ Impossible de se connecter au serveur. Vérifiez qu'il est démarré.", true);
            loginButton.setDisable(true);
            return;
        }

        client.setOnMessageReceived(this::handleServerMessage);

        client.setOnDisconnected(() -> Platform.runLater(() ->
                setStatus("❌ Connexion au serveur perdue.", true)
        ));
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    
    @FXML
    private void handleLogin() {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("⚠️ Veuillez remplir tous les champs.", true);
            return;
        }

        setStatus("Connexion en cours...", false);
        loginButton.setDisable(true);

        client.login(username, password);

    }

    @FXML
    private void handleShowRegistration() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sn/messagerie/app_messagerie/fx/register.fxml"));
            Scene registerScene = new Scene(loader.load(), 800, 600);  

            RegisterController registerController = loader.getController();
            registerController.setClient(client);
            registerController.setPrimaryStage(primaryStage);

            primaryStage.setTitle("Messagerie – Inscription");
            primaryStage.setScene(registerScene);
        } catch (IOException e) {
            setStatus("❌ Erreur lors de l'ouverture de la page d'inscription : " + e.getMessage(), true);
        }
    }

    private void handleServerMessage(String message) {
        String[] parts = Protocol.split(message);
        String command = parts[0];

        Platform.runLater(() -> {
            switch (command) {
                case Protocol.LOGIN_OK -> {
                    
                    String username = parts[1];
                    String role = parts.length > 2 ? parts[2] : "MEMBRE";
                    openChatScreen(username, role);
                }
                case Protocol.LOGIN_ERROR -> {
                    setStatus("❌ " + (parts.length > 1 ? parts[1] : "Erreur de connexion"), true);
                    loginButton.setDisable(false);
                }
            }
        });
    }

    
    private void openChatScreen(String username, String role) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sn/messagerie/app_messagerie/fx/chat.fxml"));
            Scene chatScene = new Scene(loader.load(), 1200, 800);

            ChatController chatController = loader.getController();
            chatController.setClient(client);
            chatController.setCurrentUser(username, role);

            primaryStage.setTitle("Messagerie – " + username + " (" + role + ")");
            primaryStage.setScene(chatScene);
            primaryStage.setResizable(true);
            primaryStage.setMaximized(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);

        } catch (IOException e) {
            setStatus(" Erreur lors de l'ouverture du chat : " + e.getMessage(), true);
        }
    }

    
    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (isError ? "#ef233c" : "#06d6a0") + "; -fx-font-size: 12px;");
    }
}
