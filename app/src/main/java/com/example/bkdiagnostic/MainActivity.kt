package com.example.bkdiagnostic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bkdiagnostic.ui.screens.ForgotPasswordScreen
import com.example.bkdiagnostic.ui.screens.LoginScreen
import com.example.bkdiagnostic.ui.screens.RegisterScreen
import com.example.bkdiagnostic.ui.screens.ResetPasswordScreen
import com.example.bkdiagnostic.ui.screens.SplashScreen
import com.example.bkdiagnostic.ui.theme.BKDiagnosticTheme
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Xử lý deep link khi app được mở từ email
        handleAuthDeeplink(intent)

        setContent {
            BKDiagnosticTheme {
                val navController = rememberNavController()

                // Lắng nghe navigation event từ deep link handler
                LaunchedEffect(Unit) {
                    NavigationManager.events.collect { route ->
                        navController.navigate(route) {
                            // Xóa cả splash khỏi backstack để LaunchedEffect của nó không tiếp tục chạy
                            popUpTo("splash") {
                                inclusive = true
                            }
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onNavigateToMain = {
                                    navController.navigate("main") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = {
                                    navController.navigate("register")
                                },
                                onNavigateToForgotPassword = {
                                    navController.navigate("forgot_password")
                                }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("forgot_password") {
                            ForgotPasswordScreen(
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("reset_password") {
                            ResetPasswordScreen(
                                onPasswordUpdated = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            MainScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "bkdiagnostic") return

        lifecycleScope.launch {
            runCatching {
                when (uri.host) {
                    "auth" -> {
                        // Xác nhận đăng ký: PKCE flow dùng ?code=, SplashScreen sẽ phát hiện session
                        val code = uri.getQueryParameter("code")
                        if (!code.isNullOrBlank()) {
                            supabaseClient.auth.exchangeCodeForSession(code)
                        }
                    }
                    "reset" -> {
                        // Đặt lại mật khẩu: PKCE flow (?code=) hoặc implicit flow (#access_token=)
                        val code = uri.getQueryParameter("code")
                        if (!code.isNullOrBlank()) {
                            // PKCE flow (mặc định của Supabase)
                            supabaseClient.auth.exchangeCodeForSession(code)
                        }
                        // Dù dùng flow nào, điều hướng đến màn hình đặt lại mật khẩu
                        NavigationManager.navigate("reset_password")
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Chào mừng đến với BK Diagnostic!")
    }
}
