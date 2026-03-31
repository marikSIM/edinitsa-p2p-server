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
 * Интерфейс доступа к данным для групп
 */
@Dao
public interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GroupEntity group);

    @Update
    void update(GroupEntity group);

    @Delete
    void delete(GroupEntity group);

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    LiveData<List<GroupEntity>> getAllGroups();

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    List<GroupEntity> getAllGroupsSync();

    @Query("SELECT * FROM groups WHERE id = :groupId")
    GroupEntity getGroupById(long groupId);

    @Query("DELETE FROM groups WHERE id = :groupId")
    void deleteGroupById(long groupId);
}
