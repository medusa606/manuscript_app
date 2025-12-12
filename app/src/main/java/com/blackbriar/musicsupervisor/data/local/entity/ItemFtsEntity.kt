package com.blackbriar.musicsupervisor.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Fts5Entity
import androidx.room.PrimaryKey

@Fts5Entity(tableName = "items_fts")
data class ItemFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val title: String,
    val author: String
)
