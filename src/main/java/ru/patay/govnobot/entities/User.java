package ru.patay.govnobot.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unused")
@Entity
@Table(name = "users")
public class User {
    @Id
    private long id;

    private int level;

    private int messages;

    private long time;

    private Long timestamp;

    public User() {
    }

    public User(long id, int level, int messages, long time) {
        this.id = id;
        this.level = level;
        this.messages = messages;
        this.time = time;
        this.timestamp = null;
    }

    public static User from(long id) {
        User user = new User();
        user.id = id;
        user.level = 1;
        user.messages = 0;
        user.time = 0;
        user.timestamp = null;
        return user;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Optional<Long> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void emptyTimestamp() {
        this.timestamp = null;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getMessages() {
        return messages;
    }

    public void setMessages(int messages) {
        this.messages = messages;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long ms) {
        this.time = ms;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id &&
                level == user.level &&
                messages == user.messages &&
                time == user.time &&
                Objects.equals(timestamp, user.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, messages, time, timestamp);
    }

    public void incLevel() {
        this.level++;
    }

    public void incMessages() {
        this.messages++;
    }

    public void appendTime(long ms) {
        this.time += ms;
    }
}
