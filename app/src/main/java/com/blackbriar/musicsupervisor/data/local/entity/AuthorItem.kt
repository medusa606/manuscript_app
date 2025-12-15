package com.blackbriar.musicsupervisor.data.local.entity

/**
 * Lightweight data class representing only the author field.
 * Used for Room queries that return only the author, e.g., autocomplete.
 */
data class AuthorItem(
    val author: String
)