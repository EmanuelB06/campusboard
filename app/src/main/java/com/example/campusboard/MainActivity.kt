package com.example.campusboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.campusboard.domain.use_case.*
import com.example.campusboard.presentation.auth.AuthEvent
import com.example.campusboard.presentation.auth.AuthScreen
import com.example.campusboard.presentation.auth.AuthViewModel
import com.example.campusboard.presentation.board.BoardScreen
import com.example.campusboard.presentation.board.BoardViewModel
import com.example.campusboard.ui.theme.CampusBoardTheme

/**
 * [MainActivity] follows the SOLID principles by:
 * 1. SRP: Handling only the UI entry point and high-level composition.
 * 2. DIP: Receiving dependencies via the Application context and Use Cases.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        
        // Dependency Inversion: Get dependencies from Application context
        val app = application as CampusBoardApp
        
        // SRP: Use cases handle specific business logic
        val loginUseCase = LoginUseCase(app.authRepository)
        val registerUseCase = RegisterUseCase(app.authRepository)
        val joinCommunityUseCase = JoinCommunityUseCase(app.authRepository, app.boardRepository)
        val leaveCommunityUseCase = LeaveCommunityUseCase(app.authRepository)
        val createPostUseCase = CreatePostUseCase(app.boardRepository)
        val approveJoinRequestUseCase = ApproveJoinRequestUseCase(app.authRepository, app.boardRepository)
        val rejectJoinRequestUseCase = RejectJoinRequestUseCase(app.boardRepository)
        val approvePostUseCase = ApprovePostUseCase(app.boardRepository)
        val rejectPostUseCase = RejectPostUseCase(app.boardRepository)
        val createCommunityUseCase = CreateCommunityUseCase(app.boardRepository)
        val deletePostUseCase = DeletePostUseCase(app.boardRepository)
        
        // ViewModel Factory for manual DI
        val authViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(app.authRepository, loginUseCase, registerUseCase, app.notificationHelper) as T
            }
        })[AuthViewModel::class.java]

        enableEdgeToEdge()
        
        // LOUD LOGGING AND TOAST FOR DEBUGGING (Commented out for production/presentation)
        /*
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                android.util.Log.e("FCM_TEST", "!!!!! CONNECTION SUCCESS !!!!!")
                android.util.Log.e("FCM_TEST", "TOKEN: $token")
                android.widget.Toast.makeText(this, "FCM Connected!", android.widget.Toast.LENGTH_SHORT).show()
                println("FCM_DEBUG: Token is $token")
            } else {
                android.util.Log.e("FCM_TEST", "!!!!! CONNECTION FAILED !!!!!")
                android.util.Log.e("FCM_TEST", "ERROR: ${task.exception?.message}")
                android.widget.Toast.makeText(this, "FCM Failed: ${task.exception?.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        */
        
        setContent {
            CampusBoardTheme {
                MainContent(
                    authViewModel = authViewModel, 
                    app = app,
                    joinCommunityUseCase = joinCommunityUseCase,
                    leaveCommunityUseCase = leaveCommunityUseCase,
                    createPostUseCase = createPostUseCase,
                    approveJoinRequestUseCase = approveJoinRequestUseCase,
                    rejectJoinRequestUseCase = rejectJoinRequestUseCase,
                    approvePostUseCase = approvePostUseCase,
                    rejectPostUseCase = rejectPostUseCase,
                    createCommunityUseCase = createCommunityUseCase,
                    deletePostUseCase = deletePostUseCase
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission granted
                }
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    private fun MainContent(
        authViewModel: AuthViewModel, 
        app: CampusBoardApp,
        joinCommunityUseCase: JoinCommunityUseCase,
        leaveCommunityUseCase: LeaveCommunityUseCase,
        createPostUseCase: CreatePostUseCase,
        approveJoinRequestUseCase: ApproveJoinRequestUseCase,
        rejectJoinRequestUseCase: RejectJoinRequestUseCase,
        approvePostUseCase: ApprovePostUseCase,
        rejectPostUseCase: RejectPostUseCase,
        createCommunityUseCase: CreateCommunityUseCase,
        deletePostUseCase: DeletePostUseCase
    ) {
        val authState = authViewModel.state.value
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (authState.user != null && !authState.needsCommunitySelection) {
                    // BoardViewModel is scoped to the session
                    // Side Effects (Firebase subscriptions) have been moved to BoardViewModel's init/observeCurrentUser
                    val boardViewModel = remember(authState.user.id) {
                        BoardViewModel(
                            authRepository = app.authRepository,
                            boardRepository = app.boardRepository,
                            notificationHelper = app.notificationHelper,
                            joinCommunityUseCase = joinCommunityUseCase,
                            leaveCommunityUseCase = leaveCommunityUseCase,
                            createPostUseCase = createPostUseCase,
                            approveJoinRequestUseCase = approveJoinRequestUseCase,
                            rejectJoinRequestUseCase = rejectJoinRequestUseCase,
                            approvePostUseCase = approvePostUseCase,
                            rejectPostUseCase = rejectPostUseCase,
                            createCommunityUseCase = createCommunityUseCase,
                            deletePostUseCase = deletePostUseCase,
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
