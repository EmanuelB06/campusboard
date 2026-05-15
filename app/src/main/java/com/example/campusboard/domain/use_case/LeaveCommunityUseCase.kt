package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource

class LeaveCommunityUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(user: User, community: String): Resource<User> {
        if (community == "General") {
            return Resource.Error("You cannot leave the General community")
        }

        if (user.role == Role.ADMIN && user.safeManaged().contains(community)) {
            return Resource.Error("You are an administrator of this community. You must be unassigned by a Super Admin before leaving.")
        }

        return authRepository.leaveCommunity(user.id, community)
    }
}
