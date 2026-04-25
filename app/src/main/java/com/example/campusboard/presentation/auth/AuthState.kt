package com.example.campusboard.presentation.auth

import com.example.campusboard.domain.model.User

data class AuthState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val isLoginMode: Boolean = true,
    val needsCommunitySelection: Boolean = false
)
