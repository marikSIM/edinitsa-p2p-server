package com.example.simplechat;

import com.example.simplechat.data.MessageEntity;

/**
 * Модель сообщения для UI
 * Конвертируется в MessageEntity для сохранения в БД
 */
public class Message {
    private long id;
    private String text;
    private boolean isSentByMe;
    private long timestamp;
    private MessageStatus status;
    private String mediaPath;

    public enum MessageStatus {
        SENDING,
        SENT,
        DELIVERED,
        READ
    }

    public Message(String text, boolean isSentByMe) {
        this.text = text;
        this.isSentByMe = isSentByMe;
        this.timestamp = System.currentTimeMillis();
        this.status = MessageStatus.SENT;
    }

    // Конструктор из Entity
    public Message(MessageEntity entity) {
        this.id = entity.getId();
        this.text = entity.getText();
        this.isSentByMe = entity.isSentByMe();
        this.timestamp = entity.getTimestamp();
        this.status = convertStatus(entity.getStatus());
        this.mediaPath = entity.getMediaPath();
    }

    private MessageStatus convertStatus(MessageEntity.MessageStatus status) {
        if (status == null) return MessageStatus.SENT;
        switch (status) {
            case SENDING: return MessageStatus.SENDING;
            case SENT: return MessageStatus.SENT;
            case DELIVERED: return MessageStatus.DELIVERED;
            case READ: return MessageStatus.READ;
            default: return MessageStatus.SENT;
        }
    }

    // Конвертация в Entity
    public MessageEntity toEntity(long chatId) {
        MessageEntity.MessageStatus status;
        switch (this.status) {
            case SENDING: status = MessageEntity.MessageStatus.SENDING; break;
            case DELIVERED: status = MessageEntity.MessageStatus.DELIVERED; break;
            case READ: status = MessageEntity.MessageStatus.READ; break;
            default: status = MessageEntity.MessageStatus.SENT;
        }
        return new MessageEntity(chatId, text, isSentByMe, timestamp, status, null, MessageEntity.MediaType.NONE);
    }
    
    // Конвертация в Entity с медиа
    public MessageEntity toEntity(long chatId, String mediaPath, MessageEntity.MediaType mediaType) {
        MessageEntity.MessageStatus status;
        switch (this.status) {
            case SENDING: status = MessageEntity.MessageStatus.SENDING; break;
            case DELIVERED: status = MessageEntity.MessageStatus.DELIVERED; break;
            case READ: status = MessageEntity.MessageStatus.READ; break;
            default: status = MessageEntity.MessageStatus.SENT;
        }
        return new MessageEntity(chatId, text, isSentByMe, timestamp, status, mediaPath, mediaType);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
