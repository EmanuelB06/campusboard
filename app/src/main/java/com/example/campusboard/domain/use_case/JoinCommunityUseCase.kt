package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource

class JoinCommunityUseCase(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(user: User, community: String, myJoinRequests: List<com.example.campusboard.domain.model.JoinRequest>): Resource<User> {
        // Auto-leave from other non-General communities
        val joinedNonGeneral = user.safeJoined().filter { it != "General" && it != community }
        for (prevCommunity in joinedNonGeneral) {
            authRepository.leaveCommunity(user.id, prevCommunity)
        }

        // Cancel any existing pending requests
        val pendingNonGeneral = myJoinRequests
            .filter { it.status == "PENDING" && it.community != "General" }
        for (request in pendingNonGeneral) {
            boardRepository.cancelJoinRequest(request.id)
        }

        return authRepository.joinCommunity(user.id, community)
    }
}
