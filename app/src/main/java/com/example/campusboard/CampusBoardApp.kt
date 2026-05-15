package com.example.campusboard

import android.app.Application
import com.example.campusboard.data.repository.AuthRepositoryImpl
import com.example.campusboard.data.repository.BoardRepositoryImpl
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class CampusBoardApp : Application() {

    // Dependency Injection (Manual)
    // In a full SOLID implementation, we would use Hilt or Koin.
    // However, moving these to the Application class ensures they are singletons 
    // and decoupled from the Activity lifecycle.
    lateinit var authRepository: AuthRepositoryImpl
    lateinit var boardRepository: BoardRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        
        authRepository = AuthRepositoryImpl()
        boardRepository = BoardRepositoryImpl()

        // Trigger data initialization on startup
        MainScope().launch {
            boardRepository.initializeData()
        }
    }
}
