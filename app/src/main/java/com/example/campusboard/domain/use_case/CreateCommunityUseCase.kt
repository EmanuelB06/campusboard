package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class CreateCommunityUseCase(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(
        user: User?,
        name: String,
        description: String
    ): Resource<Unit> {
        val canCreate = user?.role == Role.SUPER_ADMIN || user?.safePermissions()?.contains("can_create_community") == true
        if (!canCreate) {
            return Resource.Error("You don't have permission to create communities.")
        }

        val community = Community(
            name = name,
            description = description,
            creatorEmail = user.email
        )
        
        return boardRepository.createCommunity(community)
    }
}
