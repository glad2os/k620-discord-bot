package ru.patay.govnobot;

import ru.patay.govnobot.entities.User;

public class Config {
    public static final User[] levels = new User[]{
            new User(736461622456877106L, 1, 0, 0),
            new User(736995556764680232L, 2, 150, 90 * 60000),
            new User(736995565774307368L, 3, 450, 360 * 60000),
            new User(736995568584491110L, 4, 750, 720 * 60000),
            new User(736995568840212651L, 5, 1500, 1440 * 60000),
            new User(736995569414701172L, 6, 2400, 2160 * 60000),
            new User(736996071510900836L, 7, 3600, 2520 * 60000),
            new User(736996073327034468L, 8, 5400, 2700 * 60000),
            new User(736996076334350336L, 9, 6600, 3600 * 60000),
            new User(736996077777059912L, 10, 12000, 12000 * 60000)
    };
}
