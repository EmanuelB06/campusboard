package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class ApproveJoinRequestUseCase(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        currentUser: User?,
        requestId: String,
        targetUserId: String,
        community: String
    ): Resource<Unit> {
        val canManage = when (currentUser?.role) {
            Role.SUPER_ADMIN -> true
            Role.ADMIN -> currentUser.safeManaged().contains(community) || currentUser.safePermissions().contains("can_manage_requests_globally")
            Role.USER -> currentUser.safePermissions().contains("can_manage_requests_globally")
            else -> false
        }

        if (!canManage) {
            return Resource.Error("You don't have permission to manage requests for this community.")
        }

        return when (val joinResult = authRepository.joinCommunity(targetUserId, community)) {
            is Resource.Success -> {
                boardRepository.updateJoinRequestStatus(requestId, "ACCEPTED")
                Resource.Success(Unit)
            }
            is Resource.Error -> Resource.Error(joinResult.message ?: "Failed to join community")
            else -> Resource.Error("Unknown error")
        }
    }
}
