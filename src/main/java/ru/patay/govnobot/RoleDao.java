package ru.patay.govnobot;

import ru.patay.govnobot.entities.Role;
import ru.patay.govnobot.utils.HibernateSessionFactoryUtil;

import java.util.List;

@SuppressWarnings("unchecked")
public class RoleDao {
    public List<Role> findAll() {
        return (List<Role>) HibernateSessionFactoryUtil.getSessionFactory().openSession().createQuery("From Role").list();
    }
}
