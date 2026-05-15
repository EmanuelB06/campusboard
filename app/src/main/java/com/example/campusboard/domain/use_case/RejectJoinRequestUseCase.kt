package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class RejectJoinRequestUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        currentUser: User?,
        requestId: String,
        allRequests: List<JoinRequest>
    ): Resource<Unit> {
        val request = allRequests.find { it.id == requestId }
        val canManage = when {
            currentUser?.role == Role.SUPER_ADMIN -> true
            currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true -> true
            currentUser?.role == Role.ADMIN && 
                    currentUser.safeManaged().contains(request?.community) && 
                    currentUser.safePermissions().contains("can_manage_community_requests") -> true
            else -> false
        }

        if (!canManage) {
            return Resource.Error("You don't have permission to manage requests for this community.")
        }

        return when (val result = boardRepository.updateJoinRequestStatus(requestId, "REJECTED")) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> Resource.Error(result.message ?: "Failed to reject request")
            else -> Resource.Error("Unknown error")
        }
    }
}
