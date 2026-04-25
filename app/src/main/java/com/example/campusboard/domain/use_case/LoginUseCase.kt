package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, staySignedIn: Boolean): Resource<User> {
        if (email.isBlank() || password.isBlank()) {
            return Resource.Error("Email and password cannot be empty")
        }
        // Additional validation can go here
        return repository.login(email, password, staySignedIn)
    }
}
