package ru.patay.govnobot.utils;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import ru.patay.govnobot.entities.Role;
import ru.patay.govnobot.entities.User;

public class HibernateSessionFactoryUtil {
    private static SessionFactory sessionFactory;

    private HibernateSessionFactoryUtil() {
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            try {
                Configuration configuration = new Configuration()
                        .addAnnotatedClass(Role.class)
                        .addAnnotatedClass(User.class)
                        .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                        .setProperty("hibernate.connection.url", "jdbc:mysql://localhost:3306/govnobot?serverTimezone=Europe/Moscow&autoReconnect=true")
                        .setProperty("hibernate.connection.username", "root")
                        .setProperty("hibernate.autoReconnect", "true")
                        .setProperty("hibernate.connection.password", System.getenv("DB_PASSWORD"))
                        .setProperty("hibernate.connection.pool_size", "5")
                        .setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
                        .setProperty("hibernate.c3p0.min_size", "5")
                        .setProperty("hibernate.c3p0.max_size", "20")
                        .setProperty("hibernate.c3p0.timeout", "1800")
                        .setProperty("hibernate.c3p0.testConnectionOnCheckout", "true")
                        .setProperty("hibernate.c3p0.max_statements", "100"); // count_of_statements * max_size
                StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties());
                StandardServiceRegistry build = builder.build();
                sessionFactory = configuration.buildSessionFactory(build);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sessionFactory;
    }
}

