package ru.patay.govnobot.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import reactor.core.publisher.Mono;
import ru.patay.govnobot.Config;
import ru.patay.govnobot.business.logic.UserLogic;
import ru.patay.govnobot.entities.User;

import java.util.Optional;

import static ru.patay.govnobot.Main.STREAM_CHANNEL;

public class VoiceStateHandler {
    public static void appendDiff(long ms, User user) {
        user.appendTime(ms - user.getTimestamp().get());
    }

    public static void lvlUp(Mono<Member> member, User user) {
        User levelOld = Config.levels[user.getLevel() - 1];
        User levelNew = Config.levels[user.getLevel()];
        user.incLevel();
        member.subscribe(m -> {
            m.removeRole(Snowflake.of(levelOld.getId())).subscribe();
            m.addRole(Snowflake.of(levelNew.getId())).subscribe();
        });
    }

    public static void exec(VoiceStateUpdateEvent event) {
        long ms = System.currentTimeMillis();
        VoiceState vs = event.getCurrent();
        ru.patay.govnobot.entities.User user = UserLogic.getById(vs.getUserId());
        Optional<Snowflake> channelId = vs.getChannelId();
        // noinspection ConstantConditions
        if (channelId.isPresent() && !channelId.get().equals(event.getCurrent().getGuild().block().getAfkChannelId().get()) && !channelId.get().equals(STREAM_CHANNEL)) {
            if (user.getTimestamp().isPresent()) {
                if (vs.isMuted() || vs.isSelfMuted() || vs.isDeaf() || vs.isSelfDeaf()) {
                    appendDiff(ms, user);
                    if (UserLogic.checkLevelUp(user)) lvlUp(vs.getMember(), user);
                    user.emptyTimestamp();
                    UserLogic.saveOrUpdate(user);
                }
            } else {
                if (!(vs.isMuted() || vs.isSelfMuted() || vs.isDeaf() || vs.isSelfDeaf())) {
                    user.setTimestamp(ms);
                    UserLogic.saveOrUpdate(user);
                }
            }
        } else {
            if (user.getTimestamp().isPresent()) {
                appendDiff(ms, user);
                if (UserLogic.checkLevelUp(user)) lvlUp(vs.getMember(), user);
                user.emptyTimestamp();
                UserLogic.saveOrUpdate(user);
            }
        }
    }
}
