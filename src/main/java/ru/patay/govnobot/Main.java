package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.rest.util.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.patay.govnobot.business.logic.UserLogic;
import ru.patay.govnobot.commands.VoiceStateHandler;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static ru.patay.govnobot.Config.*;

public class Main {
    private static final Logger log = LogManager.getLogger();

    private static String format(long time) {
        long ms = time % 1000;
        time /= 1000;
        long s = time % 60;
        time /= 60;
        long m = time % 60;
        time /= 60;
        long h = time % 24;
        time /= 24;
        long d = time;
        return String.format("%dd %02d:%02d:%02d.%03d", d, h, m, s, ms);
    }

    public static void main(String[] args) {
        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("TOKEN")).build().login().blockOptional().orElseThrow(NoSuchElementException::new);
        User self = client.getSelf().blockOptional().orElseThrow(NoSuchElementException::new);
        MessageChannel voiceModLog = (MessageChannel) client.getChannelById(LOGS_VOICE).blockOptional().orElseThrow(NoSuchElementException::new);
        UserLogic.flushNotNull();

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    log.info("Logged in as {}#{}", self.getUsername(), self.getDiscriminator());
                    Guild guild = client.getGuildById(GUILD_ID).blockOptional().orElseThrow(NoSuchElementException::new);
                    long ms = System.currentTimeMillis();
                    guild.getChannels()
                            .filter(chan -> chan.getType().equals(Channel.Type.GUILD_VOICE) && !IGNORE_VOICE_IDS.contains(chan.getId()))
                            .flatMap(chan -> ((VoiceChannel) chan).getVoiceStates())
                            .filter(vs -> !(vs.isMuted() || vs.isSelfMuted() || vs.isDeaf() || vs.isSelfDeaf()))
                            .map(VoiceState::getUserId).map(UserLogic::getById)
                            .doOnNext(user -> user.setTimestamp(ms))
                            .subscribe(UserLogic::saveOrUpdate);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        long _ms = System.currentTimeMillis();
                        UserLogic.findNotNull().forEach(user -> {
                            VoiceStateHandler.appendDiff(_ms, user);
                            if (UserLogic.checkLevelUp(user))
                                VoiceStateHandler.lvlUp(guild.getMemberById(Snowflake.of(user.getId())), user);
                            user.emptyTimestamp();
                            UserLogic.saveOrUpdate(user);
                        });
                    }));
//                    MessageChannel channel = (MessageChannel) client.getChannelById(Snowflake.of(737004761324191834L)).block();
//                    Message message = channel.createMessage("? ???????? ? <#722401701209833494>. ???????? ???? <@&736461622456877106>").block();
//                    message.addReaction(ReactionEmoji.unicode("\u2705")).block();
                });

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .filter(event -> !IGNORE_TEXT_IDS.contains(event.getMessage().getChannelId()))
                .doOnNext(event -> {
                    Member member = event.getMember().orElseThrow(NoSuchElementException::new);
                    ru.patay.govnobot.entities.User user = UserLogic.getById(member.getId());
                    user.incMessages();
                    if (UserLogic.checkLevelUp(user)) {
                        ru.patay.govnobot.entities.User levelOld = LEVELS[user.getLevel() - 1];
                        ru.patay.govnobot.entities.User levelNew = LEVELS[user.getLevel()];
                        user.incLevel();
                        member.removeRole(Snowflake.of(levelOld.getId())).subscribe();
                        member.addRole(Snowflake.of(levelNew.getId())).subscribe();
                    }
                    UserLogic.saveOrUpdate(user);
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> CHANNEL_STAFF_CHANNEL_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!xp"))
                .flatMap(message -> message.getChannel().flatMap(mc -> mc.createEmbed(spec -> spec
                        .setColor(Color.DARK_GRAY)
                        .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                        .setTitle("XP")
                        .setDescription(message.getUserMentionIds().stream().map(UserLogic::getById).map(user ->
                                String.format("<@%d> Level: %d; Messages: %d; Time: %s", user.getId(), user.getLevel(), user.getMessages(), format(user.getTime())))
                                .collect(Collectors.joining("\n")))
                        .setTimestamp(Instant.now()))))
                .subscribe();

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .subscribe(VoiceStateHandler::exec);

        client.onDisconnect().block();
    }
}
