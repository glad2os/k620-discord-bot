package ru.patay.govnobot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.patay.govnobot.commands.AddRole;
import ru.patay.govnobot.commands.DelRole;

import java.util.Objects;

public class Main {
    private static final Logger log = LogManager.getLogger();
    public static final Snowflake GUILD_ID = Snowflake.of(381730844588638208L);
    private static final Snowflake COMMAND_CHANNEL = Snowflake.of(708772058968096850L);

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
//                    MessageChannel channel = (MessageChannel) client.getChannelById(Snowflake.of(722060566650290186L)).block();
//                    Message message = channel.createMessage("Согласен с правилами <#722401701209833494>, выдайте мне роль <@&736461622456877106>").block();
//                    message.addReaction(ReactionEmoji.unicode("\u2705")).block();
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
                .filter(event -> event.getMessageId().equals(Snowflake.of(736933619234373662L))) // todo задать
                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(RuntimeException::new))))
                .flatMap(member -> member.addRole(Snowflake.of(736461622456877106L)))
                .subscribe();

//        client.getEventDispatcher().on(ReactionRemoveEvent.class)
//                .filter(event -> event.getMessageId().equals(Snowflake.of(736915029064155256L))) // todo задать
//                .flatMap(event -> event.getUser().flatMap(user -> user.asMember(event.getGuildId().orElseThrow(RuntimeException::new))))
//                .flatMap(member -> member.removeRole(Snowflake.of(736461622456877106L)))
//                .subscribe();

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











//AFK



