package ru.patay.govnobot.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@SuppressWarnings("unused")
@Entity
@Table(name = "roles")
public class Role {
    @Id
    private long id;

    @Column(name = "access_level")
    private int accessLevel;

    @Column(name = "required_al_to_grant")
    private int requiredAccessLevelToGrant;

    public Role() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }

    public int getRequiredAccessLevelToGrant() {
        return requiredAccessLevelToGrant;
    }

    public void setRequiredAccessLevelToGrant(int requiredAccessLevelToGrant) {
        this.requiredAccessLevelToGrant = requiredAccessLevelToGrant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return id == role.id &&
                accessLevel == role.accessLevel &&
                requiredAccessLevelToGrant == role.requiredAccessLevelToGrant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accessLevel, requiredAccessLevelToGrant);
    }
}
