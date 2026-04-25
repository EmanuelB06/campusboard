package com.example.campusboard.presentation.board

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusboard.domain.model.*
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BoardViewModel(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository,
    val user: User,
    private val onLogout: () -> Unit = {}
) : ViewModel() {

    private val _state = mutableStateOf(BoardState(
        currentUser = user,
        selectedCommunity = user.joinedCommunities.lastOrNull() ?: "General",
        currentScreen = when {
            user.role == Role.SUPER_ADMIN -> Screen.MEMBERS
            else -> Screen.BOARD
        }
    ))
    val state: State<BoardState> = _state

    private var getPostsJob: Job? = null
    private var getUsersJob: Job? = null
    private var getRequestsJob: Job? = null
    private var getCommunitiesJob: Job? = null
    private var getUserRequestsJob: Job? = null
    
    private var pendingRoleUpdate: Pair<String, Role>? = null

    init {
        getPosts(state.value.selectedCommunity)
        getCommunities()
        getJoinRequestsForUser()
        if (user.role == Role.SUPER_ADMIN || user.role == Role.ADMIN) {
            getUsers()
            getJoinRequests()
        }
    }

    fun onEvent(event: BoardEvent) {
        when (event) {
            is BoardEvent.SelectCommunity -> {
                _state.value = _state.value.copy(selectedCommunity = event.community)
                getPosts(event.community)
                if (user.role == Role.SUPER_ADMIN || user.role == Role.ADMIN) {
                    getJoinRequests()
                }
            }
            is BoardEvent.CreatePost -> {
                createPost(event.title, event.content, event.type, event.color, event.timestamp)
            }
            
            is BoardEvent.RequestDeletePost -> {
                _state.value = _state.value.copy(postToDelete = event.postId)
            }
            is BoardEvent.CancelDeletePost -> {
                _state.value = _state.value.copy(postToDelete = null)
            }
            is BoardEvent.ConfirmDeletePost -> {
                state.value.postToDelete?.let { deletePost(it) }
                _state.value = _state.value.copy(postToDelete = null)
            }

            is BoardEvent.RequestUpdateRole -> {
                pendingRoleUpdate = Pair(event.userId, event.newRole)
                if (event.newRole == Role.ADMIN) {
                    _state.value = _state.value.copy(userToPromote = event.userId)
                } else {
                    _state.value = _state.value.copy(userToDemote = event.userId)
                }
            }
            is BoardEvent.CancelUpdateRole -> {
                pendingRoleUpdate = null
                _state.value = _state.value.copy(userToPromote = null, userToDemote = null)
            }
            is BoardEvent.ConfirmUpdateRole -> {
                pendingRoleUpdate?.let { updateUserRole(it.first, it.second) }
                pendingRoleUpdate = null
                _state.value = _state.value.copy(userToPromote = null, userToDemote = null)
            }

            is BoardEvent.RequestLogout -> {
                _state.value = _state.value.copy(showLogoutDialog = true)
            }
            is BoardEvent.CancelLogout -> {
                _state.value = _state.value.copy(showLogoutDialog = false)
            }
            is BoardEvent.ConfirmLogout -> {
                _state.value = _state.value.copy(showLogoutDialog = false)
                logout()
            }

            is BoardEvent.JoinCommunity -> {
                joinCommunity(event.community)
            }

            is BoardEvent.NavigateTo -> {
                _state.value = _state.value.copy(currentScreen = event.screen)
            }
            is BoardEvent.Logout -> {
                logout()
            }
            
            is BoardEvent.SubmitJoinRequest -> {
                submitJoinRequest(event.community)
            }
            is BoardEvent.AcceptJoinRequest -> {
                acceptJoinRequest(event.requestId, event.userEmail, event.community)
            }
            is BoardEvent.RejectJoinRequest -> {
                rejectJoinRequest(event.requestId)
            }
            is BoardEvent.CreateCommunity -> {
                createCommunity(event.name, event.description)
            }
            else -> {}
        }
    }

    private fun getCommunities() {
        getCommunitiesJob?.cancel()
        getCommunitiesJob = boardRepository.getCommunities()
            .onEach { communities ->
                _state.value = _state.value.copy(communities = communities)
            }
            .launchIn(viewModelScope)
    }

    private fun getJoinRequestsForUser() {
        getUserRequestsJob?.cancel()
        getUserRequestsJob = boardRepository.getJoinRequestsForUser(user.email)
            .onEach { requests ->
                _state.value = _state.value.copy(joinRequests = requests)
            }
            .launchIn(viewModelScope)
    }

    private fun createCommunity(name: String, description: String) {
        viewModelScope.launch {
            val community = Community(name = name, description = description, creatorEmail = user.email)
            boardRepository.createCommunity(community)
        }
    }

    private fun getJoinRequests() {
        // For Admins, this will overlap with the user's own requests in the state. 
        // In a real app, we'd use separate state variables or filter them differently.
        getRequestsJob?.cancel()
        getRequestsJob = boardRepository.getPendingJoinRequests(state.value.selectedCommunity)
            .onEach { requests ->
                _state.value = _state.value.copy(joinRequests = requests)
            }
            .launchIn(viewModelScope)
    }

    private fun submitJoinRequest(community: String) {
        viewModelScope.launch {
            val request = JoinRequest(
                userEmail = user.email,
                username = user.username,
                community = community
            )
            boardRepository.submitJoinRequest(request)
        }
    }

    private fun acceptJoinRequest(requestId: String, userEmail: String, community: String) {
        viewModelScope.launch {
            authRepository.joinCommunity(userEmail, community)
            boardRepository.updateJoinRequestStatus(requestId, "ACCEPTED")
        }
    }

    private fun rejectJoinRequest(requestId: String) {
        viewModelScope.launch {
            boardRepository.updateJoinRequestStatus(requestId, "REJECTED")
        }
    }

    private fun joinCommunity(community: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = authRepository.joinCommunity(user.email, community)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        currentUser = result.data,
                        currentScreen = Screen.BOARD,
                        selectedCommunity = community,
                        isLoading = false
                    )
                    getPosts(community)
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(error = result.message, isLoading = false)
                }
                else -> {}
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            onLogout()
        }
    }

    private fun getPosts(community: String) {
        getPostsJob?.cancel()
        getPostsJob = boardRepository.getPosts(community)
            .onEach { posts ->
                _state.value = _state.value.copy(posts = posts)
            }
            .launchIn(viewModelScope)
    }

    private fun getUsers() {
        getUsersJob?.cancel()
        getUsersJob = authRepository.getAllUsers()
            .onEach { users ->
                _state.value = _state.value.copy(users = users)
            }
            .launchIn(viewModelScope)
    }

    private fun updateUserRole(userId: String, newRole: Role) {
        viewModelScope.launch {
            authRepository.updateUserRole(userId, newRole)
            getUsers()
        }
    }

    private fun createPost(title: String, content: String, type: PostType, color: Int, timestamp: Long) {
        viewModelScope.launch {
            val post = Post(
                title = title,
                content = content,
                author = user.username,
                community = state.value.selectedCommunity,
                type = type,
                color = color,
                timestamp = timestamp
            )
            boardRepository.createPost(post)
        }
    }

    private fun deletePost(postId: String) {
        viewModelScope.launch {
            val canDelete = user.role == Role.SUPER_ADMIN || user.role == Role.ADMIN
            if (canDelete) {
                boardRepository.deletePost(postId)
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to delete posts.")
            }
        }
    }
}
