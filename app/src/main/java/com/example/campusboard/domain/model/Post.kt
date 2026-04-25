package com.example.campusboard.domain.model

import java.util.UUID

enum class PostType {
    NEWS, ALERT, OPPORTUNITY, NOTES
}

data class Post(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val author: String = "",
    val community: String = "",
    val type: PostType = PostType.NOTES,
    val timestamp: Long = System.currentTimeMillis(),
    val color: Int = 0xFFFFFFFF.toInt() // Default white
) {
    // No-argument constructor required for Firestore deserialization
    constructor() : this(id = UUID.randomUUID().toString(), title = "", content = "", author = "", community = "", type = PostType.NOTES, timestamp = System.currentTimeMillis(), color = 0xFFFFFFFF.toInt())
}
