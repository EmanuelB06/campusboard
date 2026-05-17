package com.example.campusboard.presentation.board

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.PostType
import com.example.campusboard.domain.model.Role

sealed class BoardEvent {
    data class SelectCommunity(val community: String) : BoardEvent()
    data class CreatePost(val title: String, val content: String, val type: PostType, val color: Long, val style: Int, val timestamp: Long, val community: String, val isBroadcast: Boolean = false) : BoardEvent()
    data class RequestDeletePost(val postId: String) : BoardEvent()
    object ConfirmDeletePost : BoardEvent()
    object CancelDeletePost : BoardEvent()
    
    data class RequestUpdateRole(val userId: String, val newRole: Role) : BoardEvent()
    data class ConfirmUpdateRole(val community: String? = null) : BoardEvent()
    object CancelUpdateRole : BoardEvent()
    
    object RequestLogout : BoardEvent()
    object ConfirmLogout : BoardEvent()
    object CancelLogout : BoardEvent()
    
    data class JoinCommunity(val community: String) : BoardEvent()
    data class LeaveCommunity(val community: String) : BoardEvent()
    data class NavigateTo(val screen: Screen) : BoardEvent()
    object Logout : BoardEvent()

    // Join Request Events
    data class SubmitJoinRequest(val community: String) : BoardEvent()
    data class CancelJoinRequest(val requestId: String) : BoardEvent()
    data class RequestAcceptJoinRequest(val request: JoinRequest) : BoardEvent()
    object ConfirmAcceptJoinRequest : BoardEvent()
    object CancelAcceptJoinRequest : BoardEvent()
    data class RequestRejectJoinRequest(val request: JoinRequest) : BoardEvent()
    object ConfirmRejectJoinRequest : BoardEvent()
    object CancelRejectJoinRequest : BoardEvent()
    data class AcceptJoinRequest(val requestId: String, val userId: String, val community: String) : BoardEvent()
    data class RejectJoinRequest(val requestId: String) : BoardEvent()

    // Post Management
    data class RequestAcceptPost(val post: Post) : BoardEvent()
    object ConfirmAcceptPost : BoardEvent()
    object CancelAcceptPost : BoardEvent()
    data class RequestRejectPost(val post: Post) : BoardEvent()
    object ConfirmRejectPost : BoardEvent()
    object CancelRejectPost : BoardEvent()
    data class AcceptPostRequest(val postId: String, val community: String) : BoardEvent()
    data class RejectPostRequest(val postId: String) : BoardEvent()
    
    data class UpdateRejectionReason(val reason: String) : BoardEvent()

    // Community Management
    data class CreateCommunity(val name: String, val description: String) : BoardEvent()
    data class UpdateCommunity(val name: String, val description: String) : BoardEvent()
    data class OpenCommunityEditor(val community: Community) : BoardEvent()
    object CloseCommunityEditor : BoardEvent()
    data class ViewCommunityMembers(val community: Community) : BoardEvent()
    data class OpenPermissionManager(val user: com.example.campusboard.domain.model.User) : BoardEvent()
    object ClosePermissionManager : BoardEvent()
    data class RequestToggleCommunityManagement(val userId: String, val community: String) : BoardEvent()
    data class ToggleCommunityManagement(val userId: String, val community: String) : BoardEvent()
    object CancelToggleCommunityManagement : BoardEvent()
    data class ToggleGlobalPermission(val userId: String, val permission: String) : BoardEvent()
    data class ToggleUserSuspension(val userId: String) : BoardEvent()
    object Refresh : BoardEvent()
    object DismissError : BoardEvent()
    
    // Password for Google Users
    data class SetPassword(val password: String) : BoardEvent()
    object DismissSetPasswordDialog : BoardEvent()
}
