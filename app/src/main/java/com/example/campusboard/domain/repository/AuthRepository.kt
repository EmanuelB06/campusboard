package com.example.campusboard.domain.repository

import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String, staySignedIn: Boolean): Resource<User>
    suspend fun register(email: String, username: String, password: String): Resource<User>
    suspend fun signInWithGoogle(idToken: String): Resource<User>
    suspend fun signUpWithGoogle(idToken: String, username: String): Resource<User>
    suspend fun getSession(): User?
    suspend fun logout()
    fun getAllUsers(): Flow<List<User>>
    suspend fun updateUserRole(userId: String, newRole: Role)
    suspend fun joinCommunity(email: String, community: String): Resource<User>
}
