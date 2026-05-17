package com.example.campusboard.domain.model

import java.util.UUID
import com.google.firebase.firestore.IgnoreExtraProperties

enum class PostType {
    NEWS, ALERT, EVENT, OPPORTUNITY, NOTES, OTHERS
}

enum class PostStatus {
    PENDING, APPROVED, REJECTED
}

@IgnoreExtraProperties
data class Post(
    var id: String = "",
    var title: String = "",
    var content: String = "",
    var author: String = "",
    var community: String = "",
    var type: PostType = PostType.NOTES,
    var status: PostStatus = PostStatus.APPROVED,
    var rejectionReason: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    var color: Long = 0xFFFFFFFFL,
    var style: Int = 0,
    var isBroadcast: Boolean = false
) {
    init {
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString()
        }
    }
}
