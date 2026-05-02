package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun ResetPasswordScreen(
    onPasswordUpdated: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val strErrAllFields = stringResource(R.string.reset_error_all_fields)
    val strErrShort     = stringResource(R.string.reset_error_password_short)
    val strErrMismatch  = stringResource(R.string.reset_error_mismatch)

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.PasswordUpdated) {
            viewModel.resetState()
            onPasswordUpdated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        AppTopBar(
            title    = stringResource(R.string.reset_title),
            subtitle = stringResource(R.string.reset_subtitle)
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
                    HeroIconRing(
                        icon = Icons.Default.Lock,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.reset_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = appColors.primaryText
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.reset_subtitle),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = appColors.secondaryText,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(28.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; localError = null },
                        label = { Text(stringResource(R.string.reset_hint_new_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        isError = localError != null
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; localError = null },
                        label = { Text(stringResource(R.string.reset_hint_confirm_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        isError = localError != null
                    )

                    val displayError = localError ?: (uiState as? AuthUiState.Error)?.message
                    if (displayError != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            when {
                                newPassword.isBlank() || confirmPassword.isBlank() ->
                                    localError = strErrAllFields
                                newPassword.length < 6 ->
                                    localError = strErrShort
                                newPassword != confirmPassword ->
                                    localError = strErrMismatch
                                else -> viewModel.updatePassword(newPassword)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = uiState !is AuthUiState.Loading
                    ) {
                        if (uiState is AuthUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.reset_btn_update),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
