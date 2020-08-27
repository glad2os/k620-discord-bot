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
import java.util.Optional;
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
                .filter(message -> CHANNEL_BOT_ID.equals(message.getChannelId())
                        && message.getContent().startsWith("!addrole")
                        && !message.getAuthor().orElseThrow(NoSuchElementException::new).isBot())
                .doOnNext(addRole::exec)
                .onErrorContinue((throwable, o) -> {
                    Message message = (Message) o;
                    MessageChannel channel = Objects.requireNonNull(message.getChannel().block());
                    String localizedMessage = throwable.getLocalizedMessage();
                    channel.createMessage(localizedMessage).block(); // todo ???????
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
                    channel.createMessage(localizedMessage).block();
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

        // ЛОГИРОВАНИЕ

        MessageChannel voiceModLog = (MessageChannel) client.getChannelById(Snowflake.of(743042808570445844L)).block();
        assert voiceModLog != null;

        // leave channel
        /*
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> !vs.getCurrent().getChannelId().isPresent())
                .subscribe(voiceStateUpdateEvent -> {
                    String text = String.format("<@%d> покинул комнату %s",
                            voiceStateUpdateEvent.getCurrent().getUserId().asLong(),
                            Objects.requireNonNull(voiceStateUpdateEvent.getOld().get().getChannel().block()).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(121, 68, 59))
                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block();
                });

        // connect channel
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> !(vs.getOld().isPresent()))
                        .subscribe(vs -> {
                            String text = String.format("<@%d> присоединился к %s", vs.getCurrent().getUserId().asLong(),
                                    Objects.requireNonNull(vs.getCurrent().getChannel().block()).getName());

                            voiceModLog.createEmbed(embedCreateSpec ->
                                    embedCreateSpec.setColor(Color.of(60, 170, 60))
                                            .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                            .setTitle("Информация:")
                                            .setDescription(text)
                                            .setTimestamp(Instant.now())
                            ).block();
                        });

        // server mute
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> vs.getCurrent().isDeaf() || vs.getCurrent().isMuted())
                .filter(vs -> vs.getOld().isPresent() && vs.getCurrent().getChannelId().isPresent())
                        .subscribe(vs -> {
                    String text = String.format("<@%d> получил серверный мут находясь в  %s",
                            vs.getCurrent().getUserId().asLong(),
                            Objects.requireNonNull(vs.getCurrent().getChannel().block()).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(255, 0, 0))
                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())).block();
                });

        // unmute server
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs -> !(vs.getCurrent().isDeaf() || vs.getCurrent().isMuted()) && (vs.getOld().get().isMuted() || vs.getOld().get().isDeaf()))
                .subscribe(vs -> {
                    String text = String.format("с <@%d> был снят серверный мут в %s",
                            vs.getCurrent().getUserId().asLong(),
                            Objects.requireNonNull(vs.getCurrent().getChannel().block()).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(27, 17, 22))
                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())).block();
                });

        // change channel
        //todo: fix
//        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
//                .filter(vs -> vs.getOld().isPresent() && vs.getCurrent().getChannelId().isPresent()
//                )
//                .subscribe(vs -> {
//                    String text = String.format("<@%d> переместился из %s в %s",
//                            vs.getCurrent().getUserId().asLong(),
//                            Objects.requireNonNull(vs.getOld().get().getChannel().block()).getName(),
//                            Objects.requireNonNull(vs.getCurrent().getChannel().block()).getName());
//
//                    voiceModLog.createEmbed(embedCreateSpec ->
//                            embedCreateSpec.setColor(Color.of(209, 226, 49))
//                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
//                                    .setDescription(text)
//                                    .setTitle("Информация:")
//                                    .setTimestamp(Instant.now())
//                    ).block();
//                });

*/
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .filter(vs ->
                        !(vs.getCurrent().isSelfMuted() || vs.getCurrent().isSelfDeaf()
                                || (vs.getOld().isPresent() && (vs.getOld().get().isSelfDeaf() || vs.getOld().get().isSelfMuted())
                        ))
                )
                .subscribe(voiceStateUpdateEvent -> {

                    //todo: разделить на 3 хендлера. Вынести отправку сообещения в void
                    
                    VoiceState current = voiceStateUpdateEvent.getCurrent();
                    Optional<VoiceState> old = voiceStateUpdateEvent.getOld();

                    if (!current.getChannelId().isPresent()) {

                        String text = String.format("<@%d> покинул комнату %s",
                                current.getUserId().asLong(),
                                Objects.requireNonNull(old.get().getChannel().block()).getName());

                        voiceModLog.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.of(121, 68, 59))
                                        .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                        .setTitle("Информация:")
                                        .setDescription(text)
                                        .setTimestamp(Instant.now())
                        ).block();
                        return;
                    }

                    if (!old.isPresent()) {
                        String text = String.format("<@%d> присоединился к %s", current.getUserId().asLong(), Objects.requireNonNull(current.getChannel().block()).getName());

                        voiceModLog.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.of(60, 170, 60))
                                        .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                        .setTitle("Информация:")
                                        .setDescription(text)
                                        .setTimestamp(Instant.now())
                        ).block();
                        return;
                    }

                    if (current.isDeaf() || current.isMuted()) {
                        String text = String.format("<@%d> получил серверный мут находясь в  %s",
                                current.getUserId().asLong(),
                                Objects.requireNonNull(current.getChannel().block()).getName());

                        voiceModLog.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.of(255, 0, 0))
                                        .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                        .setDescription(text)
                                        .setTitle("Информация:")
                                        .setTimestamp(Instant.now())).block();
                        return;
                    }

                    if (!(current.isDeaf() || current.isMuted()) && (old.get().isMuted() || old.get().isDeaf())) {
                        String text = String.format("с <@%d> был снят серверный мут в %s",
                                current.getUserId().asLong(),
                                Objects.requireNonNull(current.getChannel().block()).getName());

                        voiceModLog.createEmbed(embedCreateSpec ->
                                embedCreateSpec.setColor(Color.of(27, 17, 22))
                                        .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                        .setDescription(text)
                                        .setTitle("Информация:")
                                        .setTimestamp(Instant.now())).block();
                        return;
                    }

                    String text = String.format("<@%d> переместился из %s в %s",
                            current.getUserId().asLong(),
                            Objects.requireNonNull(old.get().getChannel().block()).getName(),
                            Objects.requireNonNull(current.getChannel().block()).getName());

                    voiceModLog.createEmbed(embedCreateSpec ->
                            embedCreateSpec.setColor(Color.of(209, 226, 49))
                                    .setAuthor(Objects.requireNonNull(client.getSelf().block()).getUsername(), "", Objects.requireNonNull(client.getSelf().block()).getAvatarUrl())
                                    .setDescription(text)
                                    .setTitle("Информация:")
                                    .setTimestamp(Instant.now())
                    ).block();
                });

        client.onDisconnect().block();
    }
}
