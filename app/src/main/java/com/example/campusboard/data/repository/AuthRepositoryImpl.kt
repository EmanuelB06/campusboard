package com.example.campusboard.data.repository

import android.content.Context
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(context: Context) : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val STAY_SIGNED_IN_KEY = "stay_signed_in"
    
    private val _currentUserFlow = MutableStateFlow<User?>(null)
    private val currentUserFlow = _currentUserFlow.asStateFlow()
    private var currentUserListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        // Handle session persistence logic
        val staySignedIn = prefs.getBoolean(STAY_SIGNED_IN_KEY, false)
        if (!staySignedIn && auth.currentUser != null) {
            auth.signOut()
        }

        // Listen to Firebase Auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            currentUserListener?.remove()
            if (firebaseUser == null) {
                _currentUserFlow.value = null
            } else {
                currentUserListener = usersCollection.document(firebaseUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null && snapshot.exists()) {
                            val user = snapshot.toObject(User::class.java)
                            _currentUserFlow.value = user
                        }
                    }
            }
        }
    }

    override suspend fun login(email: String, password: String, staySignedIn: Boolean): Resource<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Auth failed")
            
            val document = usersCollection.document(firebaseUser.uid).get().await()
            if (!document.exists()) {
                auth.signOut()
                return Resource.Error("No profile found for this account. Please sign up first.")
            }
            
            val user = document.toObject(User::class.java) ?: throw Exception("Failed to load profile")
            
            if (user.isSuspended) {
                auth.signOut()
                return Resource.Error("Your account has been suspended. Please contact the administrator.")
            }
            
            prefs.edit().putBoolean(STAY_SIGNED_IN_KEY, staySignedIn).apply()
            
            _currentUserFlow.value = user
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Invalid email or password.")
        }
    }

    override suspend fun register(email: String, username: String, password: String): Resource<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Registration failed")
            
            val user = User(
                id = firebaseUser.uid,
                email = email, 
                username = username, 
                role = Role.USER, 
                joinedCommunities = listOf("General"),
                isSuspended = false
            )
            usersCollection.document(firebaseUser.uid).set(user).await()
            
            _currentUserFlow.value = user
            Resource.Success(user)
        } catch (e: FirebaseAuthUserCollisionException) {
            Resource.Error("An account with this email already exists. Please login instead.")
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during registration")
        }
    }

    override suspend fun signInWithGoogle(idToken: String, staySignedIn: Boolean): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Resource.Error("Google sign-in failed")
            
            // Check if user exists in Firestore. We don't use fetchOrCreateProfile here
            // because "Sign In" should only work for already registered accounts.
            val document = usersCollection.document(firebaseUser.uid).get().await()
            
            if (!document.exists()) {
                // If it's a new account from Google's perspective, but we don't have a profile,
                // we sign them out and tell them to use the Sign Up screen.
                auth.signOut()
                return Resource.Error("No account found for this Google email. Please sign up first.")
            }

            val user = document.toObject(User::class.java) ?: throw Exception("Failed to load profile")

            if (user.isSuspended) {
                auth.signOut()
                return Resource.Error("Your account has been suspended. Please contact the administrator.")
            }
            
            prefs.edit().putBoolean(STAY_SIGNED_IN_KEY, staySignedIn).apply()

            _currentUserFlow.value = user
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during Google sign-in")
        }
    }

    override suspend fun signUpWithGoogle(idToken: String, username: String, password: String, staySignedIn: Boolean): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Resource.Error("Google sign-up failed")
            
            prefs.edit().putBoolean(STAY_SIGNED_IN_KEY, staySignedIn).apply()

            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false
            
            if (!isNewUser) {
                // If user is not new, check if they already have a profile in Firestore
                val document = usersCollection.document(firebaseUser.uid).get().await()
                if (document.exists()) {
                    // User already exists, they should use Sign In instead of Sign Up
                    auth.signOut()
                    return Resource.Error("This Google account is already registered. Please login instead.")
                }
            }

            // Link Email/Password credential so they can use the login form
            try {
                val email = firebaseUser.email ?: throw Exception("Google account has no email")
                val emailCredential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                firebaseUser.linkWithCredential(emailCredential).await()
            } catch (e: Exception) {
                // If linking fails (e.g. email already in use by another account), we might want to handle it.
                // But since we just signed in with Google, and if isNewUser was true, it should be fine.
                // If they already had an account with this email, linkWithCredential might fail.
            }
            
            // If they are new or didn't have a profile, create it
            val user = User(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                username = username,
                role = Role.USER,
                joinedCommunities = listOf("General")
            )
            usersCollection.document(firebaseUser.uid).set(user).await()
            
            _currentUserFlow.value = user
            Resource.Success(user)
        } catch (e: FirebaseAuthUserCollisionException) {
            Resource.Error("An account with this email already exists with a different sign-in method.")
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during Google sign-up")
        }
    }

    private suspend fun fetchProfile(uid: String): User? {
        val document = usersCollection.document(uid).get().await()
        val user = document.toObject(User::class.java)
        
        if (user != null && user.id.isEmpty()) {
            // Migration for old users who didn't have ID field
            val updatedUser = user.copy(id = uid)
            usersCollection.document(uid).set(updatedUser).await()
            return updatedUser
        }
        return user
    }

    override suspend fun getSession(): User? {
        if (_currentUserFlow.value != null) return _currentUserFlow.value
        
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            try {
                val user = fetchProfile(firebaseUser.uid)
                if (user == null) {
                    auth.signOut()
                    return null
                }
                _currentUserFlow.value = user
                user
            } catch (e: Exception) {
                null
            }
        } else null
    }

    override suspend fun logout() {
        auth.signOut()
        prefs.edit().putBoolean(STAY_SIGNED_IN_KEY, false).apply()
        _currentUserFlow.value = null
    }

    override fun getAllUsers(): Flow<List<User>> = callbackFlow {
        val subscription = usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val users = snapshot.toObjects(User::class.java)
                trySend(users)
            }
        }
        awaitClose { subscription.remove() }
    }

    override suspend fun updateUserRole(userId: String, newRole: Role, communityToManage: String?) {
        try {
            val userRef = usersCollection.document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction

                var updatedPermissions = user.safePermissions()
                var updatedManaged = user.safeManaged()

                if (newRole == Role.ADMIN || newRole == Role.SUPER_ADMIN) {
                    if (communityToManage != null && !updatedManaged.contains(communityToManage)) {
                        updatedManaged = (updatedManaged + communityToManage).distinct()
                    }
                    
                    // Admin roles get basic community management perms by default.
                    val defaultAdminPerms = listOf(
                        "can_delete_community_posts", 
                        "can_manage_community_users",
                        "can_approve_community_posts",
                        "can_manage_community_requests"
                    )
                    updatedPermissions = (updatedPermissions + defaultAdminPerms).distinct()
                } else if (newRole == Role.USER) {
                    updatedManaged = emptyList()
                    // When demoting to USER, we remove all administrative and management permissions.
                    val adminPermsToRemove = listOf(
                        "can_delete_community_posts", 
                        "can_manage_community_users",
                        "can_manage_roles",
                        "can_manage_permissions",
                        "can_manage_requests_globally",
                        "can_approve_posts_globally",
                        "can_manage_bypass_approval",
                        "can_delete_any_post",
                        "can_create_community",
                        "can_edit_any_community",
                        "can_send_global_broadcast",
                        "can_approve_community_posts",
                        "can_manage_community_requests"
                    )
                    updatedPermissions = updatedPermissions.filter { 
                        !adminPermsToRemove.contains(it) 
                    }
                }

                transaction.update(userRef, mapOf(
                    "role" to newRole.name,
                    "managedCommunities" to updatedManaged,
                    "permissions" to updatedPermissions
                ))

                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(
                        role = newRole,
                        managedCommunities = updatedManaged,
                        permissions = updatedPermissions
                    )
                }
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "Error updating user role: ${e.message}")
        }
    }

    override suspend fun toggleCommunityManagement(userId: String, community: String) {
        try {
            val userRef = usersCollection.document(userId)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction
                
                val managed = user.safeManaged()
                val perms = user.safePermissions()
                val isAdding = !managed.contains(community)
                
                val newManaged = if (isAdding) managed + community else managed.filter { it != community }
                
                // Decoupled community management from bypass permissions.
                // Toggling management no longer affects "bypass_approval_" flags.
                val newPermissions = perms
                
                transaction.update(userRef, mapOf(
                    "managedCommunities" to newManaged,
                    "permissions" to newPermissions
                ))

                // Internal state update happens after success
                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(
                        managedCommunities = newManaged,
                        permissions = newPermissions
                    )
                }
            }.await()
            android.util.Log.d("AuthRepo", "Successfully toggled management for $community")
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "Error toggling community management: ${e.message}")
        }
    }

    override suspend fun toggleGlobalPermission(userId: String, permission: String) {
        try {
            val userRef = usersCollection.document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction
                
                val perms = user.safePermissions()
                val newList = if (perms.contains(permission)) {
                    perms.filter { it != permission }
                } else {
                    perms + permission
                }
                
                transaction.update(userRef, "permissions", newList)
                
                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(permissions = newList)
                }
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "Error toggling global permission: ${e.message}")
        }
    }

    override suspend fun toggleUserSuspension(userId: String) {
        try {
            val userRef = usersCollection.document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction
                
                val newState = !user.isSuspended
                transaction.update(userRef, "isSuspended", newState)
                
                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(isSuspended = newState)
                }
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "Error toggling user suspension: ${e.message}")
        }
    }

    override suspend fun joinCommunity(userId: String, community: String): Resource<User> {
        return try {
            val userRef = usersCollection.document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction
                
                val currentJoined = user.safeJoined()
                if (currentJoined.contains(community)) return@runTransaction

                val newJoined = currentJoined + community
                
                // Decoupled community joining/leaving from bypass permissions.
                // Joining a community no longer automatically grants "bypass_approval_".
                val newPermissions = user.safePermissions()

                transaction.update(userRef, mapOf(
                    "joinedCommunities" to newJoined,
                    "permissions" to newPermissions
                ))

                // If it's current user, update flow
                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(
                        joinedCommunities = newJoined,
                        permissions = newPermissions
                    )
                }
            }.await()

            val document = usersCollection.document(userId).get().await()
            val updatedUser = document.toObject(User::class.java)
            
            if (updatedUser != null) {
                Resource.Success(updatedUser)
            } else {
                Resource.Error("User not found after update")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not join community")
        }
    }

    override suspend fun leaveCommunity(userId: String, community: String): Resource<User> {
        return try {
            val userRef = usersCollection.document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction
                
                val currentJoined = user.safeJoined()
                if (!currentJoined.contains(community)) return@runTransaction

                val newJoined = currentJoined.filter { it != community }
                
                // Decoupled community joining/leaving from bypass permissions.
                // Leaving a community no longer automatically revokes "bypass_approval_".
                val newPermissions = user.safePermissions()

                transaction.update(userRef, mapOf(
                    "joinedCommunities" to newJoined,
                    "permissions" to newPermissions
                ))

                // If it's current user, update flow
                if (_currentUserFlow.value?.id == userId) {
                    _currentUserFlow.value = _currentUserFlow.value?.copy(
                        joinedCommunities = newJoined,
                        permissions = newPermissions
                    )
                }
            }.await()

            val document = usersCollection.document(userId).get().await()
            val updatedUser = document.toObject(User::class.java)
            
            if (updatedUser != null) {
                Resource.Success(updatedUser)
            } else {
                Resource.Error("User not found after update")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not leave community")
        }
    }

    override suspend fun isGoogleOnly(): Boolean {
        val user = auth.currentUser ?: return false
        return user.providerData.any { it.providerId == "google.com" } && 
               user.providerData.none { it.providerId == "password" }
    }

    override suspend fun setPassword(password: String): Resource<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("No user logged in")
            // Instead of updatePassword, we should use linkWithCredential if we want to enable email/password login
            val email = user.email ?: throw Exception("User has no email")
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            user.linkWithCredential(credential).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to set password")
        }
    }

    override fun observeCurrentUser(): Flow<User?> = currentUserFlow
}
