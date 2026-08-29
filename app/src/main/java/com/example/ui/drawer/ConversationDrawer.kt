package com.example.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FestoAppState
import com.example.ui.components.NovaAvatar
import com.example.ui.theme.FestoTheme

@Composable
fun ConversationDrawer(
    appState: FestoAppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp),
        color = extendedColors.surfaceDialog
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row: Brand + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NovaAvatar(size = 30.dp)
                    Text(
                        text = "FESTO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close navigation",
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Start Fresh Action Button -- resets the ONE real server-side
            // session (shared with Telegram), not a fake local thread.
            Button(
                onClick = { appState.startFreshConversation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("new_chat_drawer_button"),
                shape = RoundedCornerShape(21.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.brandNova,
                    contentColor = Color.White
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Start Fresh",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Nav Links (Memory & Telemetry)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .clickable {
                        onClose()
                        appState.isMemorySheetOpen = true
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cross-Session Memory",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(extendedColors.brandNovaSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${appState.memories.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = extendedColors.brandNova
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .clickable {
                        onClose()
                        appState.isUsageSheetOpen = true
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DataUsage,
                        contentDescription = null,
                        tint = extendedColors.accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Usage & Spend",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$${String.format("%.3f", appState.totalCostUsd)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = extendedColors.accentGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .clickable {
                        onClose()
                        appState.isSettingsSheetOpen = true
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = extendedColors.accentPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = appState.themeMode.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = extendedColors.inkTertiary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pushes the profile footer to the bottom of the drawer (the
            // conversations list that used to sit here is gone -- Wendy has
            // exactly one shared session).
            Spacer(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(10.dp))

            // User Profile Pill Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(extendedColors.brandNovaSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = appState.userDisplayName.take(1),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = extendedColors.brandNova
                                )
                            )
                        }
                        Column {
                            Text(
                                text = appState.userDisplayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = appState.userEmail,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = extendedColors.inkTertiary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { appState.logout() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = extendedColors.inkTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

