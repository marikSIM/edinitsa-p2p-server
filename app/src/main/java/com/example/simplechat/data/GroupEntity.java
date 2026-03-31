package com.example.simplechat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Сущность группы (группового чата)
 */
@Entity(tableName = "groups")
public class GroupEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;          // Название группы
    private String avatar;        // Эмодзи аватарка
    private long createdBy;       // ID создателя
    private long createdAt;       // Дата создания
    private int memberCount;      // Количество участников

    public GroupEntity(String name, String avatar, long createdBy, long createdAt, int memberCount) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.memberCount = memberCount;
    }

    // Геттеры и сеттеры
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
