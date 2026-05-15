package com.example.campusboard.domain.repository

import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String, staySignedIn: Boolean): Resource<User>
    suspend fun register(email: String, username: String, password: String): Resource<User>
    suspend fun signInWithGoogle(idToken: String, staySignedIn: Boolean): Resource<User>
    suspend fun signUpWithGoogle(idToken: String, username: String, password: String, staySignedIn: Boolean): Resource<User>
    suspend fun getSession(): User?
    suspend fun logout()
    suspend fun isGoogleOnly(): Boolean
    suspend fun setPassword(password: String): Resource<Unit>
    fun getAllUsers(): Flow<List<User>>
    suspend fun updateUserRole(userId: String, newRole: Role, communityToManage: String? = null)
    suspend fun toggleCommunityManagement(userId: String, community: String)
    suspend fun toggleGlobalPermission(userId: String, permission: String)
    suspend fun toggleUserSuspension(userId: String)
    suspend fun joinCommunity(userId: String, community: String): Resource<User>
    suspend fun leaveCommunity(userId: String, community: String): Resource<User>
    fun observeCurrentUser(): Flow<User?>
}
