package com.blackbriar.musicsupervisor.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val model: String,
    val title: String,
    val author: String,
    val genres: String,
    val themes: String,
    val motif: String,
    val category: String,
    val time_period: String,
    val location: String,
    val tags: String,
    val blurb: String
)