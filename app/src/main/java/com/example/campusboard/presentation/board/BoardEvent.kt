package com.example.campusboard.presentation.board

import com.example.campusboard.domain.model.PostType
import com.example.campusboard.domain.model.Role

sealed class BoardEvent {
    data class SelectCommunity(val community: String) : BoardEvent()
    data class CreatePost(val title: String, val content: String, val type: PostType, val color: Int, val timestamp: Long) : BoardEvent()
    data class RequestDeletePost(val postId: String) : BoardEvent()
    object ConfirmDeletePost : BoardEvent()
    object CancelDeletePost : BoardEvent()
    
    data class RequestUpdateRole(val userId: String, val newRole: Role) : BoardEvent()
    object ConfirmUpdateRole : BoardEvent()
    object CancelUpdateRole : BoardEvent()
    
    object RequestLogout : BoardEvent()
    object ConfirmLogout : BoardEvent()
    object CancelLogout : BoardEvent()
    
    data class JoinCommunity(val community: String) : BoardEvent()
    data class NavigateTo(val screen: Screen) : BoardEvent()
    object Logout : BoardEvent()

    // Join Request Events
    data class SubmitJoinRequest(val community: String) : BoardEvent()
    data class AcceptJoinRequest(val requestId: String, val userEmail: String, val community: String) : BoardEvent()
    data class RejectJoinRequest(val requestId: String) : BoardEvent()

    // Community Management
    data class CreateCommunity(val name: String, val description: String) : BoardEvent()
}
