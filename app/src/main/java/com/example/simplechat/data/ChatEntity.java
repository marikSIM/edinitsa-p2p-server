package com.example.simplechat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Сущность чата для Room Database
 * Хранит информацию о каждом чате/контакте
 */
@Entity(tableName = "chats")
public class ChatEntity {

    @PrimaryKey
    private long id;

    private String name;          // Имя контакта
    private String avatar;        // Эмодзи аватарка
    private String lastMessage;   // Текст последнего сообщения
    private long lastMessageTime; // Время последнего сообщения
    private boolean isOnline;     // Статус онлайн
    private int unreadCount;      // Количество непрочитанных
    private String friendUserId;  // P2P userId друга

    public ChatEntity(long id, String name, String avatar, String lastMessage, 
                      long lastMessageTime, boolean isOnline, int unreadCount) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.isOnline = isOnline;
        this.unreadCount = unreadCount;
        this.friendUserId = null;
    }

    @androidx.room.Ignore
    public ChatEntity(long id, String name, String avatar, String lastMessage,
                      long lastMessageTime, boolean isOnline, int unreadCount,
                      String friendUserId) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.isOnline = isOnline;
        this.unreadCount = unreadCount;
        this.friendUserId = friendUserId;
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

    public String getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(String friendUserId) {
        this.friendUserId = friendUserId;
    }
}
