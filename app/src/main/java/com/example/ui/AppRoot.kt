package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.data.FestoAppState
import com.example.data.rememberFestoAppState
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.drawer.ConversationDrawer
import com.example.ui.memory.MemorySheet
import com.example.ui.models.ModelPickerSheet
import com.example.ui.usage.UsageSheet
import com.example.ui.voice.VoiceOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    appState: FestoAppState = rememberFestoAppState()
) {
    Crossfade(
        targetState = appState.isAuthenticated,
        label = "auth_chat_crossfade"
    ) { authenticated ->
        if (!authenticated) {
            AuthScreen(appState = appState)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Chat Screen
                ChatScreen(appState = appState)

                // Slide-over Navigation Drawer Layer
                AnimatedVisibility(
                    visible = appState.isDrawerOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { appState.isDrawerOpen = false }
                    ) {
                        ConversationDrawer(
                            appState = appState,
                            onClose = { appState.isDrawerOpen = false },
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }

                // Modal Model Picker Bottom Sheet
                if (appState.isModelSheetOpen) {
                    ModelPickerSheet(
                        appState = appState,
                        onDismiss = { appState.isModelSheetOpen = false }
                    )
                }

                // Modal Memory Bottom Sheet
                if (appState.isMemorySheetOpen) {
                    MemorySheet(
                        appState = appState,
                        onDismiss = { appState.isMemorySheetOpen = false }
                    )
                }

                // Modal Usage Bottom Sheet
                if (appState.isUsageSheetOpen) {
                    UsageSheet(
                        appState = appState,
                        onDismiss = { appState.isUsageSheetOpen = false }
                    )
                }

                // Full-Screen Interactive Voice Overlay
                AnimatedVisibility(
                    visible = appState.isVoiceOverlayOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    VoiceOverlay(
                        appState = appState,
                        onClose = { appState.isVoiceOverlayOpen = false }
                    )
                }
            }
        }
    }
}
