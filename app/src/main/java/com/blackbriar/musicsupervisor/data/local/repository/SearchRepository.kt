package com.blackbriar.musicsupervisor.data.local.repository

import com.blackbriar.musicsupervisor.data.local.dao.ItemDao
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity

class SearchRepository(private val itemDao: ItemDao) {

    /**
     * Run FTS prefix search and apply fuzzy scoring
     */
    suspend fun search(query: String): List<ItemEntity> {
        // Short query for prefix search
        val prefixQuery = if (query.length < 3) query else query.take(3)
        val candidates = itemDao.searchPrefix(prefixQuery)

        // Apply fuzzy scoring
        return candidates.fuzzySort(query)
    }

    /**
     * Levenshtein distance for fuzzy sorting
     */
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = minOf(
                    dp[i-1][j] + 1,
                    dp[i][j-1] + 1,
                    dp[i-1][j-1] + if (a[i-1] == b[j-1]) 0 else 1
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Sort list by Levenshtein distance
     */
    private fun List<ItemEntity>.fuzzySort(query: String): List<ItemEntity> {
        return this.sortedBy { levenshtein(it.title.lowercase(), query.lowercase()) }
    }
}
