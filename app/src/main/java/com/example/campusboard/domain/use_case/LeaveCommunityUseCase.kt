package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource

class LeaveCommunityUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(userId: String, community: String): Resource<User> {
        if (community == "General") {
            return Resource.Error("You cannot leave the General community")
        }
        return authRepository.leaveCommunity(userId, community)
    }
}
