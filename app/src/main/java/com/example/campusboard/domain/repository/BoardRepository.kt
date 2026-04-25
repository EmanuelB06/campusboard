package com.example.campusboard.domain.repository

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun getPosts(community: String): Flow<List<Post>>
    suspend fun createPost(post: Post): Resource<Unit>
    suspend fun deletePost(postId: String): Resource<Unit>
    suspend fun joinCommunity(userId: String, community: String): Resource<Unit>
    
    // Join Requests
    suspend fun submitJoinRequest(request: JoinRequest): Resource<Unit>
    fun getPendingJoinRequests(community: String): Flow<List<JoinRequest>>
    fun getJoinRequestsForUser(userEmail: String): Flow<List<JoinRequest>>
    suspend fun updateJoinRequestStatus(requestId: String, status: String): Resource<Unit>

    // Communities
    fun getCommunities(): Flow<List<Community>>
    suspend fun createCommunity(community: Community): Resource<Unit>
}
