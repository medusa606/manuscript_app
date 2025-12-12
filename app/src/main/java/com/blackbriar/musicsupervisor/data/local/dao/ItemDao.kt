package com.blackbriar.musicsupervisor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity
import com.blackbriar.musicsupervisor.data.local.entity.ItemFtsEntity

@Dao
interface ItemDao {

    // --- Main table inserts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    // --- FTS table inserts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(items: List<ItemFtsEntity>)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    // --- Prefix search for autocomplete ---
    @Query("""
        SELECT items.* FROM items
        JOIN items_fts ON items.rowid = items_fts.rowid
        WHERE items_fts MATCH :query || '*'
        LIMIT 50
    """)
    suspend fun searchPrefix(query: String): List<ItemEntity>

    // --- Optional: Full phrase search ---
    @Query("""
        SELECT items.* FROM items
        JOIN items_fts ON items.rowid = items_fts.rowid
        WHERE items_fts MATCH :query
        LIMIT 50
    """)
    suspend fun searchExact(query: String): List<ItemEntity>
}
