package com.blackbriar.musicsupervisor.data.local.entity


fun generateItemId(title: String, author: String): String {
    return "${title.trim().lowercase()}|${author.trim().lowercase()}"
}

fun RawItem.toEntity(): ItemEntity {
    val id = generateItemId(title, author)

    return ItemEntity(
        id = id,
        provider = provider,
        model = model,
        title = title,
        author = author,
        genres = genres,
        themes = themes,
        motif = motif,
        category = category,
        time_period = time_period,
        location = location,
        tags = tags,
        blurb = blurb
    )
}