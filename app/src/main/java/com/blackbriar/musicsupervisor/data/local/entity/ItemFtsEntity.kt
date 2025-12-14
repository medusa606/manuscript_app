package com.blackbriar.musicsupervisor.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Fts4
import androidx.room.Entity
@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "items_fts")
data class ItemFtsEntity(
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val title: String,
    val author: String
)
