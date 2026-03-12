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

public class RegisterController implements Initializable {

    
    @FXML private TextField regUsernameField;
    @FXML private PasswordField regPasswordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Button registerButton;
    @FXML private Hyperlink loginLink;
    @FXML private Label statusLabel;

    
    private Client client;
    private Stage primaryStage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        
        roleComboBox.getItems().addAll("MEMBRE", "BENEVOLE", "ORGANISATEUR");
        roleComboBox.setValue("MEMBRE"); 
    }

    public void setClient(Client client) {
        this.client = client;

        
        client.setOnMessageReceived(this::handleServerMessage);

        
        client.setOnDisconnected(() -> Platform.runLater(() ->
                setStatus("❌ Connexion au serveur perdue.", true)
        ));
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    
    @FXML
    private void handleRegister() {
        String username = regUsernameField.getText().trim();
        String password = regPasswordField.getText();
        String role = roleComboBox.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("⚠️ Veuillez remplir tous les champs.", true);
            return;
        }

        if (password.length() < 6) {
            setStatus("⚠️ Le mot de passe doit contenir au moins 6 caractères.", true);
            return;
        }

        setStatus("Inscription en cours...", false);
        registerButton.setDisable(true);

        
        client.register(username, password, role);
    }

    
    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sn/messagerie/app_messagerie/fx/login.fxml"));
            Scene loginScene = new Scene(loader.load(), 800, 600); 

            LoginController loginController = loader.getController();
            loginController.setClient(client);
            loginController.setPrimaryStage(primaryStage);

            primaryStage.setTitle("Messagerie — Connexion");
            primaryStage.setScene(loginScene);
        } catch (IOException e) {
            setStatus("❌ Erreur lors du retour à la connexion : " + e.getMessage(), true);
        }
    }

    
    private void handleServerMessage(String message) {
        String[] parts = Protocol.split(message);
        String command = parts[0];

        Platform.runLater(() -> {
            switch (command) {
                case Protocol.REGISTER_OK -> {
                    setStatus("✅ Compte créé avec succès ! Redirection...", false);
                    registerButton.setDisable(false);

                    
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                            Platform.runLater(this::handleBackToLogin);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                case Protocol.REGISTER_ERROR -> {
                    setStatus("❌ " + (parts.length > 1 ? parts[1] : "Erreur d'inscription"), true);
                    registerButton.setDisable(false);
                }
            }
        });
    }

    
    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (isError ? "#ef233c" : "#06d6a0") + "; -fx-font-size: 12px;");
    }
}

