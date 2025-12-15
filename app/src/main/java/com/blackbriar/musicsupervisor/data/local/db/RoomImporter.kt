package com.blackbriar.musicsupervisor.data.local.db

import android.content.Context
import com.blackbriar.musicsupervisor.data.local.entity.ItemFtsEntity
import com.blackbriar.musicsupervisor.data.local.entity.RawItem
import com.blackbriar.musicsupervisor.data.local.entity.toEntity

suspend fun importJsonToRoomWithFts(db: AppDatabase, context: Context) {
    val importer = JsonImporter(context)
    val rawItems: List<RawItem> = importer.loadItems()

    // Map to Room entity
    val entities = rawItems.map { it.toEntity() }
    db.itemDao().insertAll(entities)

    // Map to FTS entity
    val ftsEntities = entities.map { ItemFtsEntity(title = it.title, author = it.author) }
    db.itemDao().insertAll(entities)
}
