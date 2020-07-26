package ru.patay.govnobot.commands;

import discord4j.core.object.entity.Message;

public interface CommandInterface {
    void exec(Message message);
}
