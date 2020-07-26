package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.patay.govnobot.business.logic.UserLogic;
import ru.patay.govnobot.commands.AddRole;
import ru.patay.govnobot.commands.DelRole;
import ru.patay.govnobot.dao.RoleDao;

import java.util.Objects;
import java.util.Optional;

public class Main {

    private static final Logger log = LogManager.getLogger();
    public static final Snowflake GUILD_ID = Snowflake.of(381730844588638208L);
    private static final Snowflake COMMAND_CHANNEL = Snowflake.of(708772058968096850L);
    private static final Snowflake AFK_CHANNEL = Snowflake.of(736948549694128211L);
    private static final Snowflake MESSAGE_ID = Snowflake.of(737014451093635092L);
    private static final Snowflake LVL1_ID = Snowflake.of(736461622456877106L);

    @SuppressWarnings({"ConstantConditions"})
    public static void main(String[] args) {

        RoleDao roleDao = new RoleDao();

        AddRole addRole = new AddRole(roleDao);
        DelRole delRole = new DelRole(roleDao);

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("TOKEN")).build().login().block();

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    log.info("Logged in as {}#{}", self.getUsername(), self.getDiscriminator());
//                    MessageChannel channel = (MessageChannel) client.getChannelById(Snowflake.of(737004761324191834L)).block();
//                    Message message = channel.createMessage("я согласен с <#722401701209833494>. ѕолучить роль <@&736461622456877106>").block();
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

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .subscribe(event -> {
                    long ms = System.currentTimeMillis();
                    ru.patay.govnobot.entities.User user = UserLogic.getById(event.getCurrent().getUserId());
                    Optional<Snowflake> channelId = event.getCurrent().getChannelId();
                    if (channelId.isPresent() && !channelId.get().equals(AFK_CHANNEL)) {
                        if (!user.getTimestamp().isPresent()) {
                            user.setTimestamp(ms);
                            UserLogic.saveOrUpdate(user);
                        }
                    } else {
                        if (user.getTimestamp().isPresent()) {
                            user.appendMinutes((int) ((ms - user.getTimestamp().get()) / 60000));
                            user.emptyTimestamp();
                            if (UserLogic.checkLevelUp(user)) {
                                ru.patay.govnobot.entities.User levelOld = Config.levels[user.getLevel() - 1];
                                ru.patay.govnobot.entities.User levelNew = Config.levels[user.getLevel()];
                                user.incLevel();
                                event.getCurrent().getMember()
                                        .doOnNext(member -> member.removeRole(Snowflake.of(levelOld.getId())).subscribe())
                                        .doOnNext(member -> member.addRole(Snowflake.of(levelNew.getId())).subscribe())
                                        .subscribe();
                            }
                            UserLogic.saveOrUpdate(user);
                        }
                    }
                });


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

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .filter(event -> event.getMessageId().equals(MESSAGE_ID)) // todo задать
                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(RuntimeException::new))))
                .flatMap(member -> member.addRole(LVL1_ID))
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

        client.onDisconnect().block();
    }
}


