package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.*
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class CreatePostUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        user: User,
        title: String,
        content: String,
        type: PostType,
        color: Long,
        timestamp: Long,
        community: String,
        isBroadcast: Boolean = false
    ): Resource<Boolean> { // Returns true if bypassed (approved), false if pending
        if (user.isSuspended) {
            return Resource.Error("Your account is suspended. You cannot post.")
        }
        
        val isBypassed = user.role == Role.SUPER_ADMIN || 
                         (user.role == Role.ADMIN && user.safeManaged().contains(community)) ||
                         user.safePermissions().contains("bypass_approval_$community")

        val post = Post(
            title = title,
            content = content,
            author = user.username,
            community = community,
            type = type,
            color = color,
            timestamp = timestamp,
            status = if (isBypassed) PostStatus.APPROVED else PostStatus.PENDING,
            isBroadcast = isBroadcast
        )
        
        return when (val result = boardRepository.createPost(post)) {
            is Resource.Success -> Resource.Success(isBypassed)
            is Resource.Error -> Resource.Error(result.message ?: "Failed to create post")
            else -> Resource.Error("Unknown error")
        }
    }
}
