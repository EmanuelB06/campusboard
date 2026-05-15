package com.example.campusboard.data.repository

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.User
import com.example.campusboard.domain.repository.BoardRepository
import com.example.campusboard.domain.util.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BoardRepositoryImpl : BoardRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val postsCollection = firestore.collection("posts")
    private val joinRequestsCollection = firestore.collection("join_requests")
    private val communitiesCollection = firestore.collection("communities")

    override fun getPosts(community: String): Flow<List<Post>> = callbackFlow {
        // Fetch all approved posts and filter in memory to support global broadcasts 
        // across all community boards without complex Firestore composite indexes.
        val query = postsCollection.whereEqualTo("status", "APPROVED")

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("BoardRepository", "Error fetching posts: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                try {
                    val posts = snapshot.toObjects(Post::class.java)
                        .filter { it.isBroadcast || community == "General" || it.community == community }
                        .sortedWith(compareByDescending<Post> { it.isBroadcast }.thenByDescending { it.timestamp })
                    trySend(posts)
                } catch (e: Exception) {
                    android.util.Log.e("BoardRepository", "Error deserializing posts: ${e.message}", e)
                }
            }
        }

        awaitClose { subscription.remove() }
    }

    override fun getPendingPosts(): Flow<List<Post>> = callbackFlow {
        val subscription = postsCollection.whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("BoardRepository", "Error fetching pending posts: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val posts = snapshot.toObjects(Post::class.java)
                            .sortedByDescending { it.timestamp }
                        trySend(posts)
                    } catch (e: Exception) {
                        android.util.Log.e("BoardRepository", "Error deserializing pending posts: ${e.message}", e)
                    }
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun updatePostStatus(postId: String, status: com.example.campusboard.domain.model.PostStatus): Resource<Unit> {
        return try {
            postsCollection.document(postId).update("status", status.name).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not update post status")
        }
    }

    override suspend fun createPost(post: Post): Resource<Unit> {
        return try {
            postsCollection.document(post.id).set(post).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not save post")
        }
    }

    override suspend fun deletePost(postId: String): Resource<Unit> {
        return try {
            postsCollection.document(postId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not delete post")
        }
    }

    override suspend fun joinCommunity(userId: String, community: String): Resource<Unit> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            
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
            }.await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not join community")
        }
    }

    override suspend fun submitJoinRequest(request: JoinRequest): Resource<Unit> {
        return try {
            val id = if (request.id.isEmpty()) UUID.randomUUID().toString() else request.id
            val finalRequest = request.copy(id = id)
            joinRequestsCollection.document(id).set(finalRequest).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not submit request")
        }
    }

    override fun getPendingJoinRequests(community: String): Flow<List<JoinRequest>> = callbackFlow {
        val query = if (community == "General") {
            joinRequestsCollection.whereEqualTo("status", "PENDING")
        } else {
            joinRequestsCollection.whereEqualTo("community", community)
                .whereEqualTo("status", "PENDING")
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val requests = snapshot.toObjects(JoinRequest::class.java)
                    .sortedByDescending { it.timestamp }
                trySend(requests)
            }
        }
        awaitClose { subscription.remove() }
    }

    override fun getAllPendingJoinRequests(): Flow<List<JoinRequest>> = callbackFlow {
        val subscription = joinRequestsCollection.whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val requests = snapshot.toObjects(JoinRequest::class.java)
                        .sortedByDescending { it.timestamp }
                    trySend(requests)
                }
            }
        awaitClose { subscription.remove() }
    }

    override fun getJoinRequestsForUser(userId: String): Flow<List<JoinRequest>> = callbackFlow {
        val subscription = joinRequestsCollection.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val requests = snapshot.toObjects(JoinRequest::class.java)
                    trySend(requests)
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun updateJoinRequestStatus(requestId: String, status: String): Resource<Unit> {
        return try {
            joinRequestsCollection.document(requestId).update("status", status).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not update request")
        }
    }

    override suspend fun cancelJoinRequest(requestId: String): Resource<Unit> {
        return try {
            joinRequestsCollection.document(requestId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not cancel request")
        }
    }

    override fun getCommunities(): Flow<List<Community>> = callbackFlow {
        val subscription = communitiesCollection.orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val communities = snapshot.toObjects(Community::class.java)
                    trySend(communities)
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun createCommunity(community: Community): Resource<Unit> {
        return try {
            communitiesCollection.document(community.name).set(community).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not create community")
        }
    }

    override suspend fun updateCommunity(community: Community): Resource<Unit> {
        return try {
            communitiesCollection.document(community.name).set(community).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not update community")
        }
    }
}
