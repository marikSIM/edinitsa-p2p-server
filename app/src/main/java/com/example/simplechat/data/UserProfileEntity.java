package com.example.simplechat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Сущность профиля пользователя
 * Хранит данные текущего пользователя
 */
@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    private long id = 1;  // Всегда id=1 для текущего пользователя

    private String name;          // Имя пользователя
    private String avatar;        // Эмодзи аватарка
    private String status;        // Статус (о себе)
    private String phone;         // Телефон
    private String email;         // Email
    private String photoPath;     // Путь к фото профиля

    public UserProfileEntity(String name, String avatar, String status, String phone, String email, String photoPath) {
        this.name = name;
        this.avatar = avatar;
        this.status = status;
        this.phone = phone;
        this.email = email;
        this.photoPath = photoPath;
    }
    
    @androidx.room.Ignore
    public UserProfileEntity(String name, String avatar, String status, String phone, String email) {
        this.name = name;
        this.avatar = avatar;
        this.status = status;
        this.phone = phone;
        this.email = email;
        this.photoPath = null;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }
}
