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
    initialUser: User,
    private val onLogout: () -> Unit = {}
) : ViewModel() {

    private val _state = mutableStateOf(BoardState(
        currentUser = initialUser,
        selectedCommunity = initialUser.joinedCommunities.lastOrNull() ?: "General",
        currentScreen = Screen.BOARD
    ))
    val state: State<BoardState> = _state

    private var getPostsJob: Job? = null
    private var getUsersJob: Job? = null
    private var getRequestsJob: Job? = null
    private var getPendingPostsJob: Job? = null
    private var getCommunitiesJob: Job? = null
    private var getUserRequestsJob: Job? = null
    
    private var pendingRoleUpdate: Pair<String, Role>? = null

    init {
        observeCurrentUser()
        getCommunities()
        getPosts(state.value.selectedCommunity)
    }

    private fun observeCurrentUser() {
        authRepository.observeCurrentUser()
            .onEach { user ->
                _state.value = _state.value.copy(currentUser = user)
                
                if (user != null) {
                    // Check if selected community is still accessible
                    val isAvailable = if (user.role == Role.SUPER_ADMIN) true
                    else if (user.role == Role.ADMIN) _state.value.selectedCommunity == "General" || user.safeManaged().contains(_state.value.selectedCommunity)
                    else _state.value.selectedCommunity == "General" || user.safeJoined().contains(_state.value.selectedCommunity)
                    
                    if (!isAvailable) {
                        onEvent(BoardEvent.SelectCommunity("General"))
                    }

                    // Fetch data based on permissions
                    val canManageRequests = user.role == Role.SUPER_ADMIN || 
                                           user.safePermissions().contains("can_approve_posts_globally") || 
                                           user.safePermissions().contains("can_manage_requests_globally") ||
                                           (user.role == Role.ADMIN && user.safeManaged().isNotEmpty())
                    
                    val canManageUsers = user.role == Role.SUPER_ADMIN || 
                                        user.safePermissions().contains("can_manage_roles") || 
                                        user.safePermissions().contains("can_manage_permissions") ||
                                        user.safePermissions().contains("can_manage_community_users")
                                        
                    if (canManageUsers) {
                        getUsers()
                    }
                    
                    if (canManageRequests) {
                        getJoinRequests()
                        getPendingPosts()
                    } else {
                        getJoinRequestsForUser()
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: BoardEvent) {
        when (event) {
            is BoardEvent.SelectCommunity -> {
                _state.value = _state.value.copy(selectedCommunity = event.community)
                getPosts(event.community)
                val currentUser = state.value.currentUser
                val canManageAny = currentUser?.role == Role.SUPER_ADMIN || 
                                  currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true ||
                                  currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true ||
                                  (currentUser?.role == Role.ADMIN && currentUser.safeManaged().isNotEmpty())
                if (canManageAny) {
                    getJoinRequests()
                }
            }
            is BoardEvent.CreatePost -> {
                createPost(event.title, event.content, event.type, event.color, event.timestamp, event.community, event.isBroadcast)
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
                pendingRoleUpdate?.let { updateUserRole(it.first, it.second, event.community) }
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
                _state.value = _state.value.copy(
                    currentScreen = event.screen
                )
            }

            is BoardEvent.Logout -> {
                logout()
            }
            
            is BoardEvent.SubmitJoinRequest -> {
                submitJoinRequest(event.community)
            }
            is BoardEvent.CancelJoinRequest -> {
                cancelJoinRequest(event.requestId)
            }
            is BoardEvent.AcceptJoinRequest -> {
                acceptJoinRequest(event.requestId, event.userId, event.community)
            }
            is BoardEvent.RejectJoinRequest -> {
                rejectJoinRequest(event.requestId)
            }
            is BoardEvent.AcceptPostRequest -> {
                acceptPostRequest(event.postId, event.community)
            }
            is BoardEvent.RejectPostRequest -> {
                rejectPostRequest(event.postId)
            }
            is BoardEvent.CreateCommunity -> {
                createCommunity(event.name, event.description)
            }
            is BoardEvent.UpdateCommunity -> {
                updateCommunity(event.name, event.description)
            }
            is BoardEvent.OpenCommunityEditor -> {
                _state.value = _state.value.copy(communityToEdit = event.community)
            }
            is BoardEvent.CloseCommunityEditor -> {
                _state.value = _state.value.copy(communityToEdit = null)
            }
            is BoardEvent.ViewCommunityMembers -> {
                viewCommunityMembers(event.community)
            }
            is BoardEvent.OpenPermissionManager -> {
                _state.value = _state.value.copy(userToManagePermissions = event.user)
            }
            is BoardEvent.ClosePermissionManager -> {
                _state.value = _state.value.copy(userToManagePermissions = null)
            }
            is BoardEvent.RequestToggleCommunityManagement -> {
                // Trigger warning for any community management toggle (add or remove)
                _state.value = _state.value.copy(communityWarning = Pair(event.userId, event.community))
            }
            is BoardEvent.ToggleCommunityManagement -> {
                toggleCommunityManagement(event.userId, event.community)
                _state.value = _state.value.copy(communityWarning = null)
            }
            is BoardEvent.CancelToggleCommunityManagement -> {
                _state.value = _state.value.copy(communityWarning = null)
            }
            is BoardEvent.ToggleGlobalPermission -> {
                toggleGlobalPermission(event.userId, event.permission)
            }
            is BoardEvent.ToggleUserSuspension -> {
                toggleUserSuspension(event.userId)
            }
            is BoardEvent.Refresh -> {
                viewModelScope.launch {
                    _state.value = _state.value.copy(isLoading = true)
                    
                    val currentUser = state.value.currentUser
                    val canManageRequests = currentUser?.role == Role.SUPER_ADMIN || 
                                          currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true ||
                                          currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true ||
                                          (currentUser?.role == Role.ADMIN && currentUser.safeManaged().isNotEmpty())
                    
                    val canManageUsers = currentUser?.role == Role.SUPER_ADMIN || 
                                        currentUser?.safePermissions()?.contains("can_manage_roles") == true || 
                                        currentUser?.safePermissions()?.contains("can_manage_permissions") == true ||
                                        currentUser?.safePermissions()?.contains("can_manage_community_users") == true

                    // Refresh all data
                    getPosts(_state.value.selectedCommunity)
                    getCommunities()
                    
                    if (canManageUsers) {
                        getUsers()
                    }
                    
                    if (canManageRequests) {
                        getJoinRequests()
                        getPendingPosts()
                    } else {
                        getJoinRequestsForUser()
                    }
                    
                    kotlinx.coroutines.delay(800) // Visual feedback
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
            is BoardEvent.DismissError -> {
                _state.value = _state.value.copy(error = null)
            }
        }
    }

    private fun toggleCommunityManagement(userId: String, community: String) {
        viewModelScope.launch {
            authRepository.toggleCommunityManagement(userId, community)
            // If we are currently managing permissions for this user, update the state object
            val currentManaging = _state.value.userToManagePermissions
            if (currentManaging != null && currentManaging.id == userId) {
                val updatedManaged = currentManaging.safeManaged().toMutableList()
                if (updatedManaged.contains(community)) {
                    updatedManaged.remove(community)
                } else {
                    updatedManaged.add(community)
                }
                _state.value = _state.value.copy(
                    userToManagePermissions = currentManaging.copy(managedCommunities = updatedManaged)
                )
            }
        }
    }

    private fun toggleUserSuspension(userId: String) {
        viewModelScope.launch {
            authRepository.toggleUserSuspension(userId)
            val currentManaging = _state.value.userToManagePermissions
            if (currentManaging != null && currentManaging.id == userId) {
                _state.value = _state.value.copy(
                    userToManagePermissions = currentManaging.copy(isSuspended = !currentManaging.isSuspended)
                )
            }
        }
    }

    private fun toggleGlobalPermission(userId: String, permission: String) {
        viewModelScope.launch {
            authRepository.toggleGlobalPermission(userId, permission)
            val currentManaging = _state.value.userToManagePermissions
            if (currentManaging != null && currentManaging.id == userId) {
                val updatedPerms = currentManaging.safePermissions().toMutableList()
                if (updatedPerms.contains(permission)) {
                    updatedPerms.remove(permission)
                } else {
                    updatedPerms.add(permission)
                }
                _state.value = _state.value.copy(
                    userToManagePermissions = currentManaging.copy(permissions = updatedPerms)
                )
            }
        }
    }

    private fun viewCommunityMembers(community: Community) {
        getUsers() // Ensure users are loaded
        val members = state.value.users.filter { it.safeJoined().contains(community.name) }
        _state.value = _state.value.copy(
            currentScreen = Screen.COMMUNITY_MEMBERS,
            communityToViewMembers = community,
            membersToView = members
        )
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
        val currentUser = state.value.currentUser ?: return
        getUserRequestsJob?.cancel()
        getUserRequestsJob = boardRepository.getJoinRequestsForUser(currentUser.id)
            .onEach { requests ->
                _state.value = _state.value.copy(myJoinRequests = requests)
            }
            .launchIn(viewModelScope)
    }

    private fun createCommunity(name: String, description: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser
            val canCreate = currentUser?.role == Role.SUPER_ADMIN || currentUser?.safePermissions()?.contains("can_create_community") == true
            if (canCreate) {
                val community = Community(name = name, description = description, creatorEmail = currentUser.email)
                boardRepository.createCommunity(community)
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to create communities.")
            }
        }
    }

    private fun updateCommunity(name: String, description: String) {
        viewModelScope.launch {
            val community = _state.value.communityToEdit ?: return@launch
            val updated = community.copy(description = description)
            val result = boardRepository.updateCommunity(updated)
            if (result is Resource.Success) {
                _state.value = _state.value.copy(communityToEdit = null)
                getCommunities()
            } else {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun getJoinRequests() {
        getRequestsJob?.cancel()
        getRequestsJob = boardRepository.getAllPendingJoinRequests()
            .onEach { requests ->
                _state.value = _state.value.copy(joinRequests = requests)
            }
            .launchIn(viewModelScope)
    }

    private fun getPendingPosts() {
        getPendingPostsJob?.cancel()
        getPendingPostsJob = boardRepository.getPendingPosts()
            .onEach { posts ->
                _state.value = _state.value.copy(pendingPosts = posts)
            }
            .launchIn(viewModelScope)
    }

    private fun submitJoinRequest(community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser ?: return@launch
            // Cancel any existing pending requests for non-General communities
            val pendingNonGeneral = state.value.myJoinRequests
                .filter { it.status == "PENDING" && it.community != "General" }
            
            for (request in pendingNonGeneral) {
                boardRepository.cancelJoinRequest(request.id)
            }

            val request = JoinRequest(
                userId = currentUser.id,
                userEmail = currentUser.email,
                username = currentUser.username,
                community = community
            )
            boardRepository.submitJoinRequest(request)
        }
    }

    private fun cancelJoinRequest(requestId: String) {
        viewModelScope.launch {
            boardRepository.cancelJoinRequest(requestId)
        }
    }

    private fun acceptJoinRequest(requestId: String, userId: String, community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser
            val canManage = when (currentUser?.role) {
                Role.SUPER_ADMIN -> true
                Role.ADMIN -> currentUser.safeManaged().contains(community) || currentUser.safePermissions().contains("can_manage_requests_globally")
                Role.USER -> currentUser.safePermissions().contains("can_manage_requests_globally")
                else -> false
            }

            if (canManage) {
                // Auto-leave from other non-General communities for the target user
                val targetUser = state.value.users.find { it.id == userId }
                targetUser?.let { tUser ->
                    val joinedNonGeneral = tUser.safeJoined().filter { it != "General" && it != community }
                    for (prevCommunity in joinedNonGeneral) {
                        authRepository.leaveCommunity(userId, prevCommunity)
                    }
                }

                authRepository.joinCommunity(userId, community)
                boardRepository.updateJoinRequestStatus(requestId, "ACCEPTED")
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to manage requests for this community.")
            }
        }
    }

    private fun rejectJoinRequest(requestId: String) {
        viewModelScope.launch {
            val request = state.value.joinRequests.find { it.id == requestId }
            val currentUser = state.value.currentUser
            val canManage = when (currentUser?.role) {
                Role.SUPER_ADMIN -> true
                Role.ADMIN -> currentUser.safeManaged().contains(request?.community) || currentUser.safePermissions().contains("can_manage_requests_globally")
                Role.USER -> currentUser.safePermissions().contains("can_manage_requests_globally")
                else -> false
            }

            if (canManage) {
                boardRepository.updateJoinRequestStatus(requestId, "REJECTED")
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to manage requests for this community.")
            }
        }
    }

    private fun acceptPostRequest(postId: String, community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser
            val canManage = when (currentUser?.role) {
                Role.SUPER_ADMIN -> true
                Role.ADMIN -> currentUser.safeManaged().contains(community) || currentUser.safePermissions().contains("can_approve_posts_globally")
                Role.USER -> currentUser.safePermissions().contains("can_approve_posts_globally")
                else -> false
            }

            if (canManage) {
                boardRepository.updatePostStatus(postId, PostStatus.APPROVED)
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to approve posts for this community.")
            }
        }
    }

    private fun rejectPostRequest(postId: String) {
        viewModelScope.launch {
            val post = state.value.pendingPosts.find { it.id == postId }
            val currentUser = state.value.currentUser
            val canManage = when (currentUser?.role) {
                Role.SUPER_ADMIN -> true
                Role.ADMIN -> currentUser.safeManaged().contains(post?.community) || currentUser.safePermissions().contains("can_approve_posts_globally")
                Role.USER -> currentUser.safePermissions().contains("can_approve_posts_globally")
                else -> false
            }

            if (canManage) {
                boardRepository.updatePostStatus(postId, PostStatus.REJECTED)
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to reject posts for this community.")
            }
        }
    }

    private fun joinCommunity(community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser ?: return@launch
            _state.value = _state.value.copy(isLoading = true)

            // Auto-leave from other non-General communities
            val joinedNonGeneral = currentUser.safeJoined().filter { it != "General" && it != community }
            for (prevCommunity in joinedNonGeneral) {
                authRepository.leaveCommunity(currentUser.id, prevCommunity)
            }

            // Cancel any existing pending requests
            val pendingNonGeneral = state.value.myJoinRequests
                .filter { it.status == "PENDING" && it.community != "General" }
            for (request in pendingNonGeneral) {
                boardRepository.cancelJoinRequest(request.id)
            }

            when (val result = authRepository.joinCommunity(currentUser.id, community)) {
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
                val updatedMembersToView = state.value.communityToViewMembers?.let { community ->
                    users.filter { it.safeJoined().contains(community.name) }
                } ?: emptyList()
                
                val updatedUserToManage = state.value.userToManagePermissions?.let { target ->
                    users.find { it.id == target.id }
                }

                _state.value = _state.value.copy(
                    users = users,
                    userToManagePermissions = updatedUserToManage ?: state.value.userToManagePermissions,
                    membersToView = if (state.value.currentScreen == Screen.COMMUNITY_MEMBERS) updatedMembersToView else state.value.membersToView
                )
            }
            .launchIn(viewModelScope)
    }

    private fun updateUserRole(userId: String, newRole: Role, communityToManage: String? = null) {
        viewModelScope.launch {
            authRepository.updateUserRole(userId, newRole, communityToManage)
            getUsers()
        }
    }

    private fun createPost(title: String, content: String, type: PostType, color: Long, timestamp: Long, community: String, isBroadcast: Boolean = false) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser ?: return@launch

            if (currentUser.isSuspended) {
                _state.value = _state.value.copy(error = "Your account is suspended. You cannot post.")
                return@launch
            }
            
            val isBypassed = community == "General" || 
                             currentUser.role == Role.SUPER_ADMIN || 
                             currentUser.role == Role.ADMIN ||
                             currentUser.safePermissions().contains("bypass_approval_$community") ||
                             currentUser.safeManaged().contains(community)

            val post = Post(
                title = title,
                content = content,
                author = currentUser.username,
                community = community,
                type = type,
                color = color,
                timestamp = timestamp,
                status = if (isBypassed) PostStatus.APPROVED else PostStatus.PENDING,
                isBroadcast = isBroadcast
            )
            _state.value = _state.value.copy(isLoading = true)
            val result = boardRepository.createPost(post)
            _state.value = _state.value.copy(isLoading = false)

            when (result) {
                is Resource.Success -> {
                    if (isBypassed) {
                        _state.value = _state.value.copy(currentScreen = Screen.BOARD)
                    } else {
                        _state.value = _state.value.copy(
                            error = "Your post has been submitted for approval.",
                            currentScreen = Screen.BOARD
                        )
                    }
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(error = result.message ?: "Failed to create post")
                }
                else -> {}
            }
        }
    }

    private fun deletePost(postId: String) {
        viewModelScope.launch {
            val post = state.value.posts.find { it.id == postId }
            val currentUser = state.value.currentUser
            val canDelete = when (currentUser?.role) {
                Role.SUPER_ADMIN -> true
                Role.ADMIN -> currentUser.safePermissions().contains("can_delete_any_post") == true ||
                             (currentUser.safeManaged().contains(post?.community) == true && 
                              currentUser.safePermissions().contains("can_delete_community_posts") == true)
                Role.USER -> currentUser.safePermissions().contains("can_delete_any_post") == true
                else -> false
            }
            
            if (canDelete) {
                boardRepository.deletePost(postId)
            } else {
                _state.value = _state.value.copy(error = "You don't have permission to delete posts in this community.")
            }
        }
    }
}
