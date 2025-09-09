package com.ancienttottenham.amloggerproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ancienttottenham.amlogger.AMLogger
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Basic setup
        AMLogger.configure {
            minLevel = AMLogger.Level.DEBUG
            logToConsole = true
            logToFile = true
            logDirectory = File(applicationContext.filesDir, "logs")
            showEmojis = true
            showColors = false // Disable for production
        }

// Simple logging
        AMLogger.debug("This is a debug message")
        AMLogger.info("User logged in", "Auth")

        val testException = RuntimeException("This is a test exception")
        AMLogger.error("Network error occurred", "Network", testException)

// Convenience methods
        AMLogger.d("Debug message")
        AMLogger.i("Info message")
        AMLogger.e("Error message")

// Performance measurement
        val result = AMLogger.measure("DatabaseQuery") {
            // Simulate a database query
            Thread.sleep(100)
            "Query result"
        }

// Network logging
        AMLogger.logRequest("https://api.example.com/users", "GET", mapOf("Authorization" to "Bearer token"))
        AMLogger.logResponse("https://api.example.com/users", 200, 150, "{ \"status\" : \"ok\"}")

// Clean shutdown (call in onDestroy or similar)
        AMLogger.shutdown()
    }
}