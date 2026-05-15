package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class DeletePostUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        currentUser: User?,
        postId: String,
        posts: List<Post>
    ): Resource<Unit> {
        val post = posts.find { it.id == postId }
        val canDelete = when (currentUser?.role) {
            Role.SUPER_ADMIN -> true
            Role.ADMIN -> currentUser.safePermissions().contains("can_delete_any_post") == true ||
                         (currentUser.safeManaged().contains(post?.community) == true && 
                          currentUser.safePermissions().contains("can_delete_community_posts") == true)
            Role.USER -> currentUser.safePermissions().contains("can_delete_any_post") == true
            else -> false
        }
        
        if (!canDelete) {
            return Resource.Error("You don't have permission to delete posts in this community.")
        }

        return boardRepository.deletePost(postId)
    }
}
