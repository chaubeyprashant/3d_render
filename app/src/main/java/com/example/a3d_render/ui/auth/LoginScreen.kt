package com.example.a3d_render.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colors matching the reference screenshots ──
private val ScreenBackground = Color(0xFF0F1923)
private val CardBackground = Color(0xFF162230)
private val CardBorder = Color(0xFF243447)
private val AccentTeal = Color(0xFF4DD0C8)
private val AccentTealDim = Color(0xFF2A8E87)
private val FieldBackground = Color(0xFF0D1620)
private val FieldBorder = Color(0xFF2C3E50)
private val FieldBorderFocused = AccentTeal
private val LabelColor = Color(0xFF8A9BB0)
private val TextPrimary = Color(0xFFE8EDF2)
private val TextSecondary = Color(0xFF8A9BB0)
private val TextDim = Color(0xFF5A6F82)
private val ButtonBackground = Color(0xFF1E3248)
private val ButtonBackgroundActive = Color(0xFF243C54)
private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ScreenBackground, Color(0xFF0A1018))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // ── Branding Section ──
            BrandingSection()

            Spacer(modifier = Modifier.height(32.dp))

            // ── Sign In Card ──
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
            ) {
                SignInCard(
                    uiState = uiState,
                    passwordVisible = passwordVisible,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                    onLoginClick = {
                        focusManager.clearFocus()
                        onLoginClick()
                    },
                    onEmailDone = { focusManager.moveFocus(FocusDirection.Down) },
                    onPasswordDone = {
                        focusManager.clearFocus()
                        onLoginClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BrandingSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Logo text
        Text(
            text = "RPAV",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            fontFamily = FontFamily.SansSerif,
            color = TextPrimary,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hero headline
        Text(
            text = "ACTIVATE YOUR\n3D EXPERIENCE",
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            fontFamily = FontFamily.SansSerif,
            color = AccentTeal,
            letterSpacing = 1.sp,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Subtitle
        Text(
            text = "Secure access to your processed RPAV 3D projects:\nview, inspect and navigate GLB models on tablet.",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Security badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Encrypted session · Offline viewing after login",
                fontSize = 12.sp,
                color = AccentTealDim
            )
        }
    }
}

@Composable
private fun SignInCard(
    uiState: LoginUiState,
    passwordVisible: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onLoginClick: () -> Unit,
    onEmailDone: () -> Unit,
    onPasswordDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp)
    ) {
        // Title
        Text(
            text = "Sign in",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = AccentTeal
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter your credentials to access your 3D workspace.",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Email Field ──
        FieldLabel(text = "EMAIL ID")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (uiState.email.isNotEmpty()) FieldBorderFocused.copy(alpha = 0.5f) else FieldBorder,
                    shape = RoundedCornerShape(10.dp)
                ),
            placeholder = {
                Text(
                    text = "Enter your email",
                    color = TextDim,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { onEmailDone() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FieldBackground,
                unfocusedContainerColor = FieldBackground,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Password Field ──
        FieldLabel(text = "PASSWORD")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (uiState.password.isNotEmpty()) FieldBorderFocused.copy(alpha = 0.5f) else FieldBorder,
                    shape = RoundedCornerShape(10.dp)
                ),
            placeholder = {
                Text(
                    text = "Enter your password",
                    color = TextDim,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = image,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onPasswordDone() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FieldBackground,
                unfocusedContainerColor = FieldBackground,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // ── Error Message ──
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.error,
                    color = ErrorRed,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Login Button ──
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonBackgroundActive,
                contentColor = TextPrimary,
                disabledContainerColor = ButtonBackground,
                disabledContentColor = TextDim
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.isLoading) FieldBorder else CardBorder
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AccentTeal,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Signing in…",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            } else {
                Text(
                    text = "Verify & Continue",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = LabelColor,
        letterSpacing = 1.5.sp
    )
}
