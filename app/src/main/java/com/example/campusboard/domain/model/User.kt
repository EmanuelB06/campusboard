package com.example.campusboard.domain.model

data class User(
    val email: String = "",
    val username: String = "",
    val role: Role = Role.USER,
    val joinedCommunities: List<String> = emptyList()
) {
    // No-argument constructor for Firestore
    constructor() : this("", "", Role.USER, emptyList())
}
