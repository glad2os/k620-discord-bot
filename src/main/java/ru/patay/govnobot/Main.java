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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    public static final Snowflake GUILD_ID = Snowflake.of(381730844588638208L);
    public static final Snowflake STREAM_CHANNEL = Snowflake.of(616984490719313930L);
    private static final Logger log = LogManager.getLogger();
    private static final Snowflake COMMAND_CHANNEL = Snowflake.of(708772058968096850L);
    private static final Snowflake STAFF_CHANNEL = Snowflake.of(722064195977216071L);
    private static final Snowflake MESSAGE_ID = Snowflake.of(737014451093635092L);
    private static final Snowflake LVL1_ID = Snowflake.of(736461622456877106L);

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

    @SuppressWarnings({"ConstantConditions"})
    public static void main(String[] args) {
        RoleDao roleDao = new RoleDao();

        AddRole addRole = new AddRole(roleDao);
        DelRole delRole = new DelRole(roleDao);

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("TOKEN")).build().login().block();
        UserLogic.flushNotNull();

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    log.info("Logged in as {}#{}", self.getUsername(), self.getDiscriminator());
                    Guild guild = client.getGuildById(GUILD_ID).block();
                    Snowflake afkId = guild.getAfkChannelId().get();
                    long ms = System.currentTimeMillis();
                    guild.getChannels()
                            .filter(chan -> chan.getType().equals(Channel.Type.GUILD_VOICE) && !chan.getId().equals(STREAM_CHANNEL) && !chan.getId().equals(afkId))
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
                .filter(event -> !event.getMessage().getChannelId().equals(COMMAND_CHANNEL))
                .doOnNext(event -> {
                    Member member = event.getMember().get();
                    ru.patay.govnobot.entities.User user = UserLogic.getById(member.getId());
                    user.incMessages();
                    if (UserLogic.checkLevelUp(user)) {
                        ru.patay.govnobot.entities.User levelOld = Config.levels[user.getLevel() - 1];
                        ru.patay.govnobot.entities.User levelNew = Config.levels[user.getLevel()];
                        user.incLevel();
                        member.removeRole(Snowflake.of(levelOld.getId())).subscribe();
                        member.addRole(Snowflake.of(levelNew.getId())).subscribe();
                    }
                    UserLogic.saveOrUpdate(user);
                })
                .subscribe();

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(message -> message.getChannelId().equals(COMMAND_CHANNEL))
                .filter(message -> message.getContent().startsWith("!addrole"))
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
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
                .filter(message -> message.getChannelId().equals(COMMAND_CHANNEL))
                .filter(message -> message.getContent().startsWith("!delrole"))
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
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
                .filter(message -> message.getChannelId().equals(STAFF_CHANNEL))
                .filter(message -> message.getContent().startsWith("!xp"))
                .subscribe(message -> {
                    Set<Snowflake> userMentionIds = message.getUserMentionIds();
                    String text = userMentionIds.stream().map(UserLogic::getById).map(user ->
//                            String.format("<@%d> Level: %d; Messages: %d; Time: %d", user.getId(), user.getLevel(), user.getMessages(), user.getTime() / 60000))
                            String.format("<@%d> Level: %d; Messages: %d; Time: %s", user.getId(), user.getLevel(), user.getMessages(), format(user.getTime())))
                            .collect(Collectors.joining("\n"));

                    message.getChannel().subscribe(mc -> mc.createEmbed(spec ->
                            spec.setColor(Color.DARK_GRAY)
                                    .setAuthor(client.getSelf().block().getUsername(), "", client.getSelf().block().getAvatarUrl())
                                    .setTitle("Информация:")
                                    .setDescription(text)
                                    .setTimestamp(Instant.now())
                    ).block());
                });

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .subscribe(VoiceStateHandler::exec);

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .filter(event -> event.getMessageId().equals(MESSAGE_ID)) // todo задать
                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(RuntimeException::new))))
                .flatMap(member -> member.addRole(LVL1_ID))
                .subscribe();

        client.onDisconnect().block();
    }
}


