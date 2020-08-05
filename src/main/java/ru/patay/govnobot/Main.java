package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.rest.util.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.patay.govnobot.business.logic.UserLogic;
import ru.patay.govnobot.commands.AddRole;
import ru.patay.govnobot.commands.DelRole;
import ru.patay.govnobot.commands.VoiceStateHandler;
import ru.patay.govnobot.dao.RoleDao;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
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
        RoleDao roleDao = new RoleDao();

        AddRole addRole = new AddRole(roleDao);
        DelRole delRole = new DelRole(roleDao);

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("TOKEN")).build().login().block();
        UserLogic.flushNotNull();

        assert client != null;
        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    log.info("Logged in as {}#{}", self.getUsername(), self.getDiscriminator());
                    Guild guild = client.getGuildById(GUILD_ID).block();
                    long ms = System.currentTimeMillis();
                    assert guild != null;
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
//                    Message message = channel.createMessage("Я согласен с <#722401701209833494>. Получить роль <@&736461622456877106>").block();
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
                .filter(message -> CHANNEL_BOT_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!addrole")
                        && !message.getAuthor().orElseThrow(NoSuchElementException::new).isBot())
                .doOnNext(addRole::exec)
                .onErrorContinue((throwable, o) -> {
                    Message message = (Message) o;
                    MessageChannel channel = Objects.requireNonNull(message.getChannel().block());
                    String localizedMessage = throwable.getLocalizedMessage();
                    channel.createMessage(localizedMessage).block(); // todo костыль
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> CHANNEL_BOT_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!delrole")
                        && !message.getAuthor().orElseThrow(NoSuchElementException::new).isBot())
                .doOnNext(delRole::exec)
                .onErrorContinue((throwable, o) -> {
                    Message message = (Message) o;
                    MessageChannel channel = Objects.requireNonNull(message.getChannel().block());
                    String localizedMessage = throwable.getLocalizedMessage();
                    channel.createMessage(localizedMessage).block(); // todo костыль
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> CHANNEL_STAFF_CHANNEL_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!xp"))
                .subscribe(message -> {
                    Set<Snowflake> userMentionIds = message.getUserMentionIds();
                    String text = userMentionIds.stream().map(UserLogic::getById).map(user ->
                            String.format("<@%d> Level: %d; Messages: %d; Time: %s", user.getId(), user.getLevel(), user.getMessages(), format(user.getTime())))
                            .collect(Collectors.joining("\n"));

                    message.getChannel().subscribe(mc -> mc.createEmbed(spec ->
                            spec.setColor(Color.DARK_GRAY)
                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block());
                });

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .subscribe(VoiceStateHandler::exec);

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .filter(event -> MESSAGE_ACCEPT_ID.equals(event.getMessageId()))
                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(RuntimeException::new))))
                .flatMap(member -> member.addRole(ROLE_LVL1_ID))
                .subscribe();

        client.onDisconnect().block();
    }
}
