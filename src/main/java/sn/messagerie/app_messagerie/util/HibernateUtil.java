package sn.messagerie.app_messagerie.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class HibernateUtil {

    private static final String PERSISTENCE_UNIT = "g2-messagerie";

    private static EntityManagerFactory entityManagerFactory;

    public static void init() {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT);
            System.out.println("[HibernateUtil] Connexion à la base de données établie.");
        }
    }

    public static EntityManager getEntityManager() {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            init();
        }
        return entityManagerFactory.createEntityManager();
    }

    public static void shutdown() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
            System.out.println("[HibernateUtil] Connexion à la base fermée.");
        }
    }
}
