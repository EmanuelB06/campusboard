package com.example.campusboard.presentation.board

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.User

data class BoardState(
    val posts: List<Post> = emptyList(),
    val currentUser: User? = null,
    val selectedCommunity: String = "General",
    val currentScreen: Screen = Screen.BOARD,
    val users: List<User> = emptyList(),
    val communities: List<Community> = emptyList(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showLogoutDialog: Boolean = false,
    val postToDelete: String? = null,
    val userToPromote: String? = null,
    val userToDemote: String? = null
)
