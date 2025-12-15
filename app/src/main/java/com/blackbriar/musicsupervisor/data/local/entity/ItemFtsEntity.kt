package com.blackbriar.musicsupervisor.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "items_fts")
data class ItemFtsEntity(
    val title: String,
    val author: String
)
