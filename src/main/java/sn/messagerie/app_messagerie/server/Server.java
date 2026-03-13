package sn.messagerie.app_messagerie.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerie.app_messagerie.util.HibernateUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT = 12345;


    private static final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        logger.info("  Démarrage du serveur Messagerie   ");

        HibernateUtil.init();

        new sn.messagerie.app_messagerie.dao.UserDAO().resetAllStatus();
        logger.info("Statuts utilisateurs remis à OFFLINE.");


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Arrêt du serveur...");
            HibernateUtil.shutdown();
        }));

        ServerSocket serverSocket = null;
        int maxRetries = 5;
        int retryCount = 0;

        while (serverSocket == null && retryCount < maxRetries) {
            try {
                serverSocket = new ServerSocket(PORT);

                serverSocket.setReuseAddress(true);
                logger.info("Serveur démarré sur le port {}. En attente de connexions...", PORT);
            } catch (IOException e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    logger.warn("Port {} occupé. Nouvelle tentative dans 2 secondes... ({}/{})",
                            PORT, retryCount, maxRetries);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Interruption lors de l'attente : {}", ie.getMessage());
                        HibernateUtil.shutdown();
                        return;
                    }
                } else {
                    logger.error("Impossible de démarrer le serveur après {} tentatives. Port {} occupé.",
                            maxRetries, PORT);
                    HibernateUtil.shutdown();
                    return;
                }
            }
        }

        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, connectedClients);

                Thread thread = new Thread(handler);
                thread.setDaemon(true); // s'arrête avec le serveur principal
                thread.start();

                logger.info("Nouveau thread client démarré. Clients actifs : {}", connectedClients.size());
            }
        } catch (IOException e) {
            logger.error("Erreur du serveur : {}", e.getMessage());
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                logger.error("Erreur lors de la fermeture du ServerSocket : {}", e.getMessage());
            }
            HibernateUtil.shutdown();
        }
    }
}

