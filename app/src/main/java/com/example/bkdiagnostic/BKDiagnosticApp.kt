package com.example.bkdiagnostic

import android.app.Application
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.protocol.ford.FordRangerActiveTests
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BKDiagnosticApp : Application() {

    /** Singleton USB Serial Manager — dùng chung toàn app */
    val usbSerialManager: UsbSerialManager by lazy {
        UsbSerialManager.getInstance(this)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        FordRangerActiveTests.register()
        // Khởi tạo supabaseClient sớm trên IO thread → tránh block Main Thread khi SplashScreen start
        GlobalScope.launch(Dispatchers.IO) {
            supabaseClient
        }
    }
}
