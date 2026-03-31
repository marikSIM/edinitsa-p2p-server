package com.example.simplechat;

import com.example.simplechat.data.ChatEntity;

/**
 * Модель чата для UI
 * Конвертируется в ChatEntity для сохранения в БД
 */
public class Chat {
    private long id;
    private String name;
    private String avatar;
    private String lastMessage;
    private long lastMessageTime;
    private boolean isOnline;
    private int unreadCount;

    public Chat(long id, String name, String avatar, String lastMessage, 
                long lastMessageTime, boolean isOnline, int unreadCount) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.isOnline = isOnline;
        this.unreadCount = unreadCount;
    }

    // Конструктор из Entity
    public Chat(ChatEntity entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.avatar = entity.getAvatar();
        this.lastMessage = entity.getLastMessage();
        this.lastMessageTime = entity.getLastMessageTime();
        this.isOnline = entity.isOnline();
        this.unreadCount = entity.getUnreadCount();
    }

    // Конвертация в Entity
    public ChatEntity toEntity() {
        return new ChatEntity(id, name, avatar, lastMessage, lastMessageTime, isOnline, unreadCount);
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

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
