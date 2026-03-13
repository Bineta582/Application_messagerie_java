package sn.messagerie.app_messagerie.client.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import sn.messagerie.app_messagerie.client.Client;
import sn.messagerie.app_messagerie.util.Protocol;


import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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
    private String selectedContact; // le contact dont on affiche la conversation


    private boolean suppressSelectionEvent = false;


    private final List<ConversationEntry> allConversations = new ArrayList<>();

    private final ObservableList<String> onlineMembers = FXCollections.observableArrayList();

    public static class ConversationEntry {
        public final String username;
        public final String lastMessage;
        public final String lastDate;
        public final long   unreadCount;

        public ConversationEntry(String u, String msg, String date, long unread) {
            this.username    = u;
            this.lastMessage = msg;
            this.lastDate    = date;
            this.unreadCount = unread;
        }
    }


    @Override
    public void initialize(URL url, ResourceBundle rb) {
        conversationsListView.setCellFactory(lv -> new ConversationCell());

        onlineMembersListView.setItems(onlineMembers);
        onlineMembersListView.setCellFactory(lv -> new MemberCell());

        conversationsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, entry) -> {
                    if (suppressSelectionEvent) return;
                    if (entry != null) loadConversation(entry.username);
                }
        );
    }

    public void setClient(Client client) {
        this.client = client;
        client.setOnMessageReceived(this::handleServerMessage);
        client.setOnDisconnected(() -> Platform.runLater(() ->
                showStatus("Connexion perdue. Veuillez relancer l'application.", true)
        ));
    }

    public void setCurrentUser(String username, String role) {
        this.currentUsername = username;
        this.currentRole     = role;
        userInfoLabel.setText("👤 " + username + "  ·  " + role);
        client.getConversations();
        client.getOnlineUsers(); // Charger la liste des membres en ligne
    }



    @FXML
    private void handleSendMessage() {
        if (selectedContact == null) {
            showStatus("Sélectionnez ou démarrez une conversation.", true);
            return;
        }
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        if (text.length() > 1000) { showStatus("Message trop long (max 1000).", true); return; }

        client.sendMessage(selectedContact, text);

        messageField.clear();
        messageField.requestFocus();
    }

    @FXML
    private void handleNewConversation() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Nouvelle conversation");
        dlg.setHeaderText("Démarrer une conversation");
        dlg.setContentText("Nom d'utilisateur :");
        dlg.showAndWait().ifPresent(u -> {
            String username = u.trim();
            if (!username.isEmpty() && !username.equals(currentUsername))
                loadConversation(username);
        });
    }

    @FXML
    private void handleSearch() {
        String q = searchField.getText().toLowerCase().trim();
        suppressSelectionEvent = true;
        conversationsListView.getItems().clear();
        allConversations.stream()
                .filter(e -> q.isEmpty()
                        || e.username.toLowerCase().contains(q)
                        || e.lastMessage.toLowerCase().contains(q))
                .forEach(conversationsListView.getItems()::add);
        suppressSelectionEvent = false;


        if (selectedContact != null) silentReselect(selectedContact);
    }

    @FXML
    private void handleLogout() {
        client.logout();
        client.disconnect();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/sn/messagerie/app_messagerie/fx/login.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load(), 500, 650);
            Client nc = new Client();
            LoginController ctrl = loader.getController();
            ctrl.setClient(nc);
            javafx.stage.Stage stage = (javafx.stage.Stage) messageField.getScene().getWindow();
            ctrl.setPrimaryStage(stage);
            stage.setScene(scene);
            stage.setTitle("Messagerie — Connexion");
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void handleServerMessage(String message) {
        String[] parts   = Protocol.split(message);
        String   command = parts[0];

        Platform.runLater(() -> {
            switch (command) {

                case Protocol.MSG -> {
                    if (parts.length >= 3) {
                        String  sender  = parts[1];
                        String  contenu = parts[2];
                        String  date    = parts.length > 3 ? parts[3] : "";
                        boolean isMine  = sender.equals(currentUsername);

                        if (isMine || sender.equals(selectedContact)) {
                            addMessageBubble(sender, contenu, date, isMine);
                        } else {
                            showStatus("💬 Nouveau message de " + sender, false);
                        }
                    }
                }


                case Protocol.CONVERSATIONS_LIST -> {
                    allConversations.clear();
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        for (String entry : parts[1].split(",")) {
                            String[] f = entry.split("~~", -1);
                            if (f.length >= 4) {
                                long unread = 0;
                                try { unread = Long.parseLong(f[3]); } catch (Exception ignored) {}
                                allConversations.add(new ConversationEntry(f[0], f[1], f[2], unread));
                            }
                        }
                    }

                    handleSearch();
                }


                case Protocol.HISTORY_START -> messagesContainer.getChildren().clear();
                case Protocol.HISTORY_END   -> scheduleScrollToBottom();


                case Protocol.USER_ONLINE -> {
                    if (parts.length > 1) {
                        String username = parts[1];
                        if (username.equals(selectedContact)) contactStatusLabel.setText("🟢 En ligne");

                        if (!onlineMembers.contains(username) && !username.equals(currentUsername)) {
                            onlineMembers.add(username);

                            FXCollections.sort(onlineMembers);
                        }
                        showStatus("🟢 " + username + " connecté", false);
                    }
                }
                case Protocol.USER_OFFLINE -> {
                    if (parts.length > 1) {
                        if (parts[1].equals(selectedContact)) contactStatusLabel.setText("⚫ Hors ligne");
                        showStatus("⚫ " + parts[1] + " déconnecté", false);

                        onlineMembers.remove(parts[1]);
                    }
                }

                //Liste des utilisateurs en ligne
                case Protocol.USERS_LIST -> {
                    onlineMembers.clear();
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        String[] users = parts[1].split(",");
                        for (String user : users) {
                            String trimmed = user.trim();
                            if (!trimmed.isEmpty() && !trimmed.equals(currentUsername)) {
                                onlineMembers.add(trimmed);
                            }
                        }
                    }
                    // Trier la liste
                    FXCollections.sort(onlineMembers);
                }

                case Protocol.MSG_ERROR ->
                        showStatus("Erreur envoi : " + (parts.length > 1 ? parts[1] : "?"), true);
                case Protocol.ERROR ->
                        showStatus("Erreur : " + (parts.length > 1 ? parts[1] : "?"), true);
            }
        });
    }


    private void loadConversation(String contactUsername) {
        selectedContact = contactUsername;
        conversationLabel.setText(contactUsername);
        contactStatusLabel.setText("");
        messagesContainer.getChildren().clear();
        client.getHistory(contactUsername);
        messageField.requestFocus();

    }


    private void silentReselect(String username) {
        suppressSelectionEvent = true;
        try {
            for (int i = 0; i < conversationsListView.getItems().size(); i++) {
                if (conversationsListView.getItems().get(i).username.equals(username)) {
                    conversationsListView.getSelectionModel().select(i);
                    return;
                }
            }

            conversationsListView.getSelectionModel().clearSelection();
        } finally {
            suppressSelectionEvent = false;
        }
    }


    private void addMessageBubble(String sender, String contenu, String date, boolean isMine) {
        Label bubble = new Label(contenu);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setStyle("-fx-background-radius: 16; -fx-font-size: 13px; " +
                (isMine
                        ? "-fx-background-color: #ef233c; -fx-text-fill: white;"
                        : "-fx-background-color: #3a3d58; -fx-text-fill: #edf2f4;"));

        Label meta = new Label(date);
        meta.setStyle("-fx-text-fill: #8d99ae; -fx-font-size: 10px;");

        VBox box = new VBox(3, bubble, meta);
        box.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox wrapper = new HBox(box);
        wrapper.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(2, 0, 2, 0));
        messagesContainer.getChildren().add(wrapper);

        scheduleScrollToBottom();
    }


    private void scheduleScrollToBottom() {

        Platform.runLater(() -> Platform.runLater(() ->
                messagesScrollPane.setVvalue(1.0)
        ));
    }

    private void showStatus(String message, boolean isError) {
        bottomStatusLabel.setText(message);
        bottomStatusLabel.setStyle("-fx-text-fill: " + (isError ? "#ef233c" : "#06d6a0")
                + "; -fx-font-size: 10px; -fx-padding: 4 15; -fx-background-color: #1a1b2e;");
    }


    private class ConversationCell extends ListCell<ConversationEntry> {

        private final HBox  root;
        private final Label avatar;
        private final Label name;
        private final Label date;
        private final Label preview;
        private final Label badge;

        public ConversationCell() {
            avatar = new Label();
            avatar.setMinWidth(42); avatar.setMinHeight(42);
            avatar.setMaxWidth(42); avatar.setMaxHeight(42);
            avatar.setAlignment(Pos.CENTER);

            name    = new Label();
            date    = new Label();
            date.setStyle("-fx-text-fill: #8d99ae; -fx-font-size: 10px;");

            badge = new Label();
            badge.setMinWidth(20); badge.setMinHeight(20);
            badge.setAlignment(Pos.CENTER);
            badge.setStyle("-fx-background-radius: 50; -fx-background-color: #ef233c; " +
                    "-fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold;");

            Region spc = new Region();
            HBox.setHgrow(spc, Priority.ALWAYS);

            HBox topRow = new HBox(4, name, spc, date, badge);
            topRow.setAlignment(Pos.CENTER_LEFT);

            preview = new Label();
            preview.setMaxWidth(180);

            VBox textCol = new VBox(3, topRow, preview);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            root = new HBox(10, avatar, textCol);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(8, 12, 8, 12));

            setGraphic(null); setText(null);
        }

        @Override
        protected void updateItem(ConversationEntry e, boolean empty) {
            super.updateItem(e, empty);
            if (empty || e == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                return;
            }

            String initial = e.username.substring(0, 1).toUpperCase();
            int hue = (e.username.charAt(0) * 37) % 360;
            avatar.setText(initial);
            avatar.setStyle("-fx-background-radius: 50; " +
                    "-fx-background-color: hsb(" + hue + ", 70%, 65%); " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");

            name.setText(e.username);
            name.setStyle("-fx-text-fill: #edf2f4; -fx-font-weight: bold; -fx-font-size: 13px;");
            date.setText(e.lastDate);

            String prev = e.lastMessage.length() > 38
                    ? e.lastMessage.substring(0, 38) + "…" : e.lastMessage;
            preview.setText(prev);

            if (e.unreadCount > 0) {
                badge.setText(String.valueOf(e.unreadCount));
                badge.setVisible(true);
                preview.setStyle("-fx-text-fill: #edf2f4; -fx-font-size: 11px; -fx-font-weight: bold;");
            } else {
                badge.setVisible(false);
                preview.setStyle("-fx-text-fill: #8d99ae; -fx-font-size: 11px;");
            }

            root.setStyle(isSelected()
                    ? "-fx-background-color: #3a3d58; -fx-background-radius: 6;"
                    : "-fx-background-color: transparent; " +
                    "-fx-border-color: transparent transparent #2b2d42 transparent; " +
                    "-fx-border-width: 0 0 1 0;");

            setGraphic(root);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        }
    }


    private class MemberCell extends ListCell<String> {

        private final HBox root;
        private final Label avatar;
        private final Label name;
        private final Label status;

        public MemberCell() {
            avatar = new Label("🟢");
            avatar.setMinWidth(36);
            avatar.setMinHeight(36);
            avatar.setMaxWidth(36);
            avatar.setMaxHeight(36);
            avatar.setAlignment(Pos.CENTER);
            avatar.setStyle("-fx-background-radius: 50; " +
                    "-fx-background-color: #3a3d58; " +
                    "-fx-text-fill: white; -fx-font-size: 14px;");

            name = new Label();
            name.setStyle("-fx-text-fill: #edf2f4; -fx-font-weight: bold; -fx-font-size: 12px;");

            status = new Label("En ligne");
            status.setStyle("-fx-text-fill: #06d6a0; -fx-font-size: 10px;");

            VBox textCol = new VBox(2, name, status);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            root = new HBox(8, avatar, textCol);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(6, 8, 6, 8));

            setGraphic(null);
            setText(null);
        }

        @Override
        protected void updateItem(String member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                return;
            }

            String initial = member.substring(0, 1).toUpperCase();
            int hue = (member.charAt(0) * 37) % 360;
            avatar.setText(initial);
            avatar.setStyle("-fx-background-radius: 50; " +
                    "-fx-background-color: hsb(" + hue + ", 70%, 65%); " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");

            name.setText(member);
            status.setText("🟢 En ligne");

            setGraphic(root);
            setStyle("-fx-background-color: transparent; -fx-padding: 0; " +
                    "-fx-border-color: transparent transparent #2b2d42 transparent; " +
                    "-fx-border-width: 0 0 1 0;");
        }
    }
}
