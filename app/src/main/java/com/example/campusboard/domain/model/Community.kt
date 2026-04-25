package com.example.campusboard.domain.model

data class Community(
    val name: String = "",
    val description: String = "",
    val creatorEmail: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", System.currentTimeMillis())
}
