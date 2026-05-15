package com.example.campusboard.data.repository

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

class AuthRepositoryImpl : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    
    private val _currentUserFlow = MutableStateFlow<User?>(null)
    private val currentUserFlow = _currentUserFlow.asStateFlow()
    private var currentUserListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
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
            
            val user = fetchOrCreateProfile(firebaseUser.uid, email, firebaseUser.displayName)
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
                joinedCommunities = listOf("General")
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
            
            // Note: Firebase Auth handles session persistence automatically.
            // If staySignedIn is false, we might want to sign out when the app is closed,
            // but Firebase doesn't support "session only" tokens easily on Android.
            // Common practice is to handle this logic in the session management layer if needed.
            
            val user = fetchOrCreateProfile(
                firebaseUser.uid, 
                firebaseUser.email ?: "", 
                firebaseUser.displayName
            )

            _currentUserFlow.value = user
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during Google sign-in")
        }
    }

    override suspend fun signUpWithGoogle(idToken: String, username: String, staySignedIn: Boolean): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Resource.Error("Google sign-up failed")
            
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

    private suspend fun fetchOrCreateProfile(uid: String, email: String, displayName: String?): User {
        val document = usersCollection.document(uid).get().await()
        var user = document.toObject(User::class.java)
        
        if (user == null) {
            user = User(
                id = uid,
                email = email,
                username = displayName ?: email.substringBefore("@"),
                role = Role.USER,
                joinedCommunities = listOf("General")
            )
            usersCollection.document(uid).set(user).await()
        } else if (user.id.isEmpty()) {
            // Migration for old users who didn't have ID field
            user = user.copy(id = uid)
            usersCollection.document(uid).set(user).await()
        }
        return user
    }

    override suspend fun getSession(): User? {
        if (_currentUserFlow.value != null) return _currentUserFlow.value
        
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            try {
                val user = fetchOrCreateProfile(
                    firebaseUser.uid, 
                    firebaseUser.email ?: "", 
                    firebaseUser.displayName
                )
                _currentUserFlow.value = user
                user
            } catch (e: Exception) {
                null
            }
        } else null
    }

    override suspend fun logout() {
        auth.signOut()
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
                    
                    val joinedBypasses = user.safeJoined().map { "bypass_approval_$it" }
                    val managedBypasses = updatedManaged.map { "bypass_approval_$it" }
                    val defaultAdminPerms = listOf("can_delete_community_posts", "can_manage_community_users")
                    updatedPermissions = (updatedPermissions + joinedBypasses + managedBypasses + defaultAdminPerms).distinct()
                } else if (newRole == Role.USER) {
                    updatedManaged = emptyList()
                    val adminPermsToRemove = listOf("can_delete_community_posts", "can_manage_community_users")
                    updatedPermissions = updatedPermissions.filter { 
                        !it.startsWith("bypass_approval_") && !adminPermsToRemove.contains(it) 
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
                var newPermissions = perms

                // If user is ADMIN/SUPER_ADMIN and we're adding a community, auto-grant bypass for it
                if ((user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN) && isAdding) {
                    val bypassPerm = "bypass_approval_$community"
                    if (!perms.contains(bypassPerm)) {
                        newPermissions = perms + bypassPerm
                    }
                } else if ((user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN) && !isAdding) {
                    // If removing management, also revoke the bypass UNLESS they are also joined
                    if (!user.safeJoined().contains(community)) {
                        val bypassPerm = "bypass_approval_$community"
                        newPermissions = perms.filter { it != bypassPerm }
                    }
                }
                
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
            val doc = usersCollection.document(userId).get().await()
            val user = doc.toObject(User::class.java) ?: return
            
            val perms = user.safePermissions()
            val newList = if (perms.contains(permission)) {
                perms.filter { it != permission }
            } else {
                perms + permission
            }
            
            usersCollection.document(userId).update("permissions", newList).await()
            
            if (_currentUserFlow.value?.id == userId) {
                _currentUserFlow.value = _currentUserFlow.value?.copy(permissions = newList)
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    override suspend fun toggleUserSuspension(userId: String) {
        try {
            val doc = usersCollection.document(userId).get().await()
            val user = doc.toObject(User::class.java) ?: return
            
            val newState = !user.isSuspended
            usersCollection.document(userId).update("isSuspended", newState).await()
            
            if (_currentUserFlow.value?.id == userId) {
                _currentUserFlow.value = _currentUserFlow.value?.copy(isSuspended = newState)
            }
        } catch (e: Exception) {
            // Log error
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
                var newPermissions = user.safePermissions()

                // If user is ADMIN or SUPER_ADMIN, auto-grant bypass for the community they just joined
                if (user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN) {
                    val bypassPerm = "bypass_approval_$community"
                    if (!newPermissions.contains(bypassPerm)) {
                        newPermissions = newPermissions + bypassPerm
                    }
                }

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
                var newPermissions = user.safePermissions()

                // If user is ADMIN or SUPER_ADMIN, remove bypass UNLESS they also manage this community
                if (user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN) {
                    if (!user.safeManaged().contains(community)) {
                        val bypassPerm = "bypass_approval_$community"
                        newPermissions = newPermissions.filter { it != bypassPerm }
                    }
                }

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

    override fun observeCurrentUser(): Flow<User?> = currentUserFlow
}
