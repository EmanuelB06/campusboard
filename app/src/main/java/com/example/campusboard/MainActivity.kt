package com.example.campusboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.campusboard.domain.use_case.LoginUseCase
import com.example.campusboard.domain.use_case.RegisterUseCase
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
        
        // Dependency Inversion: Get dependencies from Application context
        val app = application as CampusBoardApp
        
        // SRP: Use cases handle specific business logic
        val loginUseCase = LoginUseCase(app.authRepository)
        val registerUseCase = RegisterUseCase(app.authRepository)
        
        // ViewModel Factory for manual DI
        val authViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(app.authRepository, loginUseCase, registerUseCase) as T
            }
        })[AuthViewModel::class.java]

        enableEdgeToEdge()
        
        setContent {
            CampusBoardTheme {
                MainContent(authViewModel, app)
            }
        }
    }

    @Composable
    private fun MainContent(authViewModel: AuthViewModel, app: CampusBoardApp) {
        val authState = authViewModel.state.value
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (authState.user != null && !authState.needsCommunitySelection) {
                    // BoardViewModel is scoped to the session
                    val boardViewModel = remember(authState.user.id) {
                        BoardViewModel(
                            authRepository = app.authRepository,
                            boardRepository = app.boardRepository,
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
