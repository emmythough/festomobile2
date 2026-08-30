package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.data.FestoAppState
import com.example.data.rememberFestoAppState
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.drawer.ConversationDrawer
import com.example.ui.memory.MemoryScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.usage.UsageSheet

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

                // Slide-over Navigation Drawer Layer. The scrim fades while
                // the drawer itself slides in from the left edge -- same
                // tween(220, FastOutSlowInEasing) curve the message list
                // uses -- so it opens from the edge instead of materializing.
                AnimatedVisibility(
                    visible = appState.isDrawerOpen,
                    enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(220, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { appState.isDrawerOpen = false }
                    )
                }
                AnimatedVisibility(
                    visible = appState.isDrawerOpen,
                    enter = slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) { -it } + fadeIn(tween(220, easing = FastOutSlowInEasing)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) { -it } + fadeOut(tween(220, easing = FastOutSlowInEasing)),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    ConversationDrawer(
                        appState = appState,
                        onClose = { appState.isDrawerOpen = false }
                    )
                }

                // Modal Usage Bottom Sheet
                if (appState.isUsageSheetOpen) {
                    UsageSheet(
                        appState = appState,
                        onDismiss = { appState.isUsageSheetOpen = false }
                    )
                }

                // Modal Settings Bottom Sheet
                if (appState.isSettingsSheetOpen) {
                    SettingsScreen(
                        appState = appState,
                        onDismiss = { appState.isSettingsSheetOpen = false }
                    )
                }

                // Full-Screen Wendy Memory Screen -- gateway session list +
                // read-only transcripts with a client-side search filter
                // (the gateway has no search endpoint to call).
                AnimatedVisibility(
                    visible = appState.isMemoryBrowserOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    MemoryScreen(
                        appState = appState,
                        onClose = { appState.isMemoryBrowserOpen = false }
                    )
                }
            }
        }
    }
}
