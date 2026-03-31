package com.example.simplechat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Интерфейс доступа к данным для сообщений
 * Все операции с таблицей messages
 */
@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MessageEntity> messages);

    @Delete
    void delete(MessageEntity message);

    // Получить все сообщения для чата (отсортированные по времени)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    LiveData<List<MessageEntity>> getMessagesForChat(long chatId);

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    List<MessageEntity> getMessagesForChatSync(long chatId);

    // Получить последнее сообщение чата
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    MessageEntity getLastMessageForChat(long chatId);

    // Удалить все сообщения чата (очистка истории)
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    void deleteAllMessagesForChat(long chatId);

    // Алиас для совместимости
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    void deleteMessagesForChatSync(long chatId);

    // Удалить все сообщения (полная очистка)
    @Query("DELETE FROM messages")
    void deleteAllMessages();

    // Получить количество непрочитанных сообщений
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isSentByMe = 0")
    int getUnreadCount(long chatId);

    // Обновить статус сообщения
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    void updateMessageStatus(long messageId, MessageEntity.MessageStatus status);
    
    // Получить все сообщения (для статистики)
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<MessageEntity> getAllMessagesSync();
}
