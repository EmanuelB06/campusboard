package com.example.campusboard.presentation.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.use_case.LoginUseCase
import com.example.campusboard.domain.use_case.RegisterUseCase
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _state = mutableStateOf(AuthState())
    val state: State<AuthState> = _state

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.ToggleMode -> {
                _state.value = _state.value.copy(isLoginMode = !_state.value.isLoginMode, error = null)
            }
            is AuthEvent.ToggleStaySignedIn -> {
                _state.value = _state.value.copy(staySignedIn = !_state.value.staySignedIn)
            }
            is AuthEvent.Login -> {
                login(event.email, event.password, event.staySignedIn)
            }
            is AuthEvent.Register -> {
                register(event.email, username = event.username, event.password, event.confirmPassword)
            }
            is AuthEvent.SignInWithGoogle -> {
                signInWithGoogle(event.idToken, event.staySignedIn)
            }
            is AuthEvent.SignUpWithGoogle -> {
                signUpWithGoogle(event.idToken, event.username, event.staySignedIn)
            }
            is AuthEvent.SelectCommunity -> {
                selectCommunity(event.community)
            }
            is AuthEvent.Logout -> {
                logout()
            }
        }
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, isLoading = false)
    }

    private fun login(email: String, password: String, staySignedIn: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = loginUseCase(email, password, staySignedIn)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(isLoading = false, user = result.data)
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun register(email: String, username: String, password: String, confirmPassword: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = registerUseCase(email, username, password, confirmPassword)) {
                is Resource.Success -> {
                    val user = result.data
                    val needsSelection = user?.role == Role.USER
                    _state.value = _state.value.copy(
                        isLoading = false,
                        user = user,
                        needsCommunitySelection = needsSelection
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun signInWithGoogle(idToken: String, staySignedIn: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = authRepository.signInWithGoogle(idToken, staySignedIn)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(isLoading = false, user = result.data)
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun signUpWithGoogle(idToken: String, username: String, staySignedIn: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = authRepository.signUpWithGoogle(idToken, username, staySignedIn)) {
                is Resource.Success -> {
                    val user = result.data
                    val needsSelection = user?.role == Role.USER
                    _state.value = _state.value.copy(
                        isLoading = false,
                        user = user,
                        needsCommunitySelection = needsSelection
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun selectCommunity(community: String) {
        viewModelScope.launch {
            val user = _state.value.user ?: return@launch
            _state.value = _state.value.copy(isLoading = true)
            when (val result = authRepository.joinCommunity(user.id, community)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(isLoading = false, user = result.data, needsCommunitySelection = false)
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = _state.value.copy(user = null, needsCommunitySelection = false)
        }
    }
}
