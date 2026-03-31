package com.example.simplechat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

/**
 * Интерфейс доступа к данным для профиля пользователя
 */
@Dao
public interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserProfileEntity profile);

    @Update
    void update(UserProfileEntity profile);

    @Query("SELECT * FROM user_profile WHERE id = 1")
    UserProfileEntity getProfile();

    @Query("SELECT * FROM user_profile WHERE id = 1")
    LiveData<UserProfileEntity> getProfileLive();

    @Query("UPDATE user_profile SET name = :name, avatar = :avatar, status = :status WHERE id = 1")
    void updateProfile(String name, String avatar, String status);
    
    @Query("UPDATE user_profile SET photoPath = :photoPath WHERE id = 1")
    void updatePhoto(String photoPath);
    
    @Query("UPDATE user_profile SET name = :name, avatar = :avatar, status = :status, photoPath = :photoPath WHERE id = 1")
    void updateProfileWithPhoto(String name, String avatar, String status, String photoPath);
}
