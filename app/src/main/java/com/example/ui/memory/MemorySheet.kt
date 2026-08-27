package com.example.ui.memory

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FestoAppState
import com.example.data.MemoryFact
import com.example.ui.theme.FestoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(
    appState: FestoAppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val extendedColors = FestoTheme.colors
    var newFactInput by remember { mutableStateOf("") }
    var isAddingFact by remember { mutableStateOf(false) }

    val filteredMemories = if (appState.memorySearchQuery.isBlank()) {
        appState.memories
    } else {
        appState.memories.filter {
            it.content.contains(appState.memorySearchQuery, ignoreCase = true) ||
                    it.category.contains(appState.memorySearchQuery, ignoreCase = true)
        }
    }

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
        modifier = modifier.testTag("memory_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
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
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Cross-Session Memory",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${appState.memories.size} durable facts distilled across threads",
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

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = appState.memorySearchQuery,
                onValueChange = { appState.memorySearchQuery = it },
                placeholder = { Text("Search distilled facts...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extendedColors.brandNova,
                    unfocusedBorderColor = extendedColors.borderHairline,
                    focusedContainerColor = extendedColors.surfaceSubtle,
                    unfocusedContainerColor = extendedColors.surfaceSubtle
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Add Memory Toggle / Form
            if (!isAddingFact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                        .clickable { isAddingFact = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add durable fact to assistant memory...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = extendedColors.brandNova,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = newFactInput,
                        onValueChange = { newFactInput = it },
                        placeholder = { Text("e.g. User prefers concise answers with code snippets", fontSize = 12.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = extendedColors.brandNova,
                            unfocusedBorderColor = extendedColors.borderHairline
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(color = extendedColors.inkTertiary),
                            modifier = Modifier
                                .clickable {
                                    isAddingFact = false
                                    newFactInput = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        Button(
                            onClick = {
                                if (newFactInput.isNotBlank()) {
                                    appState.addMemory(newFactInput)
                                    newFactInput = ""
                                    isAddingFact = false
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = extendedColors.brandNova)
                        ) {
                            Text("Save Memory", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Memory Facts List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { fact ->
                    MemoryFactItemCard(
                        fact = fact,
                        onDelete = { appState.deleteMemory(fact.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFactItemCard(
    fact: MemoryFact,
    onDelete: () -> Unit
) {
    val extendedColors = FestoTheme.colors
    val categoryColor = when (fact.category) {
        "Preference" -> extendedColors.accentGreen
        "Decision" -> extendedColors.accentAmber
        "Profile" -> extendedColors.accentBlue
        else -> extendedColors.accentPurple
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.surfaceSubtle)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fact.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = categoryColor
                        )
                    }

                    if (fact.sourceConversationTitle != null) {
                        Text(
                            text = "via ${fact.sourceConversationTitle}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = extendedColors.inkTertiary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = fact.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete memory fact",
                    tint = extendedColors.inkTertiary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
