package sn.messagerie.app_messagerie.service;

import sn.messagerie.app_messagerie.dao.MessageDAO;
import sn.messagerie.app_messagerie.dao.UserDAO;
import sn.messagerie.app_messagerie.model.Message;
import sn.messagerie.app_messagerie.model.User;

import java.util.List;
import java.util.Optional;

public class MessageService {

    private final MessageDAO messageDAO;
    private final UserDAO userDAO;

    public MessageService() {
        this.messageDAO = new MessageDAO();
        this.userDAO = new UserDAO();
    }


    public Message sendMessage(User sender, String receiverUsername, String contenu) {

        if (sender.getStatus() != User.Status.ONLINE) {
            throw new IllegalStateException("Vous devez être connecté pour envoyer un message.");
        }


        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide.");
        }

        if (contenu.length() > 1000) {
            throw new IllegalArgumentException("Le message ne peut pas dépasser 1000 caractères.");
        }

        Optional<User> optReceiver = userDAO.findByUsername(receiverUsername);
        if (optReceiver.isEmpty()) {
            throw new IllegalArgumentException("Le destinataire '" + receiverUsername + "' n'existe pas.");
        }

        User receiver = optReceiver.get();

        Message message = new Message(sender, receiver, contenu);

        messageDAO.save(message);

        System.out.println("[MessageService] Message de " + sender.getUsername()
                + " → " + receiverUsername
                + (receiver.getStatus() == User.Status.OFFLINE ? " (destinataire offline, livraison différée)" : ""));

        return message;
    }


    public List<Message> getConversation(Long user1Id, Long user2Id) {
        return messageDAO.findConversation(user1Id, user2Id);
    }


    public List<Message> deliverPendingMessages(User user) {
        List<Message> pending = messageDAO.findPendingMessages(user.getId());

        for (Message m : pending) {
            m.setStatut(Message.Statut.RECU);
            messageDAO.update(m);
        }
        if (!pending.isEmpty()) {
            System.out.println("[MessageService] " + pending.size()
                    + " message(s) en attente livrés à " + user.getUsername());
        }
        return pending;
    }


    public void markConversationAsRead(Long senderId, Long receiverId) {
        messageDAO.markAsRead(senderId, receiverId);
    }
}
