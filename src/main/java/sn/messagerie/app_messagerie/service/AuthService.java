package sn.messagerie.app_messagerie.service;

import sn.messagerie.app_messagerie.dao.UserDAO;
import sn.messagerie.app_messagerie.model.User;
import sn.messagerie.app_messagerie.util.PasswordUtil;

import java.util.Optional;

public class AuthService {
    private final UserDAO userDAO;

    public AuthService() {
        this.userDAO = new UserDAO();
    }

    public User register(String username, String password, User.Role role) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide.");
        }
        if (userDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà utilisé.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 6 caractères.");
        }

        String hashedPassword = PasswordUtil.hash(password);

        User newUser = new User(username, hashedPassword, role);
        userDAO.save(newUser);

        System.out.println("[AuthService] Nouvel utilisateur inscrit : " + username + " (" + role + ")");
        return newUser;
    }


    public User login(String username, String password) {
        // Recherche de l'utilisateur
        Optional<User> optUser = userDAO.findByUsername(username);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("Nom d'utilisateur ou mot de passe incorrect.");
        }

        User user = optUser.get();

        if (!PasswordUtil.verify(password, user.getPassword())) {
            throw new IllegalArgumentException("Nom d'utilisateur ou mot de passe incorrect.");
        }

        if (user.getStatus() == User.Status.ONLINE) {
            throw new IllegalArgumentException("Cet utilisateur est déjà connecté.");
        }

        user.setStatus(User.Status.ONLINE);
        userDAO.update(user);

        System.out.println("[AuthService] Connexion réussie : " + username);
        return user;
    }

    public void logout(User user) {

        user.setStatus(User.Status.OFFLINE);
        userDAO.update(user);
        System.out.println("[AuthService] Déconnexion : " + user.getUsername());
    }
}
