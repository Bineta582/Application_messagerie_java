package sn.messagerie.app_messagerie.client.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sn.messagerie.app_messagerie.client.Client;
import sn.messagerie.app_messagerie.util.Protocol;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ChatController implements Initializable {

    @FXML private Label userInfoLabel;
    @FXML private Label statusDot;
    @FXML private TextField searchField;
    @FXML private ListView<ConversationEntry> conversationsListView;
    @FXML private ListView<String> onlineMembersListView;
    @FXML private Label contactAvatarLabel;
    @FXML private Label contactStatusLabel;
    @FXML private ListView<String> usersListView;
    @FXML private Label conversationLabel;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private TextField messageField;
    @FXML private Label bottomStatusLabel;

    private Client client;
    private String currentUsername;
    private String currentRole;
    private String selectedContact;

    private final ObservableList<ConversationEntry> conversations = FXCollections.observableArrayList();
    private final ObservableList<String> onlineMembers = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        conversationsListView.setItems(conversations);
        conversationsListView.setCellFactory(lv -> new ConversationCell());
        onlineMembersListView.setItems(onlineMembers);
        onlineMembersListView.setCellFactory(lv -> new MemberCell());

        conversationsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, entry) -> {
            if (entry != null) {
                selectedContact = entry.contactName();
                conversationLabel.setText(selectedContact);
                messagesContainer.getChildren().clear();
                if (client != null) client.getHistory(selectedContact);
            }
        });

        onlineMembersListView.getSelectionModel().selectedItemProperty().addListener((obs, old, username) -> {
            if (username != null && !username.equals(currentUsername)) {
                selectedContact = username;
                conversationLabel.setText(selectedContact);
                messagesContainer.getChildren().clear();
                if (client != null) client.getHistory(selectedContact);
            }
        });
    }

    public void setClient(Client client) {
        this.client = client;
        client.setOnMessageReceived(this::handleServerMessage);
        client.setOnDisconnected(() -> Platform.runLater(() ->
                setBottomStatus("❌ Connexion au serveur perdue.", true)
        ));
    }

    public void setCurrentUser(String username, String role) {
        this.currentUsername = username;
        this.currentRole = role;
        userInfoLabel.setText(username + " (" + role + ")");
        client.getConversations();
        client.getOnlineUsers();
    }

    @FXML
    private void handleSendMessage() {
        if (selectedContact == null || selectedContact.isBlank()) return;
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        client.sendMessage(selectedContact, text);
        addMessageBubble(currentUsername, text, "maintenant", true);
        messageField.clear();
    }

    @FXML
    private void handleNewConversation() {
        if (onlineMembers.isEmpty()) {
            setBottomStatus("Aucun utilisateur en ligne.", false);
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(onlineMembers.get(0), onlineMembers);
        dialog.setTitle("Nouvelle conversation");
        dialog.setHeaderText("Choisir un contact");
        dialog.setContentText("Utilisateur :");
        dialog.showAndWait().ifPresent(contact -> {
            selectedContact = contact;
            conversationLabel.setText(selectedContact);
            messagesContainer.getChildren().clear();
            client.getHistory(selectedContact);
        });
    }

    @FXML
    private void handleLogout() {
        client.logout();
        client.disconnect();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sn/messagerie/app_messagerie/fx/login.fxml"));
            Stage stage = (Stage) messageField.getScene().getWindow();
            Client nc = new Client();
            Scene loginScene = new Scene(loader.load(), 800, 600);
            LoginController ctrl = loader.getController();
            ctrl.setClient(nc);
            ctrl.setPrimaryStage(stage);
            stage.setTitle("Messagerie – Connexion");
            stage.setScene(loginScene);
            stage.setMaximized(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleServerMessage(String message) {
        String[] parts = Protocol.split(message);
        String command = parts[0];

        Platform.runLater(() -> {
            switch (command) {
                case Protocol.MSG -> {
                    String sender  = parts.length > 1 ? parts[1] : "?";
                    String contenu = parts.length > 2 ? parts[2] : "";
                    String date    = parts.length > 3 ? parts[3] : "";
                    boolean isMine = sender.equals(currentUsername);
                    addMessageBubble(sender, contenu, date, isMine);
                    scheduleScrollToBottom();
                }
                case Protocol.CONVERSATIONS_LIST -> {
                    conversations.clear();
                    for (int i = 1; i < parts.length; i += 4) {
                        String contact  = parts[i];
                        String lastMsg  = parts.length > i + 1 ? parts[i + 1] : "";
                        String lastDate = parts.length > i + 2 ? parts[i + 2] : "";
                        int unread      = 0;
                        try { unread = Integer.parseInt(parts.length > i + 3 ? parts[i + 3] : "0"); }
                        catch (NumberFormatException ignored) {}
                        conversations.add(new ConversationEntry(contact, lastMsg, lastDate, unread));
                    }
                }
                case Protocol.HISTORY_START -> messagesContainer.getChildren().clear();
                case Protocol.HISTORY_END   -> scheduleScrollToBottom();
                case Protocol.USER_ONLINE -> {
                    String u = parts.length > 1 ? parts[1] : "";
                    if (!u.isEmpty() && !u.equals(currentUsername) && !onlineMembers.contains(u)) {
                        onlineMembers.add(u);
                    }
                    setBottomStatus(u + " est en ligne.", false);
                }
                case Protocol.USER_OFFLINE -> {
                    String u = parts.length > 1 ? parts[1] : "";
                    onlineMembers.remove(u);
                    setBottomStatus(u + " s'est déconnecté.", false);
                }
                case Protocol.USERS_LIST -> {
                    onlineMembers.clear();
                    for (int i = 1; i < parts.length; i++) {
                        if (!parts[i].isEmpty() && !parts[i].equals(currentUsername)) {
                            onlineMembers.add(parts[i]);
                        }
                    }
                }
                case Protocol.MSG_ERROR ->
                        setBottomStatus("❌ " + (parts.length > 1 ? parts[1] : "Erreur d'envoi"), true);
                case Protocol.ERROR ->
                        setBottomStatus("❌ " + (parts.length > 1 ? parts[1] : "Erreur serveur"), true);
                default -> {
                    // message ignoré
                }
            }
        });
    }

    private void scheduleScrollToBottom() {
        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    }

    private void addMessageBubble(String sender, String contenu, String date, boolean isMine) {
        Label bubble = new Label(contenu);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(isMine
                ? "-fx-background-color:#4361ee; -fx-text-fill:white; -fx-background-radius:12 12 0 12;"
                : "-fx-background-color:#2a2d3e; -fx-text-fill:white; -fx-background-radius:12 12 12 0;");

        Label dateLbl = new Label(date);
        dateLbl.setStyle("-fx-font-size:9px; -fx-text-fill:#888;");

        VBox box = new VBox(2, bubble, dateLbl);
        box.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(box);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 12, 4, 12));

        messagesContainer.getChildren().add(row);
    }

    private void setBottomStatus(String msg, boolean isError) {
        if (bottomStatusLabel != null) {
            bottomStatusLabel.setText(msg);
            bottomStatusLabel.setStyle("-fx-text-fill:" + (isError ? "#ef233c" : "#06d6a0") + ";");
        }
    }

    // ─── inner records / classes ────────────────────────────────────────────

    public record ConversationEntry(String contactName, String lastMessage, String lastDate, int unreadCount) {}

    private static class ConversationCell extends ListCell<ConversationEntry> {
        @Override
        protected void updateItem(ConversationEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }
            Label name   = new Label(item.contactName());
            name.setStyle("-fx-font-weight:bold; -fx-text-fill:white;");
            Label last   = new Label(item.lastMessage());
            last.setStyle("-fx-font-size:11px; -fx-text-fill:#aaa;");
            last.setMaxWidth(160);
            VBox info    = new VBox(2, name, last);
            Label date   = new Label(item.lastDate());
            date.setStyle("-fx-font-size:10px; -fx-text-fill:#888;");
            HBox row     = new HBox(10, info, date);
            row.setAlignment(Pos.CENTER_LEFT);
            if (item.unreadCount() > 0) {
                Label badge = new Label(String.valueOf(item.unreadCount()));
                badge.setStyle("-fx-background-color:#ef233c; -fx-text-fill:white; -fx-background-radius:8; -fx-padding:1 5 1 5; -fx-font-size:10px;");
                row.getChildren().add(badge);
            }
            setGraphic(row);
            setStyle("-fx-background-color:transparent;");
        }
    }

    private static class MemberCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }
            Label dot  = new Label("●");
            dot.setStyle("-fx-text-fill:#06d6a0; -fx-font-size:10px;");
            Label name = new Label(item);
            name.setStyle("-fx-text-fill:white;");
            HBox row   = new HBox(6, dot, name);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
            setStyle("-fx-background-color:transparent;");
        }
    }
}
