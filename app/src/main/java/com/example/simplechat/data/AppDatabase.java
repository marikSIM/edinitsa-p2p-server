package com.example.simplechat.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room Database для мессенджера ЕДИНИЦА
 * 
 * Все данные хранятся локально на устройстве пользователя:
 * - Сообщения
 * - Чаты/Контакты
 * 
 * Преимущества:
 * ✅ Работает без интернета
 * ✅ Данные под полным контролем пользователя
 * ✅ Можно очистить в любой момент
 * ✅ Безопасно — нет сервера
 */
@Database(entities = {MessageEntity.class, ChatEntity.class, UserProfileEntity.class, GroupEntity.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "unitca_messenger_db";

    public abstract MessageDao messageDao();
    public abstract ChatDao chatDao();
    public abstract UserProfileDao userProfileDao();
    public abstract GroupDao groupDao();

    /**
     * Получение экземпляра базы данных (Singleton)
     * Используется Double-Checked Locking для потокобезопасности
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // Разрешить запросы в главном потоке (для простоты, в продакшене лучше использовать LiveData/Async)
                    .allowMainThreadQueries()
                    // Удалить и создать заново при изменении схемы БД
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Очистка всех данных (по запросу пользователя)
     */
    public void clearAllData() {
        chatDao().deleteAllChats();
        messageDao().deleteAllMessages();
    }
}
