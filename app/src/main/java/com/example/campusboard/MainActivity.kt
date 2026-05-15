package com.example.campusboard

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusboard.data.repository.AuthRepositoryImpl
import com.example.campusboard.data.repository.BoardRepositoryImpl
import com.example.campusboard.domain.model.Community
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.PostType
import com.example.campusboard.domain.use_case.LoginUseCase
import com.example.campusboard.domain.use_case.RegisterUseCase
import com.example.campusboard.presentation.auth.AuthEvent
import com.example.campusboard.presentation.auth.AuthScreen
import com.example.campusboard.presentation.auth.AuthViewModel
import com.example.campusboard.presentation.board.BoardScreen
import com.example.campusboard.presentation.board.BoardViewModel
import com.example.campusboard.ui.theme.CampusBoardTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Persistent repositories
private val authRepository = AuthRepositoryImpl()
private val boardRepository = BoardRepositoryImpl()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        seedFirestore()

        val loginUseCase = LoginUseCase(authRepository)
        val registerUseCase = RegisterUseCase(authRepository)
        
        val authViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(authRepository, loginUseCase, registerUseCase) as T
            }
        })[AuthViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            CampusBoardTheme {
                val authState = authViewModel.state.value
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (authState.user != null && !authState.needsCommunitySelection) {
                            val boardViewModel = remember(authState.user.id) {
                                BoardViewModel(
                                    authRepository = authRepository,
                                    boardRepository = boardRepository,
                                    initialUser = authState.user,
                                    onLogout = { authViewModel.onEvent(AuthEvent.Logout) }
                                )
                            }
                            BoardScreen(viewModel = boardViewModel)
                        } else {
                            AuthScreen(viewModel = authViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun seedFirestore() {
        lifecycleScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                
                // Seed Communities if empty
                val communitySnapshot = db.collection("communities").get().await()
                if (communitySnapshot.isEmpty) {
                    val defaultCommunities = listOf(
                        Community("General", "Public board for everyone", "system"),
                        Community("BSIT", "Information Technology Department", "system"),
                        Community("BSBA", "Business Administration Department", "system"),
                        Community("BEED", "Education Department", "system"),
                        Community("BSSW", "Social Work Department", "system")
                    )
                    defaultCommunities.forEach { 
                        db.collection("communities").document(it.name).set(it).await()
                    }
                    Log.d("FirestoreSeed", "Communities seeded successfully.")
                }

                // Seed Posts if empty
                val postSnapshot = db.collection("posts").limit(1).get().await()
                if (postSnapshot.isEmpty) {
                    val welcomePost = Post(
                        title = "Welcome to CampusBoard",
                        content = "This is the first post in your community. Feel free to add more!",
                        author = "System Admin",
                        community = "General",
                        type = PostType.NEWS
                    )
                    db.collection("posts").document(welcomePost.id).set(welcomePost).await()
                }
            } catch (e: Exception) {
                Log.e("FirestoreSeed", "Error: ${e.message}")
            }
        }
    }
}
