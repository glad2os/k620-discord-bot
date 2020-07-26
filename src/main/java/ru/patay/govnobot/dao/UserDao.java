package ru.patay.govnobot.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import ru.patay.govnobot.entities.User;
import ru.patay.govnobot.utils.HibernateSessionFactoryUtil;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class UserDao {
    public Optional<User> findById(long id) {
        return HibernateSessionFactoryUtil.getSessionFactory().openSession().byId(User.class).loadOptional(id);
    }

    public void saveOrUpdate(User user) {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();
        session.saveOrUpdate(user);
        transaction.commit();
        session.close();
    }

    public List<User> findAll() {
        return (List<User>) HibernateSessionFactoryUtil.getSessionFactory().openSession().createQuery("From User").list();
    }
}
