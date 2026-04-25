package com.example.campusboard.data.repository

import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.AuthRepository
import com.example.campusboard.domain.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    
    /**
     * TODO: Change this to the actual email you added in the Firebase Authentication console.
     * Any account matching this email will be automatically granted SUPER_ADMIN status
     * and joined to all communities on their first successful login.
     */
    private val ADMIN_EMAIL = "super@admin.com" 
    
    private var currentUser: User? = null

    override suspend fun login(email: String, password: String, staySignedIn: Boolean): Resource<User> {
        return try {
            // 1. Authenticate with Firebase Auth
            auth.signInWithEmailAndPassword(email, password).await()
            
            // 2. Fetch or Create Firestore profile
            val document = usersCollection.document(email).get().await()
            var user = document.toObject(User::class.java)
            
            if (user == null) {
                // If user exists in Auth but not Firestore (e.g. manually added in console), create profile
                val role = if (email.lowercase() == ADMIN_EMAIL.lowercase()) Role.SUPER_ADMIN else Role.USER
                user = User(
                    email = email,
                    username = email.substringBefore("@"),
                    role = role,
                    joinedCommunities = if (role == Role.SUPER_ADMIN) 
                        listOf("General", "BSIT", "BSBA", "BEED", "BSSW") 
                    else listOf("General")
                )
                usersCollection.document(email).set(user).await()
            }
            
            if (staySignedIn) currentUser = user
            Resource.Success(user!!)
        } catch (e: Exception) {
            // Provide the actual Firebase error message for easier debugging
            Resource.Error(e.localizedMessage ?: "Invalid email or password.")
        }
    }

    override suspend fun register(email: String, username: String, password: String): Resource<User> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            
            val role = if (email.lowercase() == ADMIN_EMAIL.lowercase()) Role.SUPER_ADMIN else Role.USER
            val user = User(
                email = email, 
                username = username, 
                role = role, 
                joinedCommunities = if (role == Role.SUPER_ADMIN) 
                    listOf("General", "BSIT", "BSBA", "BEED", "BSSW") 
                else listOf("General")
            )
            usersCollection.document(email).set(user).await()
            
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during registration")
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Resource.Error("Google sign-in failed")
            val email = firebaseUser.email ?: return Resource.Error("Email not found")

            val document = usersCollection.document(email).get().await()
            var user = document.toObject(User::class.java)
            
            if (user == null) {
                val role = if (email.lowercase() == ADMIN_EMAIL.lowercase()) Role.SUPER_ADMIN else Role.USER
                user = User(
                    email = email,
                    username = firebaseUser.displayName ?: email.substringBefore("@"),
                    role = role,
                    joinedCommunities = if (role == Role.SUPER_ADMIN) 
                        listOf("General", "BSIT", "BSBA", "BEED", "BSSW") 
                    else listOf("General")
                )
                usersCollection.document(email).set(user).await()
            }

            currentUser = user
            Resource.Success(user!!)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during Google sign-in")
        }
    }

    override suspend fun signUpWithGoogle(idToken: String, username: String): Resource<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Resource.Error("Google sign-up failed")
            val email = firebaseUser.email ?: return Resource.Error("Email not found")
            
            val document = usersCollection.document(email).get().await()
            if (document.exists()) {
                val existingUser = document.toObject(User::class.java)
                if (existingUser != null) {
                    currentUser = existingUser
                    return Resource.Success(existingUser)
                }
            }

            val role = if (email.lowercase() == ADMIN_EMAIL.lowercase()) Role.SUPER_ADMIN else Role.USER
            val user = User(
                email = email, 
                username = username, 
                role = role,
                joinedCommunities = if (role == Role.SUPER_ADMIN) 
                    listOf("General", "BSIT", "BSBA", "BEED", "BSSW") 
                else listOf("General")
            )
            usersCollection.document(email).set(user).await()
            
            currentUser = user
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "An error occurred during Google sign-up")
        }
    }

    override suspend fun getSession(): User? {
        if (currentUser != null) return currentUser
        
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            val email = firebaseUser.email
            if (email != null) {
                try {
                    val document = usersCollection.document(email).get().await()
                    currentUser = document.toObject(User::class.java)
                    currentUser
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    override suspend fun logout() {
        auth.signOut()
        currentUser = null
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

    override suspend fun updateUserRole(userId: String, newRole: Role) {
        try {
            usersCollection.document(userId).update("role", newRole).await()
        } catch (e: Exception) {
            // Log error
        }
    }

    override suspend fun joinCommunity(email: String, community: String): Resource<User> {
        return try {
            usersCollection.document(email)
                .update("joinedCommunities", com.google.firebase.firestore.FieldValue.arrayUnion(community))
                .await()
            
            val document = usersCollection.document(email).get().await()
            val updatedUser = document.toObject(User::class.java)
            
            if (updatedUser != null) {
                currentUser = updatedUser
                Resource.Success(updatedUser)
            } else {
                Resource.Error("User not found after update")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not join community")
        }
    }
}
