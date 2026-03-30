package com.openring.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openring.ui.i18n.AppLanguageManager
import com.openring.ui.navigation.OpenRingNavHost
import com.openring.ui.theme.OpenRingTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Bring user to chat when a cloud relay message should run as an in-app task. */
        const val EXTRA_OPEN_CHAT_FROM_RELAY = "open_chat_from_relay"

        /** User-visible task text from WebSocket RELAY_MESSAGE (read by NavHost, then removed). */
        const val EXTRA_RELAY_TASK_TEXT = "relay_task_text"
    }

    private var relayIntentNonce by mutableIntStateOf(0)
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenRingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OpenRingNavHost(relayIntentNonce = relayIntentNonce)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        relayIntentNonce++
    }
}
