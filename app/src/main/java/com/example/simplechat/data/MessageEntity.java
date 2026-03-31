package com.example.simplechat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Сущность сообщения для Room Database
 * Хранит все сообщения локально на устройстве пользователя
 */
@Entity(tableName = "messages")
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long chatId;          // ID чата
    private String text;          // Текст сообщения
    private boolean isSentByMe;   // true = исходящее
    private long timestamp;       // Время отправки
    private MessageStatus status; // Статус
    private String mediaPath;     // Путь к файлу
    private MediaType mediaType;  // Тип медиа
    
    public enum MediaType {
        NONE, IMAGE, VIDEO, AUDIO, FILE
    }

    public enum MessageStatus {
        SENDING, SENT, DELIVERED, READ
    }

    // Конструктор для Room (все поля)
    public MessageEntity(long chatId, String text, boolean isSentByMe, long timestamp, 
                         MessageStatus status, String mediaPath, MediaType mediaType) {
        this.chatId = chatId;
        this.text = text;
        this.isSentByMe = isSentByMe;
        this.timestamp = timestamp;
        this.status = status;
        this.mediaPath = mediaPath;
        this.mediaType = mediaType;
    }

    // Геттеры и сеттеры
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isSentByMe() {
        return isSentByMe;
    }

    public void setSentByMe(boolean sentByMe) {
        isSentByMe = sentByMe;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }
}
