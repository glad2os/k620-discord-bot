package ru.patay.govnobot.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import ru.patay.govnobot.entities.User;
import ru.patay.govnobot.utils.HibernateSessionFactoryUtil;

import java.util.List;
import java.util.Optional;

public class UserDao {
    public Optional<User> findById(long id) {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        Optional<User> user = session.byId(User.class).loadOptional(id);
        session.close();
        return user;
    }

    public void saveOrUpdate(User user) {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();
        session.saveOrUpdate(user);
        transaction.commit();
        session.close();
    }

    public List<User> findNotNull() {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        //noinspection unchecked
        List<User> users = session.createQuery("From User Where timestamp Is Not Null").list();
        session.close();
        return users;
    }

    public void flushNotNull() {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();
        session.createQuery("update User set timestamp = null where timestamp is not null").executeUpdate();
        transaction.commit();
        session.close();
    }
}
