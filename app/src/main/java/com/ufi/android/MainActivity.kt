package com.ufi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ufi.android.ui.screens.ChatScreen
import com.ufi.android.ui.theme.DarkBackground
import com.ufi.android.ui.theme.UfiTheme
import com.ufi.android.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UfiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground,
                ) {
                    val viewModel: ChatViewModel = viewModel()
                    ChatScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
