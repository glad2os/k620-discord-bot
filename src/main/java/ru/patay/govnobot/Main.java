package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
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

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("TOKEN")).build().login().blockOptional().orElseThrow(NoSuchElementException::new);
        User self = client.getSelf().blockOptional().orElseThrow(NoSuchElementException::new);
        UserLogic.flushNotNull();

        client.getEventDispatcher().on(Event.class)
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .subscribe(log::info);

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
//                    log.info("ReadyEvent.class");
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
//                    log.info("MessageCreateEvent.class");
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
//                .doOnNext(message -> log.info("MessageCreateEvent.class addrole"))
                .doOnNext(addRole::exec)
                .onErrorContinue((throwable, o) -> {
                    Message message = (Message) o;
                    MessageChannel channel = message.getChannel().blockOptional().orElseThrow(NoSuchElementException::new);
                    String localizedMessage = throwable.getLocalizedMessage();
                    channel.createMessage(localizedMessage).block(); // todo ???????
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> CHANNEL_BOT_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!delrole")
                        && !message.getAuthor().orElseThrow(NoSuchElementException::new).isBot())
//                .doOnNext(message -> log.info("MessageCreateEvent.class delrole"))
                .doOnNext(delRole::exec)
                .onErrorContinue((throwable, o) -> {
                    Message message = (Message) o;
                    MessageChannel channel = message.getChannel().blockOptional().orElseThrow(NoSuchElementException::new);
                    String localizedMessage = throwable.getLocalizedMessage();
                    channel.createMessage(localizedMessage).block();
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> CHANNEL_STAFF_CHANNEL_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!xp"))
                .subscribe(message -> {
//                    log.info("MessageCreateEvent.class xp");
                    Set<Snowflake> userMentionIds = message.getUserMentionIds();
                    String text = userMentionIds.stream().map(UserLogic::getById).map(user ->
                            String.format("<@%d> Level: %d; Messages: %d; Time: %s", user.getId(), user.getLevel(), user.getMessages(), format(user.getTime())))
                            .collect(Collectors.joining("\n"));

                    message.getChannel().subscribe(mc -> mc.createEmbed(spec ->
                            spec.setColor(Color.DARK_GRAY)
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block());
                });

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
//                .doOnNext(voiceStateUpdateEvent -> log.info("VoiceStateUpdateEvent.class"))
                .subscribe(VoiceStateHandler::exec);

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .filter(event -> MESSAGE_ACCEPT_ID.equals(event.getMessageId()))
//                .doOnNext(reactionAddEvent -> log.info("ReactionAddEvent.class"))
                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(NoSuchElementException::new))))
                .flatMap(member -> member.addRole(ROLE_LVL1_ID))
                .subscribe();

        // ЛОГИРОВАНИЕ

        MessageChannel voiceModLog = (MessageChannel) client.getChannelById(LOGS_VOICE).blockOptional().orElseThrow(NoSuchElementException::new);

        // leave channel
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> !vs.getCurrent().getChannelId().isPresent())
                .subscribe(voiceStateUpdateEvent -> {
//                    log.info("VoiceStateUpdateEvent.class leave");
                    String text = String.format("<@%d> покинул комнату %s",
                            voiceStateUpdateEvent.getCurrent().getUserId().asLong(),
                            voiceStateUpdateEvent.getOld().get().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(121, 68, 59))
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block();
                });

        // connect channel
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> !(vs.getOld().isPresent()))
                .subscribe(vs -> {
//                    log.info("VoiceStateUpdateEvent.class connect");
                    String text = String.format("<@%d> присоединился к %s", vs.getCurrent().getUserId().asLong(),
                            vs.getCurrent().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(60, 170, 60))
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block();
                });

        // server mute
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> vs.getOld().isPresent() &&
                        (
                                (!vs.getOld().get().isMuted() && vs.getCurrent().isMuted()) || (!vs.getOld().get().isDeaf() && vs.getCurrent().isDeaf())
                        )
                )
                .subscribe(vs -> {
//                    log.info("VoiceStateUpdateEvent.class mute");
                    String text = String.format("<@%d> получил серверный мут находясь в  %s",
                            vs.getCurrent().getUserId().asLong(),
                            vs.getCurrent().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(255, 0, 0))
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())).block();
                });

        // unmute server
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> vs.getOld().isPresent() &&
                        (
                                (vs.getOld().get().isMuted() && !vs.getCurrent().isMuted()) || (vs.getOld().get().isDeaf() && !vs.getCurrent().isDeaf())
                        )
                )
                .subscribe(vs -> {
//                    log.info("VoiceStateUpdateEvent.class unmute");
                    String text = String.format("с <@%d> был снят серверный мут в %s",
                            vs.getCurrent().getUserId().asLong(),
                            vs.getCurrent().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(27, 17, 22))
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())).block();
                });

        // change channel
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> vs.getOld().isPresent() && vs.getCurrent().getChannelId().isPresent() &&
                        vs.getOld().get().isMuted() == vs.getCurrent().isMuted() &&
                        vs.getOld().get().isDeaf() == vs.getCurrent().isDeaf() &&
                        vs.getOld().get().isSelfMuted() == vs.getCurrent().isSelfMuted() &&
                        vs.getOld().get().isSelfDeaf() == vs.getCurrent().isSelfDeaf()
                )
                .subscribe(vs -> {
//                    log.info("VoiceStateUpdateEvent.class change");
                    String text = String.format("<@%d> переместился из %s в %s",
                            vs.getCurrent().getUserId().asLong(),
                            vs.getOld().get().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName(),
                            vs.getCurrent().getChannel().blockOptional().orElseThrow(NoSuchElementException::new).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(209, 226, 49))
                                    .setAuthor(self.getUsername(), "", self.getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())
                    ).block();
                });

        client.onDisconnect().block();
    }
}
