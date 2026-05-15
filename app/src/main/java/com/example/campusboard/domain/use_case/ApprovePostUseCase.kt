package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.PostStatus
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class ApprovePostUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        currentUser: User?,
        postId: String,
        community: String
    ): Resource<Unit> {
        val canManage = when {
            currentUser?.role == Role.SUPER_ADMIN -> true
            currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true -> true
            currentUser?.role == Role.ADMIN && 
                    currentUser.safeManaged().contains(community) && 
                    currentUser.safePermissions().contains("can_approve_community_posts") -> true
            else -> false
        }

        if (!canManage) {
            return Resource.Error("You don't have permission to approve posts for this community.")
        }

        return when (val result = boardRepository.updatePostStatus(postId, PostStatus.APPROVED)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> Resource.Error(result.message ?: "Failed to approve post")
            else -> Resource.Error("Unknown error")
        }
    }
}
