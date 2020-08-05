package ru.patay.govnobot.dao;

import org.hibernate.Session;
import ru.patay.govnobot.entities.Role;
import ru.patay.govnobot.utils.HibernateSessionFactoryUtil;

import java.util.List;

public class RoleDao {
    public List<Role> findAll() {
        Session session = HibernateSessionFactoryUtil.getSessionFactory().openSession();
        //noinspection unchecked
        List<Role> roles = session.createQuery("From Role").list();
        session.close();
        return roles;
    }
}
