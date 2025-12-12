package com.blackbriar.musicsupervisor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.blackbriar.musicsupervisor.data.local.dao.ItemDao
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity
import com.blackbriar.musicsupervisor.data.local.entity.ItemFtsEntity

@Database(
    entities = [ItemEntity::class, ItemFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
