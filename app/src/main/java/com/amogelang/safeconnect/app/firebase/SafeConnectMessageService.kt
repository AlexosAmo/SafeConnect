package com.amogelang.safeconnect.app.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amogelang.safeconnect.app.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SafeConnectMessagingService : FirebaseMessagingService() {
    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        FirebaseRepository.currentUserId()?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid).update("fcmToken", token)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {
        val channelId = "safeconnect_alerts"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Safety alerts", NotificationManager.IMPORTANCE_HIGH))
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.notification?.title ?: "SafeConnect alert")
            .setContentText(message.notification?.body ?: "You have a new safety update.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
