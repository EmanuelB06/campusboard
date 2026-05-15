package com.example.campusboard.presentation.board

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusboard.domain.model.*
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.use_case.*
import com.example.campusboard.domain.util.Resource
import com.example.campusboard.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BoardViewModel(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository,
    private val notificationHelper: NotificationHelper,
    private val joinCommunityUseCase: JoinCommunityUseCase,
    private val leaveCommunityUseCase: LeaveCommunityUseCase,
    private val createPostUseCase: CreatePostUseCase,
    private val approveJoinRequestUseCase: ApproveJoinRequestUseCase,
    private val rejectJoinRequestUseCase: RejectJoinRequestUseCase,
    private val approvePostUseCase: ApprovePostUseCase,
    private val rejectPostUseCase: RejectPostUseCase,
    private val createCommunityUseCase: CreateCommunityUseCase,
    private val deletePostUseCase: DeletePostUseCase,
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

    private val knownPostIds = mutableSetOf<String>()
    private var isFirstPostLoad = true

    init {
        observeCurrentUser()
        getCommunities()
        getPosts(state.value.selectedCommunity)
        checkIfPasswordRequired()
        setupPostNotifications()
        
        // Always subscribe to general topic (lowercase)
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
        FirebaseMessaging.getInstance().subscribeToTopic("general")
    }

    private fun setupPostNotifications() {
        // Observe all approved posts to notify users of new content
        boardRepository.getAllApprovedPosts()
            .onEach { posts ->
                val currentUser = state.value.currentUser ?: return@onEach
                
                // On first load, we remember existing posts so we only notify for TRULY new ones
                if (isFirstPostLoad) {
                    knownPostIds.addAll(posts.map { it.id })
                    isFirstPostLoad = false
                    return@onEach
                }

                // Filter for posts we haven't seen yet
                val newPosts = posts.filter { post ->
                    val isNew = !knownPostIds.contains(post.id)
                    val isNotMe = post.author != currentUser.username
                    val isInMyCommunity = post.isBroadcast || 
                                         post.community.equals("General", ignoreCase = true) || 
                                         currentUser.safeJoined().any { it.equals(post.community, ignoreCase = true) }
                    
                    isNew && isNotMe && isInMyCommunity
                }.sortedBy { it.timestamp }

                newPosts.forEach { post ->
                    knownPostIds.add(post.id)
                    
                    // MATCHING YOUR SNIPPET STYLE:
                    val displayCommunity = if (post.isBroadcast) "Global Board" else post.community
                    val snippetContent = if (post.content.length > 50) post.content.substring(0, 50) + "..." else post.content
                    
                    notificationHelper.showNotification(
                        title = "New Note in $displayCommunity",
                        message = "${post.author}: $snippetContent"
                    )
                }
            }
            .launchIn(viewModelScope)
    }


    private fun checkIfPasswordRequired() {
        viewModelScope.launch {
            if (authRepository.isGoogleOnly()) {
                _state.value = _state.value.copy(showSetPasswordDialog = true)
            }
        }
    }

    private fun observeCurrentUser() {
        authRepository.observeCurrentUser()
            .onEach { user ->
                _state.value = _state.value.copy(currentUser = user)
                
                if (user != null) {
                    // Sync topic subscriptions for all joined communities
                    user.safeJoined().forEach { community ->
                        val topic = community.lowercase().replace(" ", "_")
                        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                        android.util.Log.d("FCM_TEST", "Subscribed to topic: $topic")
                    }
                    
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
                                           (user.role == Role.ADMIN && user.safeManaged().isNotEmpty() && 
                                            (user.safePermissions().contains("can_approve_community_posts") || 
                                             user.safePermissions().contains("can_manage_community_requests")))
                    
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
                                  (currentUser?.role == Role.ADMIN && currentUser.safeManaged().isNotEmpty() && 
                                   (currentUser.safePermissions().contains("can_approve_community_posts") || 
                                    currentUser.safePermissions().contains("can_manage_community_requests")))
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

            is BoardEvent.LeaveCommunity -> {
                leaveCommunity(event.community)
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
                                          (currentUser?.role == Role.ADMIN && currentUser.safeManaged().isNotEmpty() && 
                                           (currentUser.safePermissions().contains("can_approve_community_posts") || 
                                            currentUser.safePermissions().contains("can_manage_community_requests")))
                    
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
            is BoardEvent.SetPassword -> {
                setPassword(event.password)
            }
            is BoardEvent.DismissSetPasswordDialog -> {
                _state.value = _state.value.copy(showSetPasswordDialog = false)
            }
        }
    }

    private fun setPassword(password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = authRepository.setPassword(password)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(isLoading = false, showSetPasswordDialog = false)
                    notificationHelper.showNotification("Success", "Password set successfully. You can now use it to login.")
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun toggleCommunityManagement(userId: String, community: String) {
        // Optimistic update
        val currentState = _state.value
        val currentManaging = currentState.userToManagePermissions
        if (currentManaging != null && currentManaging.id == userId) {
            val updatedManaged = currentManaging.safeManaged().toMutableList()
            if (updatedManaged.contains(community)) {
                updatedManaged.remove(community)
            } else {
                updatedManaged.add(community)
            }
            _state.value = currentState.copy(
                userToManagePermissions = currentManaging.copy(managedCommunities = updatedManaged)
            )
        }

        viewModelScope.launch {
            authRepository.toggleCommunityManagement(userId, community)
            // Note: We don't manually update state after the call anymore 
            // because the getUsers() Firestore listener will sync it.
        }
    }

    private fun toggleUserSuspension(userId: String) {
        // Optimistic update
        val currentState = _state.value
        val currentManaging = currentState.userToManagePermissions
        if (currentManaging != null && currentManaging.id == userId) {
            _state.value = currentState.copy(
                userToManagePermissions = currentManaging.copy(isSuspended = !currentManaging.isSuspended)
            )
        }

        viewModelScope.launch {
            authRepository.toggleUserSuspension(userId)
        }
    }

    private fun toggleGlobalPermission(userId: String, permission: String) {
        // Optimistic update
        val currentState = _state.value
        val currentManaging = currentState.userToManagePermissions
        if (currentManaging != null && currentManaging.id == userId) {
            val updatedPerms = currentManaging.safePermissions().toMutableList()
            if (updatedPerms.contains(permission)) {
                updatedPerms.remove(permission)
            } else {
                updatedPerms.add(permission)
            }
            _state.value = currentState.copy(
                userToManagePermissions = currentManaging.copy(permissions = updatedPerms)
            )
        }

        viewModelScope.launch {
            authRepository.toggleGlobalPermission(userId, permission)
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
            _state.value = _state.value.copy(isLoading = true)
            val result = createCommunityUseCase(state.value.currentUser, name, description)
            _state.value = _state.value.copy(isLoading = false)
            if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun updateCommunity(name: String, description: String) {
        viewModelScope.launch {
            val community = _state.value.communityToEdit ?: return@launch
            val updated = community.copy(name = name, description = description)
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
                val user = state.value.currentUser ?: return@onEach
                
                // Filter requests this user is actually allowed to manage
                val manageableRequests = if (user.role == Role.SUPER_ADMIN || user.safePermissions().contains("can_manage_requests_globally")) {
                    requests
                } else if (user.role == Role.ADMIN) {
                    requests.filter { user.safeManaged().contains(it.community) }
                } else {
                    emptyList()
                }

                val prevManageableCount = if (user.role == Role.SUPER_ADMIN || user.safePermissions().contains("can_manage_requests_globally")) {
                    _state.value.joinRequests.size
                } else if (user.role == Role.ADMIN) {
                    _state.value.joinRequests.count { user.safeManaged().contains(it.community) }
                } else {
                    0
                }

                // Notify only if new manageable requests arrived
                if (manageableRequests.size > prevManageableCount) {
                    val newCount = manageableRequests.size - prevManageableCount
                    notificationHelper.showNotification(
                        "New Join Requests",
                        "You have $newCount new community join request(s) to review."
                    )
                }
                _state.value = _state.value.copy(joinRequests = requests)
            }
            .launchIn(viewModelScope)
    }

    private fun getPendingPosts() {
        getPendingPostsJob?.cancel()
        getPendingPostsJob = boardRepository.getPendingPosts()
            .onEach { posts ->
                val user = state.value.currentUser ?: return@onEach
                
                // Filter posts this user is actually allowed to approve
                val manageablePosts = if (user.role == Role.SUPER_ADMIN || user.safePermissions().contains("can_approve_posts_globally")) {
                    posts
                } else if (user.role == Role.ADMIN) {
                    posts.filter { user.safeManaged().contains(it.community) }
                } else {
                    emptyList()
                }

                val prevManageableCount = if (user.role == Role.SUPER_ADMIN || user.safePermissions().contains("can_approve_posts_globally")) {
                    _state.value.pendingPosts.size
                } else if (user.role == Role.ADMIN) {
                    _state.value.pendingPosts.count { user.safeManaged().contains(it.community) }
                } else {
                    0
                }

                // Notify only if new manageable posts arrived for approval
                if (manageablePosts.size > prevManageableCount) {
                    notificationHelper.showNotification(
                        "Pending Posts",
                        "New posts are waiting for your approval."
                    )
                }
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
            _state.value = _state.value.copy(isLoading = true)
            val result = approveJoinRequestUseCase(state.value.currentUser, requestId, userId, community)
            _state.value = _state.value.copy(isLoading = false)
            
            if (result is Resource.Success) {
                notificationHelper.showNotification(
                    "Request Accepted",
                    "You have accepted $userId's request to join $community."
                )
            } else if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun rejectJoinRequest(requestId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = rejectJoinRequestUseCase(state.value.currentUser, requestId, state.value.joinRequests)
            _state.value = _state.value.copy(isLoading = false)
            
            if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun acceptPostRequest(postId: String, community: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = approvePostUseCase(state.value.currentUser, postId, community)
            _state.value = _state.value.copy(isLoading = false)

            if (result is Resource.Success) {
                notificationHelper.showNotification(
                    "Post Approved",
                    "The post has been successfully approved and is now live."
                )
            } else if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun rejectPostRequest(postId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = rejectPostUseCase(state.value.currentUser, postId, state.value.pendingPosts)
            _state.value = _state.value.copy(isLoading = false)

            if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    private fun joinCommunity(community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser ?: return@launch
            _state.value = _state.value.copy(isLoading = true)

            when (val result = joinCommunityUseCase(currentUser, community, state.value.myJoinRequests)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        currentUser = result.data,
                        currentScreen = Screen.BOARD,
                        selectedCommunity = community,
                        isLoading = false
                    )
                    FirebaseMessaging.getInstance().subscribeToTopic(community.replace(" ", "_"))
                    getPosts(community)
                    notificationHelper.showNotification(
                        "Community Joined",
                        "You have successfully joined $community"
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(error = result.message, isLoading = false)
                }
                else -> {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun leaveCommunity(community: String) {
        viewModelScope.launch {
            val currentUser = state.value.currentUser ?: return@launch
            _state.value = _state.value.copy(isLoading = true)

            when (val result = leaveCommunityUseCase(currentUser, community)) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        currentUser = result.data,
                        selectedCommunity = "General",
                        isLoading = false
                    )
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(community.replace(" ", "_"))
                    getPosts("General")
                    notificationHelper.showNotification(
                        "Community Left",
                        "You have left $community"
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(error = result.message, isLoading = false)
                }
                else -> {
                    _state.value = _state.value.copy(isLoading = false)
                }
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
            _state.value = _state.value.copy(isLoading = true)

            when (val result = createPostUseCase(currentUser, title, content, type, color, timestamp, community, isBroadcast)) {
                is Resource.Success -> {
                    val isBypassed = result.data == true
                    if (isBypassed) {
                        _state.value = _state.value.copy(
                            currentScreen = Screen.BOARD,
                            selectedCommunity = community,
                            isLoading = false
                        )
                        getPosts(community)
                        notificationHelper.showNotification("Post Created", "Your post has been published in $community")
                    } else {
                        _state.value = _state.value.copy(
                            error = "Your post has been submitted for approval.",
                            currentScreen = Screen.BOARD,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(error = result.message, isLoading = false)
                }
                else -> {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun deletePost(postId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = deletePostUseCase(state.value.currentUser, postId, state.value.posts)
            _state.value = _state.value.copy(isLoading = false)
            
            if (result is Resource.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }
}
