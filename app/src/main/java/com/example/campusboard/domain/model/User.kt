package com.example.campusboard.domain.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var id: String = "",
    var email: String = "",
    var username: String = "",
    var role: Role = Role.USER,
    var joinedCommunities: List<String> = emptyList(),
    var managedCommunities: List<String> = emptyList(),
    var permissions: List<String> = emptyList(),
    var isSuspended: Boolean = false
) {
    // Required for Firestore deserialization
    constructor() : this(id = "")

    // Helper to ensure lists are never null even after Firestore deserialization
    fun safeJoined(): List<String> = joinedCommunities ?: emptyList()
    fun safeManaged(): List<String> = managedCommunities ?: emptyList()
    fun safePermissions(): List<String> = permissions ?: emptyList()
}
