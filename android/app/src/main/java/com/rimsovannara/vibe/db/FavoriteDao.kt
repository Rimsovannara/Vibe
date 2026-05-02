package com.rimsovannara.vibe.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_tracks")
    suspend fun getAllFavorites(): List<FavoriteTrack>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE id = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(track: FavoriteTrack)

    @Query("DELETE FROM favorite_tracks WHERE id = :trackId")
    suspend fun removeFavorite(trackId: String)
}
