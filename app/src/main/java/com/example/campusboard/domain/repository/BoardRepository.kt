package com.example.campusboard.domain.repository

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun getPosts(community: String): Flow<List<Post>>
    fun getPendingPosts(): Flow<List<Post>>
    suspend fun createPost(post: Post): Resource<Unit>
    suspend fun deletePost(postId: String): Resource<Unit>
    suspend fun updatePostStatus(postId: String, status: com.example.campusboard.domain.model.PostStatus): Resource<Unit>
    suspend fun joinCommunity(userId: String, community: String): Resource<Unit>
    
    // Join Requests
    suspend fun submitJoinRequest(request: JoinRequest): Resource<Unit>
    fun getPendingJoinRequests(community: String): Flow<List<JoinRequest>>
    fun getAllPendingJoinRequests(): Flow<List<JoinRequest>>
    fun getJoinRequestsForUser(userId: String): Flow<List<JoinRequest>>
    suspend fun updateJoinRequestStatus(requestId: String, status: String): Resource<Unit>
    suspend fun cancelJoinRequest(requestId: String): Resource<Unit>

    // Communities
    fun getCommunities(): Flow<List<Community>>
    suspend fun createCommunity(community: Community): Resource<Unit>
    suspend fun updateCommunity(community: Community): Resource<Unit>
    suspend fun initializeData(): Resource<Unit>
}
