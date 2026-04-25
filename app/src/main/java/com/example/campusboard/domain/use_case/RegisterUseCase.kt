package com.example.campusboard.domain.use_case

import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): Resource<User> {
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            return Resource.Error("Fields cannot be empty")
        }
        if (password != confirmPassword) {
            return Resource.Error("Passwords do not match")
        }
        return repository.register(email, username, password)
    }
}
