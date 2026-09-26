package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_bookmarks")
data class WebBookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val category: String, // e.g. "Video Portals", "Creative Commons", "Audio & Music", "Custom"
    val description: String = "",
    val iconName: String = "globe",
    val isPinned: Boolean = false,
    val isAdultCategory: Boolean = false
)
