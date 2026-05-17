package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.*
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class CreatePostUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        user: User,
        post: Post
    ): Resource<Boolean> { // Returns true if bypassed (approved), false if pending
        if (user.isSuspended) {
            return Resource.Error("Your account is suspended. You cannot post.")
        }
        
        val isBypassed = user.role == Role.SUPER_ADMIN ||
                         (user.role == Role.ADMIN && user.safeManaged().contains(post.community)) ||
                         user.safePermissions().contains("bypass_approval_${post.community}")

        val finalPost = post.copy(
            status = if (isBypassed) PostStatus.APPROVED else PostStatus.PENDING
        )
        
        return when (val result = boardRepository.createPost(finalPost)) {
            is Resource.Success -> Resource.Success(isBypassed)
            is Resource.Error -> Resource.Error(result.message ?: "Failed to create post")
            else -> Resource.Error("Unknown error")
        }
    }
}
