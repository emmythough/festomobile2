package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuthMode
import com.example.data.FestoAppState
import com.example.ui.components.NovaAvatar
import com.example.ui.theme.FestoTheme

@Composable
fun AuthScreen(
    appState: FestoAppState,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var emailInput by remember { mutableStateOf("demo@festo.app") }
    var passwordInput by remember { mutableStateOf("festo1234") }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Nova Avatar & Brand Header
                NovaAvatar(
                    size = 64.dp,
                    isPulsing = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "FESTO",
                    style = MaterialTheme.typography.displayMedium.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Unified Intelligence • Models • Voice",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = extendedColors.inkTertiary
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Auth Card Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mode Selector Segmented Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(extendedColors.surfaceContainer)
                                .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (appState.authMode == AuthMode.SIGN_IN) extendedColors.surfaceSubtle
                                        else Color.Transparent
                                    )
                                    .clickable { appState.selectAuthMode(AuthMode.SIGN_IN) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (appState.authMode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (appState.authMode == AuthMode.SIGN_IN) MaterialTheme.colorScheme.onSurface else extendedColors.inkTertiary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (appState.authMode == AuthMode.CREATE_ACCOUNT) extendedColors.surfaceSubtle
                                        else Color.Transparent
                                    )
                                    .clickable { appState.selectAuthMode(AuthMode.CREATE_ACCOUNT) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create Account",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (appState.authMode == AuthMode.CREATE_ACCOUNT) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (appState.authMode == AuthMode.CREATE_ACCOUNT) MaterialTheme.colorScheme.onSurface else extendedColors.inkTertiary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                appState.authError = null
                            },
                            label = { Text("Email address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Email,
                                    contentDescription = null,
                                    tint = extendedColors.brandNova
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = extendedColors.brandNova,
                                unfocusedBorderColor = extendedColors.borderHairline,
                                focusedContainerColor = extendedColors.surfaceContainer,
                                unfocusedContainerColor = extendedColors.surfaceContainer
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                appState.authError = null
                            },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = extendedColors.brandNova
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = extendedColors.inkTertiary
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { appState.submitAuth(emailInput, passwordInput) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = extendedColors.brandNova,
                                unfocusedBorderColor = extendedColors.borderHairline,
                                focusedContainerColor = extendedColors.surfaceContainer,
                                unfocusedContainerColor = extendedColors.surfaceContainer
                            )
                        )

                        // Error Message
                        AnimatedVisibility(
                            visible = appState.authError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            appState.authError?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Submit Button
                        Button(
                            onClick = { appState.submitAuth(emailInput, passwordInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extendedColors.brandNova,
                                contentColor = Color.White
                            ),
                            enabled = !appState.authInFlight
                        ) {
                            if (appState.authInFlight) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (appState.authMode == AuthMode.SIGN_IN) "Enter Festo" else "Create Account",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Demo Fill Shortcut Chip
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(extendedColors.brandNovaSoft)
                                .clickable {
                                    emailInput = "demo@festo.app"
                                    passwordInput = "festo1234"
                                    appState.submitAuth(emailInput, passwordInput)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = extendedColors.brandNova,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Quick Demo Sign-In",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = extendedColors.brandNova,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
