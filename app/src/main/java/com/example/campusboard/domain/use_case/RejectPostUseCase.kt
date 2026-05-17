package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.PostStatus
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class RejectPostUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        currentUser: User?,
        postId: String,
        pendingPosts: List<Post>,
        reason: String? = null
    ): Resource<Unit> {
        val post = pendingPosts.find { it.id == postId }
            ?: return Resource.Error("Post not found in pending approvals list.")

        val canManage = when {
            currentUser?.role == Role.SUPER_ADMIN -> true
            currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true -> true
            currentUser?.role == Role.ADMIN && 
                    currentUser.safeManaged().contains(post.community) &&
                    currentUser.safePermissions().contains("can_approve_community_posts") -> true
            else -> false
        }

        if (!canManage) {
            return Resource.Error("You don't have permission to reject posts for this community.")
        }

        return when (val result = boardRepository.updatePostStatus(postId, PostStatus.REJECTED, reason)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> Resource.Error(result.message ?: "Failed to reject post")
            else -> Resource.Error("Unknown error")
        }
    }
}
