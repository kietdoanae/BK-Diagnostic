package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.AuthUiState
import com.example.bkdiagnostic.AuthViewModel
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.theme.LocalAppColors

@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.EmailSent) {
            emailSent = true
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        AppTopBar(
            title    = stringResource(R.string.forgot_title),
            subtitle = stringResource(R.string.forgot_subtitle),
            onBack   = {
                viewModel.resetState()
                onNavigateToLogin()
            }
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!emailSent) {
                        // Hero icon with glow ring
                        HeroIconRing(
                            icon = Icons.Default.Email,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.forgot_title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = appColors.primaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.forgot_subtitle),
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            color = appColors.secondaryText,
                            lineHeight = 19.sp
                        )

                        Spacer(Modifier.height(28.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.forgot_hint_email)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            isError = uiState is AuthUiState.Error
                        )

                        if (uiState is AuthUiState.Error) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = (uiState as AuthUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (email.isNotBlank()) viewModel.forgotPassword(email.trim())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = email.isNotBlank() && uiState !is AuthUiState.Loading
                        ) {
                            if (uiState is AuthUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    stringResource(R.string.forgot_btn_send),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    } else {
                        // Success state
                        HeroIconRing(
                            icon = Icons.Default.MarkEmailRead,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.forgot_success_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = appColors.primaryText
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "${stringResource(R.string.forgot_success_message)} $email ${stringResource(R.string.forgot_success_message2)}",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = appColors.secondaryText,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF8E1)
                        ) {
                            Text(
                                text = stringResource(R.string.forgot_spam_warning),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF8D6E00),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetState()
                            onNavigateToLogin()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.btn_back_to_sign_in),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** Shared hero icon — vòng glow ngoài + icon trong vòng nhỏ. */
@Composable
internal fun HeroIconRing(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 88.dp
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.36f)
            )
        }
    }
}
