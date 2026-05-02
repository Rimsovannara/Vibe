package com.rimsovannara.vibe.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrack(
    @PrimaryKey val id: String,
    val addedAt: Long = System.currentTimeMillis()
)
