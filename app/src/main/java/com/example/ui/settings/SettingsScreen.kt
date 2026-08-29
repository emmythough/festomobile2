package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FestoAppState
import com.example.data.ThemeMode
import com.example.ui.theme.FestoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: FestoAppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val extendedColors = FestoTheme.colors
    val hapticFeedback = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.surfaceDialog,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extendedColors.borderMedium)
            )
        },
        modifier = modifier.testTag("settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header -- same icon + title/subtitle + close pattern as
            // MemorySheet / ModelPickerSheet / UsageSheet.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Changes apply instantly and are remembered",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = extendedColors.inkTertiary
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance section
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                ),
                color = extendedColors.inkTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Theme segmented control -- taps switch the live scheme
            // immediately (MainActivity recomposes from appState.themeMode)
            // and write through to the persisted store.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = appState.themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) extendedColors.brandNova else Color.Transparent)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                appState.setThemeMode(mode)
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            ),
                            color = if (isSelected) Color.White else extendedColors.inkTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "System follows your device's light or dark setting.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = extendedColors.inkTertiary
                )
            )
        }
    }
}
