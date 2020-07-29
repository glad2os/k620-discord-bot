package ru.patay.govnobot.utils;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import ru.patay.govnobot.entities.Role;
import ru.patay.govnobot.entities.User;

import java.util.Optional;

public class HibernateSessionFactoryUtil {
    private static SessionFactory sessionFactory;

    private HibernateSessionFactoryUtil() {
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                Configuration configuration = new Configuration()
                        .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                        .setProperty("hibernate.connection.url", "jdbc:mysql://localhost:3306/govnobot?serverTimezone=Europe/Moscow")
                        .setProperty("hibernate.connection.username", "root")
                        .setProperty("hibernate.connection.password", System.getenv("DB_PASSWORD"))
                        .setProperty("hibernate.connection.pool_size", "2")
                        .setProperty("hibernate.current_session_context_class", "thread")
                        //.setProperty("hibernate.show_sql", "true") //todo: отключить
                        .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

                configuration.addAnnotatedClass(Optional.class);
                configuration.addAnnotatedClass(Role.class);
                configuration.addAnnotatedClass(User.class);
                StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties());
                StandardServiceRegistry build = builder.build();
                sessionFactory = configuration.buildSessionFactory(build);
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
        return sessionFactory;
    }
}
