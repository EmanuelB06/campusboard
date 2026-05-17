package com.example.campusboard

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // android.util.Log.e("FCM_TEST", "!!!!! NEW TOKEN GENERATED !!!!!")
        // android.util.Log.e("FCM_TEST", "TOKEN: $token")
        // println("FCM_DEBUG: New Token is $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Log.d("FCM_TEST", "Message Received from: ${remoteMessage.from}")
        
        val app = application as CampusBoardApp
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Note!"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Check the board."
        
        app.notificationHelper.showSimpleNotification(title, body)
    }
}
