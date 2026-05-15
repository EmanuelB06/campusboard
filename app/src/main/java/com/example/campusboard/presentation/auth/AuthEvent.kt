package com.example.campusboard.presentation.auth

sealed class AuthEvent {
    data class Login(val email: String, val password: String, val staySignedIn: Boolean) : AuthEvent()
    data class Register(
        val email: String,
        val username: String,
        val password: String,
        val confirmPassword: String
    ) : AuthEvent()
    data class SignUpWithGoogle(val idToken: String, val username: String, val password: String, val staySignedIn: Boolean) : AuthEvent()
    data class PrepareGoogleSignUp(val idToken: String, val username: String) : AuthEvent()
    object CancelGoogleSignUp : AuthEvent()
    data class SignInWithGoogle(val idToken: String, val staySignedIn: Boolean) : AuthEvent()
    data class SelectCommunity(val community: String) : AuthEvent()
    object ToggleMode : AuthEvent()
    object ToggleStaySignedIn : AuthEvent()
    object Logout : AuthEvent()
}
