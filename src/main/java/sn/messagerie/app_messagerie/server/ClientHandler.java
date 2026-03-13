package sn.messagerie.app_messagerie.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerie.app_messagerie.model.Message;
import sn.messagerie.app_messagerie.model.User;
import sn.messagerie.app_messagerie.service.AuthService;
import sn.messagerie.app_messagerie.service.MessageService;
import sn.messagerie.app_messagerie.util.Protocol;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final Socket socket;
    private final Map<String, ClientHandler> connectedClients; // référence partagée du serveur

    private BufferedReader in;
    private PrintWriter out;

    private User currentUser; // null si pas encore authentifié
    private final AuthService authService;
    private final MessageService messageService;

    public ClientHandler(Socket socket, Map<String, ClientHandler> connectedClients) {
        this.socket = socket;
        this.connectedClients = connectedClients;
        this.authService = new AuthService();
        this.messageService = new MessageService();
    }

    @Override
    public void run() {
        try {

            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);


            logger.info("Nouveau client connecté depuis : {}", socket.getInetAddress());

            String line;

            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {

            logger.warn("Perte de connexion pour {} : {}", getClientName(), e.getMessage());
        } finally {
            disconnect();
        }
    }

     // Traite un message reçu du client et appelle la bonne méthode.

    private void handleMessage(String rawMessage) {
        if (rawMessage.isEmpty()) return;

        String[] parts = Protocol.split(rawMessage);
        String command = parts[0];

        logger.debug("Reçu de {} : {}", getClientName(), rawMessage);

        switch (command) {
            case Protocol.REGISTER      -> handleRegister(parts);
            case Protocol.LOGIN         -> handleLogin(parts);
            case Protocol.LOGOUT        -> handleLogout();
            case Protocol.SEND_MSG      -> handleSendMessage(parts);
            case Protocol.GET_HISTORY   -> handleGetHistory(parts);
            case Protocol.GET_CONVERSATIONS -> handleGetConversations();
            case Protocol.GET_ONLINE_USERS -> handleGetOnlineUsers();
            case Protocol.GET_ALL_USERS -> handleGetAllUsers();
            default -> send(Protocol.build(Protocol.ERROR, "Commande inconnue : " + command));
        }
    }


    private void handleRegister(String[] parts) {
        if (parts.length < 4) { send(Protocol.build(Protocol.REGISTER_ERROR, "Paramètres manquants")); return; }
        try {
            String username = parts[1];
            String password = parts[2];
            User.Role role  = User.Role.valueOf(parts[3]);

            authService.register(username, password, role);
            send(Protocol.build(Protocol.REGISTER_OK, username));
            logger.info("Inscription réussie : {}", username); // RG12
        } catch (IllegalArgumentException e) {
            send(Protocol.build(Protocol.REGISTER_ERROR, e.getMessage()));
        }
    }


    private void handleLogin(String[] parts) {
        if (parts.length < 3) { send(Protocol.build(Protocol.LOGIN_ERROR, "Paramètres manquants")); return; }
        try {
            String username = parts[1];
            String password = parts[2];

            User user = authService.login(username, password);
            currentUser = user;

            // Enregistrement dans la map des clients connectés (pour envoi direct)
            connectedClients.put(username, this);

            send(Protocol.build(Protocol.LOGIN_OK, username, user.getRole().name()));
            logger.info("Connexion : {} ({})", username, user.getRole());

            // Notifier les autres clients de la connexion
            broadcast(Protocol.build(Protocol.USER_ONLINE, username), username);

            // RG6 : livrer les messages en attente
            deliverPendingMessages();

        } catch (IllegalArgumentException e) {
            send(Protocol.build(Protocol.LOGIN_ERROR, e.getMessage()));
        }
    }

    //LOGOUT
    private void handleLogout() {
        if (currentUser == null) return;
        String username = currentUser.getUsername();
        authService.logout(currentUser);
        connectedClients.remove(username);
        send(Protocol.build(Protocol.LOGOUT_OK));
        broadcast(Protocol.build(Protocol.USER_OFFLINE, username), username);
        logger.info("Déconnexion : {}", username); // RG12
        currentUser = null;
    }


    private void handleSendMessage(String[] parts) {
        if (currentUser == null) { send(Protocol.build(Protocol.MSG_ERROR, "Non authentifié")); return; }
        if (parts.length < 3)   { send(Protocol.build(Protocol.MSG_ERROR, "Paramètres manquants")); return; }

        String receiverUsername = parts[1];
        String contenu = parts[2];

        try {
            Message msg = messageService.sendMessage(currentUser, receiverUsername, contenu);
            send(Protocol.build(Protocol.MSG_OK));
            logger.info("Message de {} vers {}", currentUser.getUsername(), receiverUsername); // RG12

            // Envoyer le message confirmé à l'expéditeur avec la vraie date
            send(Protocol.build(
                    Protocol.MSG,
                    currentUser.getUsername(),
                    contenu,
                    msg.getDateEnvoi().format(DATE_FMT)
            ));

            // Rafraîchir la liste des conversations de l'expéditeur
            handleGetConversations();

            // Si le destinataire est connecté, lui envoyer le message immédiatement
            ClientHandler receiverHandler = connectedClients.get(receiverUsername);
            if (receiverHandler != null) {
                receiverHandler.send(Protocol.build(
                        Protocol.MSG,
                        currentUser.getUsername(),
                        contenu,
                        msg.getDateEnvoi().format(DATE_FMT)
                ));
                // Rafraîchir la liste des conversations du destinataire connecté
                receiverHandler.handleGetConversations();
                // Marquer comme RECU
                msg.setStatut(Message.Statut.RECU);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            send(Protocol.build(Protocol.MSG_ERROR, e.getMessage()));
        }
    }


    private void handleGetConversations() {
        if (currentUser == null) return;

        sn.messagerie.app_messagerie.dao.MessageDAO msgDAO = new sn.messagerie.app_messagerie.dao.MessageDAO();
        java.util.List<Object[]> summaries = msgDAO.findConversationSummaries(currentUser.getId());

        if (summaries.isEmpty()) {
            send(Protocol.build(Protocol.CONVERSATIONS_LIST, ""));
            return;
        }


        StringBuilder sb = new StringBuilder();
        for (Object[] row : summaries) {
            if (sb.length() > 0) sb.append(",");
            String username   = (String) row[0];
            String lastMsg    = ((String) row[1]).replace(",", "‚").replace("~~", "—"); // échapper les séparateurs
            String lastDate   = (String) row[2];
            long   unread     = ((Number) row[3]).longValue();
            sb.append(username).append("~~")
                    .append(lastMsg).append("~~")
                    .append(lastDate).append("~~")
                    .append(unread);
        }
        send(Protocol.build(Protocol.CONVERSATIONS_LIST, sb.toString()));
    }


    private void handleGetHistory(String[] parts) {
        if (currentUser == null || parts.length < 2) return;

        String otherUsername = parts[1];
        // On a besoin de l'id de l'autre utilisateur — on passe par le DAO indirectement via service
        // Pour simplifier, on récupère via la map ou en base
        List<Message> messages = messageService.getConversation(
                currentUser.getId(),
                getUserIdByUsername(otherUsername)
        );

        send(Protocol.HISTORY_START);
        for (Message m : messages) {
            // Format : MSG|senderUsername|contenu|date
            send(Protocol.build(
                    Protocol.MSG,
                    m.getSender().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi().format(DATE_FMT)
            ));
        }
        send(Protocol.HISTORY_END);

        // Marquer comme lus (RG — mise à jour statut)
        messageService.markConversationAsRead(getUserIdByUsername(otherUsername), currentUser.getId());

        // Envoyer la liste à jour des conversations (badges non-lus remis à zéro)
        handleGetConversations();
    }


    private void handleGetOnlineUsers() {
        String users = String.join(",", connectedClients.keySet());
        send(Protocol.build(Protocol.USERS_LIST, users));
    }


    private void handleGetAllUsers() {
        if (currentUser == null || currentUser.getRole() != User.Role.ORGANISATEUR) {
            send(Protocol.build(Protocol.ERROR, "Accès réservé aux organisateurs."));
            return;
        }
        handleGetOnlineUsers(); // Pour l'instant on retourne les connectés, extensible en base
    }



    public void send(String message) {
        out.println(message);
    }


    private void broadcast(String message, String excludeUsername) {
        for (Map.Entry<String, ClientHandler> entry : connectedClients.entrySet()) {
            if (!entry.getKey().equals(excludeUsername)) {
                entry.getValue().send(message);
            }
        }
    }


    private void deliverPendingMessages() {
        List<Message> pending = messageService.deliverPendingMessages(currentUser);
        for (Message m : pending) {
            send(Protocol.build(
                    Protocol.MSG,
                    m.getSender().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi().format(DATE_FMT) + " (en attente)"
            ));
        }
    }

    //Déconnexion propre du client
    private void disconnect() {
        if (currentUser != null) {
            try { authService.logout(currentUser); } catch (Exception ignored) {}
            connectedClients.remove(currentUser.getUsername());
            broadcast(Protocol.build(Protocol.USER_OFFLINE, currentUser.getUsername()), currentUser.getUsername());
            logger.info("Déconnexion (perte réseau) : {}", currentUser.getUsername()); // RG12
        }
        try { socket.close(); } catch (IOException ignored) {}
    }


    private String getClientName() {
        return currentUser != null ? currentUser.getUsername() : socket.getInetAddress().toString();
    }


    private Long getUserIdByUsername(String username) {
        sn.messagerie.app_messagerie.dao.UserDAO userDAO = new sn.messagerie.app_messagerie.dao.UserDAO();
        return userDAO.findByUsername(username)
                .map(User::getId)
                .orElse(-1L);
    }
}
