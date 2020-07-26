package ru.patay.govnobot.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;
import ru.patay.govnobot.dao.RoleDao;
import ru.patay.govnobot.entities.Role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.patay.govnobot.Main.GUILD_ID;

public class AddRole implements CommandInterface {
    private static final Logger log = LogManager.getLogger();
    RoleDao roleDao;

    public AddRole(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @SuppressWarnings({"DuplicatedCode", "ConstantConditions", "OptionalGetWithoutIsPresent"})
    @Override
    public void exec(Message message) {
        User author = message.getAuthor().get();
        Set<Snowflake> ids = author.asMember(GUILD_ID).map(Member::getRoleIds).block();
        List<Role> roles = roleDao.findAll();
        int accessLevel = roles.stream()
                .filter(role -> ids.contains(Snowflake.of(role.getId())))
                .flatMapToInt(role -> IntStream.of(role.getAccessLevel()))
                .max().orElse(0);

        if (accessLevel < 1) throw new RuntimeException("Недостаточно прав");

        Set<Snowflake> users = message.getUserMentionIds();
        Set<Snowflake> targetRoles = message.getRoleMentionIds();
        if (users.isEmpty()) throw new RuntimeException("Требуется указать пользователей");
        if (targetRoles.isEmpty()) throw new RuntimeException("Требуется указать роли");

        HashSet<Long> _allowedRoles = new HashSet<>();
        HashSet<Long> _deniedRoles = new HashSet<>();

        targetRoles.stream().map(Snowflake::asLong).forEach(id -> {
            int requiredAl = roles.stream().filter(role -> role.getId() == id).mapToInt(Role::getRequiredAccessLevelToGrant).findFirst().orElse(4);
            if (requiredAl > accessLevel) _deniedRoles.add(id);
            else _allowedRoles.add(id);
        });

        log.info("{} granted {} roles for {}", author.getId().asString(),
                String.join(", ", _allowedRoles.stream().map(String::valueOf).collect(Collectors.toSet())),
                String.join(", ", users.stream().map(Snowflake::asString).collect(Collectors.toSet())));

//        String[] list = message.getContent().split(" ");
//        Pattern patternTime = Pattern.compile("^(\\d+)([mhd])$");
//        for (String s : list) {
//            Matcher matcherTime = patternTime.matcher(s);
//            if (matcherTime.find()) {
//                int count = Integer.parseInt(matcherTime.group(1));
//                String type = matcherTime.group(2);
//                // todo generate time
//            }
//        }

        Guild guild = message.getGuild().block();
        users.forEach(id -> {
            Member member = guild.getMemberById(id).block();
            _allowedRoles.stream().map(Snowflake::of).map(member::addRole).forEach(Mono::block);
        });

        ArrayList<String> strings = new ArrayList<>();
        if (!_allowedRoles.isEmpty())
            strings.add(String.format("Granted <@&%s> roles for <@%s>",
                    String.join(">, <@&", _allowedRoles.stream().map(String::valueOf).collect(Collectors.toSet())),
                    String.join(">, <@", users.stream().map(Snowflake::asString).collect(Collectors.toSet()))));
        if (!_deniedRoles.isEmpty())
            strings.add(String.format("You have no permissions to grant following roles: <@&%s>",
                    String.join(">, <@&", _deniedRoles.stream().map(String::valueOf).collect(Collectors.toSet()))));
        String text = String.join("\n", strings);
        message.getChannel().block().createMessage(text).block();
    }
}
