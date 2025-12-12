package com.blackbriar.musicsupervisor.data.local.db

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.blackbriar.musicsupervisor.data.local.entity.RawItem

class JsonImporter(private val context: Context) {

    fun loadItems(): List<RawItem> {
        val jsonText = context.assets.open("items.json")
            .bufferedReader()
            .use { it.readText() }

        val json = Json { ignoreUnknownKeys = true }

        return json.decodeFromString(jsonText)
    }
}
