package com.example.campusboard

import android.app.Application
import com.example.campusboard.data.repository.AuthRepositoryImpl
import com.example.campusboard.data.repository.BoardRepositoryImpl
import com.example.campusboard.util.NotificationHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class CampusBoardApp : Application() {

    // Dependency Injection (Manual)
    lateinit var authRepository: AuthRepositoryImpl
    lateinit var boardRepository: BoardRepositoryImpl
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        
        authRepository = AuthRepositoryImpl()
        boardRepository = BoardRepositoryImpl()
        notificationHelper = NotificationHelper(this)

        // Trigger data initialization on startup
        MainScope().launch {
            boardRepository.initializeData()
        }
    }
}
