package com.example.campusboard.domain.model

data class JoinRequest(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val username: String = "",
    val community: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val rejectionReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", "", "PENDING", null, System.currentTimeMillis())
}
