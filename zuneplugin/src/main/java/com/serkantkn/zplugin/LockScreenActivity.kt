package com.serkantkn.zplugin

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            var currentTime by remember { mutableStateOf("") }
            var currentDate by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                while (true) {
                    val now = Date()
                    currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                    currentDate = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
                    kotlinx.coroutines.delay(1000)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = currentTime,
                            fontSize = 72.sp,
                            color = Color.White
                        )
                        Text(
                            text = currentDate,
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD81B60))
                        ) {
                            Text("Kilidi Aç")
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // Consumed to prevent lock bypass
    }
}
