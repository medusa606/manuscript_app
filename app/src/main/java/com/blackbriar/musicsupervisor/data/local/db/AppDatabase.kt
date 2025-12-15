//package com.blackbriar.musicsupervisor.data.local.db
//
//import androidx.room.Database
//import androidx.room.RoomDatabase
//import com.blackbriar.musicsupervisor.data.local.dao.ItemDao
//import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity
//import com.blackbriar.musicsupervisor.data.local.entity.ItemFtsEntity
//
//@Database(
//    entities = [ItemEntity::class, ItemFtsEntity::class],
//    version = 1,
//    exportSchema = false
//)
//abstract class AppDatabase : RoomDatabase() {
//    abstract fun itemDao(): ItemDao
//}


package com.blackbriar.musicsupervisor.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
