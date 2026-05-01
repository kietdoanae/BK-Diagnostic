package com.example.bkdiagnostic

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bkdiagnostic.ui.screens.BrandSelectionScreen
import com.example.bkdiagnostic.ui.screens.DashboardScreen
import com.example.bkdiagnostic.ui.screens.DiagnosticScreen
import com.example.bkdiagnostic.ui.screens.ModelSelectionScreen
import com.example.bkdiagnostic.ui.screens.ForgotPasswordScreen
import com.example.bkdiagnostic.ui.screens.LoginScreen
import com.example.bkdiagnostic.ui.screens.RegisterScreen
import com.example.bkdiagnostic.ui.screens.ResetPasswordScreen
import com.example.bkdiagnostic.ui.screens.SplashScreen
import com.example.bkdiagnostic.SettingsViewModel
import com.example.bkdiagnostic.ui.screens.SettingsScreen
import com.example.bkdiagnostic.ui.screens.WiringDiagramScreen
import com.example.bkdiagnostic.ui.screens.LabModeScreen
import com.example.bkdiagnostic.ui.theme.BKDiagnosticTheme
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.ui.components.LabModeBanner
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase
            .getSharedPreferences("bk_settings", Context.MODE_PRIVATE)
            .getString("language", "en") ?: "en"
        val locale = java.util.Locale.forLanguageTag(lang)
        java.util.Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Xử lý deep link khi app được mở từ email
        handleAuthDeeplink(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val displaySettings by settingsViewModel.displaySettings.collectAsStateWithLifecycle()

            SideEffect {
                val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                if (displaySettings.keepScreenOn) window.addFlags(flags)
                else window.clearFlags(flags)
            }

            // Locale switching: khi language thay đổi → lưu vào SharedPreferences → recreate()
            var prevLanguage by remember { mutableStateOf(displaySettings.language) }
            LaunchedEffect(displaySettings.language) {
                if (displaySettings.language != prevLanguage) {
                    prevLanguage = displaySettings.language
                    getSharedPreferences("bk_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("language", displaySettings.language)
                        .apply()
                    recreate()
                }
            }


            BKDiagnosticTheme(displaySettings = displaySettings) {
                val authViewModel: AuthViewModel = viewModel()
                val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
                val isAdmin        = userProfile?.isAdmin == true
                val canViewRawFrame = userProfile?.canViewRawFrame == true

                val navController = rememberNavController()
                val labModeState by LabModeManager.state.collectAsStateWithLifecycle()

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

                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                    // Session đã tồn tại — reload profile trên Activity ViewModel
                                    authViewModel.reloadProfile()
                                    navController.navigate("main") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    // Login thành công — reload profile trên Activity-scoped ViewModel
                                    // để isAdmin được cập nhật đúng cho DiagnosticScreen
                                    authViewModel.reloadProfile()
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
                            DashboardScreen(
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onDiagnosticsClick = {
                                    navController.navigate("brand_selection")
                                },
                                onWiringDiagramClick = {
                                    navController.navigate("wiring_diagram")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                authViewModel    = authViewModel,
                                settingsViewModel = settingsViewModel
                            )
                        }
                        composable("wiring_diagram") {
                            WiringDiagramScreen(onBack = { navController.popBackStack() })
                        }
                        composable("brand_selection") {
                            BrandSelectionScreen(
                                onBrandSelected = { brand ->
                                    navController.navigate("model_selection/${brand.id}")
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("model_selection/{brandId}") { backStack ->
                            val brandId = backStack.arguments?.getString("brandId") ?: "ford"
                            ModelSelectionScreen(
                                brandId = brandId,
                                onModelSelected = { model ->
                                    navController.navigate("diagnostic/$brandId/${model.id}")
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("diagnostic/{brandId}/{modelId}") { backStack ->
                            val brandId = backStack.arguments?.getString("brandId") ?: "ford"
                            val modelId = backStack.arguments?.getString("modelId") ?: "ranger"
                            val diagSettings by settingsViewModel.diagnosticsSettings
                                .collectAsStateWithLifecycle()
                            DiagnosticScreen(
                                brandId        = brandId,
                                modelId        = modelId,
                                onBack         = { navController.popBackStack() },
                                application    = application,
                                isAdmin        = canViewRawFrame,
                                diagSettings   = diagSettings,
                                onLabModeClick = { navController.navigate("lab_mode") }
                            )
                        }
                        composable("lab_mode") {
                            LabModeScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
                LabModeBanner(
                    state    = labModeState,
                    onManage = { navController.navigate("lab_mode") }
                )
                }  // end Box
                }  // end Surface
            }
        }
    }

    /** Tự động đăng xuất khi user tắt app (isFinishing = true).
     *  Không sign out khi xoay màn hình / thay đổi cấu hình (isFinishing = false). */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            lifecycleScope.launch {
                runCatching { supabaseClient.auth.signOut() }
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
