package com.swyp.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SwypApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_API_KEY.isNotBlank() && FirebaseApp.getApps(this).isEmpty())
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(
                listOf(
                    NotificationChannel(
                        "scan",
                        "Checkout scanning",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                    NotificationChannel(
                        "deals",
                        "Nearby offers",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                    NotificationChannel(
                        "scan_results",
                        "Purchase recommendations",
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            )
    }
}
