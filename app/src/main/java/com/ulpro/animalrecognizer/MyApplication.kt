package com.ulpro.animalrecognizer

import android.app.Application
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Programar el UpdateAnimalsWorker
        val workRequest = OneTimeWorkRequest.Builder(UpdateAnimalsWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }
}