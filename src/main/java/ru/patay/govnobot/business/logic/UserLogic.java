package ru.patay.govnobot.business.logic;

import discord4j.common.util.Snowflake;
import ru.patay.govnobot.Config;
import ru.patay.govnobot.dao.UserDao;
import ru.patay.govnobot.entities.User;

import java.util.List;

public class UserLogic {
    private static final UserDao userDao = new UserDao();

    public static User getById(Snowflake id) {
        long lId = id.asLong();
        return userDao.findById(lId).orElse(User.from(lId));
    }

    public static void saveOrUpdate(User user) {
        userDao.saveOrUpdate(user);
    }

    public static boolean checkLevelUp(User user) {
        User nextLevel;
        try {
            nextLevel = Config.levels[user.getLevel()];
        } catch (RuntimeException e) {
            return false;
        }
        return user.getMessages() >= nextLevel.getMessages() && user.getTime() >= nextLevel.getTime();
    }

    public static List<User> findNotNull() {
        return userDao.findNotNull();
    }

    public static void flushNotNull() {
        userDao.flushNotNull();
    }
}
