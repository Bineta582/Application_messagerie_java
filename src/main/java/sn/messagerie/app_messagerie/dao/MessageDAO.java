package sn.messagerie.app_messagerie.dao;


import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import sn.messagerie.app_messagerie.model.Message;
import sn.messagerie.app_messagerie.util.HibernateUtil;

import java.util.List;

public class MessageDAO {

    
    public void save(Message message) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(message);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors de la sauvegarde du message : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    
    public void update(Message message) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(message);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors de la mise à jour du message : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    
    public List<Message> findConversation(Long user1Id, Long user2Id) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            
            TypedQuery<Message> query = em.createQuery(
                    "SELECT m FROM Message m " +
                            "WHERE (m.sender.id = :u1 AND m.receiver.id = :u2) " +
                            "   OR (m.sender.id = :u2 AND m.receiver.id = :u1) " +
                            "ORDER BY m.dateEnvoi ASC", 
                    Message.class
            );
            query.setParameter("u1", user1Id);
            query.setParameter("u2", user2Id);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    
    public List<Message> findPendingMessages(Long receiverId) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE m.receiver.id = :receiverId " +
                                    "  AND m.statut = :statut " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class
                    ).setParameter("receiverId", receiverId)
                    .setParameter("statut", Message.Statut.ENVOYE)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    
    public List<Object[]> findConversationSummaries(Long userId) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            
            
            
            
            
            return em.createNativeQuery(
                    """
                    SELECT
                        contact_name,
                        last_content,
                        TO_CHAR(last_date, 'DD/MM HH24:MI') AS last_date_fmt,
                        unread_count
                    FROM (
                        SELECT
                            CASE
                                WHEN m.sender_id = :uid THEN ru.username
                                ELSE su.username
                            END AS contact_name,
                            m.contenu   AS last_content,
                            m.date_envoi AS last_date,
                            (
                                SELECT COUNT(*) FROM messages sub
                                WHERE sub.sender_id =
                                    CASE WHEN m.sender_id = :uid THEN m.receiver_id ELSE m.sender_id END
                                  AND sub.receiver_id = :uid
                                  AND sub.statut <> 'LU'
                            ) AS unread_count,
                            ROW_NUMBER() OVER (
                                PARTITION BY
                                    CASE WHEN m.sender_id = :uid THEN m.receiver_id ELSE m.sender_id END
                                ORDER BY m.date_envoi DESC
                            ) AS rn
                        FROM messages m
                        JOIN users su ON su.id = m.sender_id
                        JOIN users ru ON ru.id = m.receiver_id
                        WHERE m.sender_id = :uid OR m.receiver_id = :uid
                    ) sub2
                    WHERE rn = 1
                    ORDER BY last_date DESC
                    """
            ).setParameter("uid", userId).getResultList();
        } finally {
            em.close();
        }
    }

    
    public void markAsRead(Long senderId, Long receiverId) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery(
                            "UPDATE Message m SET m.statut = :lu " +
                                    "WHERE m.sender.id = :senderId " +
                                    "  AND m.receiver.id = :receiverId " +
                                    "  AND m.statut <> :lu"
                    ).setParameter("lu", Message.Statut.LU)
                    .setParameter("senderId", senderId)
                    .setParameter("receiverId", receiverId)
                    .executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors du marquage des messages : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
}

