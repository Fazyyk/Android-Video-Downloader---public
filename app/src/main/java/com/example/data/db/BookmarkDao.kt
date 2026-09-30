package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WebBookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM web_bookmarks ORDER BY isPinned DESC, id ASC")
    fun getAllBookmarks(): Flow<List<WebBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: WebBookmark): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<WebBookmark>)

    @Query("DELETE FROM web_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM web_bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT COUNT(*) FROM web_bookmarks")
    suspend fun getCount(): Int
}
