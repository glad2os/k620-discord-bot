package ru.patay.govnobot.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "users")
public class User {
    @Id
    private long id;

    private int level;

    private int messages;

    private int minutes;

    @Column(nullable = true)
    private Long timestamp;

    public User() {
    }

    public User(long id, int level, int messages, int minutes) {
        this.id = id;
        this.level = level;
        this.messages = messages;
        this.minutes = minutes;
        this.timestamp = null;
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

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id &&
                level == user.level &&
                messages == user.messages &&
                minutes == user.minutes &&
                Objects.equals(timestamp, user.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, messages, minutes, timestamp);
    }

    public void incLevel() {
        this.level++;
    }

    public void incMessages() {
        this.messages++;
    }

    public void appendMinutes(int minutes) {
        this.minutes += minutes;
    }

    public static User from(long id) {
        User user = new User();
        user.id = id;
        user.level = 1;
        user.messages = 0;
        user.minutes = 0;
        user.timestamp = null;
        return user;
    }
}
