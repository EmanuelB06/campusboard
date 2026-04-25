package com.example.campusboard.data.repository

import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Post
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
        val query = if (community == "General") {
            postsCollection.orderBy("timestamp", Query.Direction.DESCENDING)
        } else {
            postsCollection.whereEqualTo("community", community)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val posts = snapshot.toObjects(Post::class.java)
                trySend(posts)
            }
        }

        awaitClose { subscription.remove() }
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
            firestore.collection("users").document(userId)
                .update("joinedCommunities", com.google.firebase.firestore.FieldValue.arrayUnion(community))
                .await()
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
                .orderBy("timestamp", Query.Direction.DESCENDING)
        } else {
            joinRequestsCollection.whereEqualTo("community", community)
                .whereEqualTo("status", "PENDING")
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val requests = snapshot.toObjects(JoinRequest::class.java)
                trySend(requests)
            }
        }
        awaitClose { subscription.remove() }
    }

    override fun getJoinRequestsForUser(userEmail: String): Flow<List<JoinRequest>> = callbackFlow {
        val subscription = joinRequestsCollection.whereEqualTo("userEmail", userEmail)
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
}
