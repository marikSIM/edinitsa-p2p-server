package com.example.simplechat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Интерфейс доступа к данным для чатов
 * Все операции с таблицей chats
 */
@Dao
public interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChatEntity> chats);

    @Update
    void update(ChatEntity chat);

    @Delete
    void delete(ChatEntity chat);

    // Получить все чаты (отсортированные по времени последнего сообщения)
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    LiveData<List<ChatEntity>> getAllChats();

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    List<ChatEntity> getAllChatsSync();

    // Получить чат по ID
    @Query("SELECT * FROM chats WHERE id = :chatId")
    ChatEntity getChatById(long chatId);

    @Query("SELECT * FROM chats WHERE id = :chatId")
    LiveData<ChatEntity> getChatByIdLive(long chatId);

    // Удалить чат
    @Query("DELETE FROM chats WHERE id = :chatId")
    void deleteChatById(long chatId);

    // Алиас для совместимости
    @Query("DELETE FROM chats WHERE id = :chatId")
    void deleteChatByIdSync(long chatId);

    // Удалить все чаты
    @Query("DELETE FROM chats")
    void deleteAllChats();

    // Поиск чатов по имени
    @Query("SELECT * FROM chats WHERE name LIKE '%' || :query || '%' ORDER BY lastMessageTime DESC")
    LiveData<List<ChatEntity>> searchChats(String query);

    // Обновить последнее сообщение чата
    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTime = :lastMessageTime WHERE id = :chatId")
    void updateLastMessage(long chatId, String lastMessage, long lastMessageTime);

    // Обновить статус онлайн
    @Query("UPDATE chats SET isOnline = :isOnline WHERE id = :chatId")
    void updateOnlineStatus(long chatId, boolean isOnline);

    // Сбросить счётчик непрочитанных
    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    void resetUnreadCount(long chatId);

    // Увеличить счётчик непрочитанных
    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE id = :chatId")
    void incrementUnreadCount(long chatId);

    // Получить чат по userId друга
    @Query("SELECT * FROM chats WHERE friendUserId = :friendUserId LIMIT 1")
    ChatEntity getChatByFriendUserIdSync(String friendUserId);
}
