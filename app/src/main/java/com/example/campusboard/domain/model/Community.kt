package com.example.campusboard.domain.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Community(
    var name: String = "",
    var description: String = "",
    var creatorEmail: String = "",
    var timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this(name = "")
}
