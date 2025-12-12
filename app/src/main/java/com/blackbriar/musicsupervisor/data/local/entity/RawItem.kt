package com.blackbriar.musicsupervisor.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class RawItem(
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
    val blurb: String,
    val tokens_used: Int,
    val timestamp: String
)
