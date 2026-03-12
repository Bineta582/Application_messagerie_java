package sn.messagerie.app_messagerie.dao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import sn.messagerie.app_messagerie.model.User;
import sn.messagerie.app_messagerie.util.HibernateUtil;
import java.util.List;
import java.util.Optional;
public class UserDAO {
    public void save(User user) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors de l enregistrement de l utilisateur : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
    public void update(User user) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors de la mise a jour de l utilisateur : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
    public Optional<User> findById(Long id) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            User user = em.find(User.class, id);
            return Optional.ofNullable(user);
        } finally {
            em.close();
        }
    }
    public Optional<User> findByUsername(String username) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            TypedQuery<User> query = em.createQuery(
                    "SELECT u FROM User u WHERE u.username = :username", User.class
            );
            query.setParameter("username", username);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }
    public List<User> findAll() {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }
    public List<User> findAllOnline() {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery(
                            "SELECT u FROM User u WHERE u.status = :status ORDER BY u.username",
                            User.class
                    ).setParameter("status", User.Status.ONLINE)
                    .getResultList();
        } finally {
            em.close();
        }
    }
    public void resetAllStatus() {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("UPDATE User u SET u.status = :status")
                    .setParameter("status", User.Status.OFFLINE)
                    .executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw new RuntimeException("Erreur lors du reset des statuts : " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
}
