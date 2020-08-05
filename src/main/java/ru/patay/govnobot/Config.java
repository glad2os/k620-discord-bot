package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import ru.patay.govnobot.entities.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Config {
    public static final Snowflake GUILD_ID = Snowflake.of(381730844588638208L);
    public static final Snowflake CHANNEL_BOT_ID = Snowflake.of(708772058968096850L);
    public static final Snowflake CHANNEL_STAFF_CHANNEL_ID = Snowflake.of(722064195977216071L);
    public static final Snowflake MESSAGE_ACCEPT_ID = Snowflake.of(737014451093635092L);
    public static final Snowflake ROLE_LVL1_ID = Snowflake.of(736461622456877106L);

    public static final List<Snowflake> IGNORE_VOICE_IDS = Arrays.asList(
            Snowflake.of(616984490719313930L), // Stream Limit
            Snowflake.of(737489022628135002L), // Stream
            Snowflake.of(736948549694128211L) // AFK
    );

    public static final List<Snowflake> IGNORE_TEXT_IDS = Collections.singletonList(
            CHANNEL_BOT_ID
    );

    public static final User[] LEVELS = new User[]{
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
