package com.example.simplechat;

import com.example.simplechat.data.GroupEntity;

/**
 * Модель группы для UI
 * Конвертируется в GroupEntity для сохранения в БД
 */
public class Group {
    private long id;
    private String name;
    private String avatar;
    private long createdBy;
    private long createdAt;
    private int memberCount;

    public Group(long id, String name, String avatar, long createdBy, long createdAt, int memberCount) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.memberCount = memberCount;
    }

    // Конструктор из Entity
    public Group(GroupEntity entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.avatar = entity.getAvatar();
        this.createdBy = entity.getCreatedBy();
        this.createdAt = entity.getCreatedAt();
        this.memberCount = entity.getMemberCount();
    }

    // Конвертация в Entity
    public GroupEntity toEntity() {
        GroupEntity entity = new GroupEntity(name, avatar, createdBy, createdAt, memberCount);
        entity.setId(id);
        return entity;
    }

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
