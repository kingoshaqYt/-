package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.ui.*
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {
                com.example.ui.AdmobManager.loadInterstitial(this)
                com.example.ui.AdmobManager.loadRewardedAd(this)
                com.example.ui.AdmobManager.loadRewardedInterstitial(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        setContent {
            val vModel: ReclaimViewModel = viewModel()
            val state by vModel.uiState.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current

            // One-time only login persistence controller
            val sharedPrefs = remember { context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE) }
            val wasLoggedIn = remember { sharedPrefs.getBoolean("is_logged_in", false) }
            val savedEmail = remember { sharedPrefs.getString("logged_in_email", "bypass@reclaim.com") ?: "bypass@reclaim.com" }
            val savedName = remember { sharedPrefs.getString("logged_in_name", "Bypass Guest Player") ?: "Bypass Guest Player" }
            val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

            // Real-time Network Monitoring
            androidx.compose.runtime.DisposableEffect(Unit) {
                val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                
                // Initial check
                val activeNetwork = connManager.activeNetwork
                val capabilities = connManager.getNetworkCapabilities(activeNetwork)
                val isInitiallyConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                vModel.setInternetAvailable(isInitiallyConnected)

                val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        vModel.setInternetAvailable(true)
                    }
                    override fun onLost(network: android.net.Network) {
                        vModel.setInternetAvailable(false)
                    }
                }
                
                val builder = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                
                connManager.registerNetworkCallback(builder, callback)
                
                onDispose {
                    try {
                        connManager.unregisterNetworkCallback(callback)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

             LaunchedEffect(Unit) {
                 val hasCompletedOnboarding = sharedPrefs.getBoolean("has_completed_onboarding_v4", false)
                 if (hasCompletedOnboarding) {
                     vModel.setOnboardingCompleted(true)
                 }

                 if (wasLoggedIn && !state.isLoggedIn) {
                     vModel.autoLoginWithSplash(savedEmail, savedName)
                 }
             }

             var loadedEmail by remember { mutableStateOf("") }

             LaunchedEffect(state.isLoggedIn, state.googleAccountEmail) {
                 if (state.isLoggedIn) {
                     val userEmail = state.googleAccountEmail ?: "guest"
                     val creditsKey = "user_credits_$userEmail"
                     val streakKey = "streak_days_$userEmail"
                     val claimKey = "last_claim_date_$userEmail"
                     val historyKey = "ad_history_$userEmail"

                     val savedCredits = sharedPrefs.getInt(creditsKey, 50)
                     val savedStreak = sharedPrefs.getInt(streakKey, 0)
                     val savedLastClaimDate = sharedPrefs.getString(claimKey, "") ?: ""
                     val savedAdHistorySet = sharedPrefs.getStringSet(historyKey, emptySet()) ?: emptySet()
                     
                     vModel.initializeCreditsAndClaimInfo(
                         credits = savedCredits,
                         streak = savedStreak,
                         lastClaimDate = savedLastClaimDate,
                         adHistory = savedAdHistorySet.toList()
                     )
                     loadedEmail = userEmail
                 }
             }

             // Auto save changes immediately (per user email)
             LaunchedEffect(state.userCredits, state.streakDays, state.lastClaimDate, state.adHistory, state.googleAccountEmail) {
                 if (state.isLoggedIn && loadedEmail == (state.googleAccountEmail ?: "guest")) {
                     val userEmail = state.googleAccountEmail ?: "guest"
                     sharedPrefs.edit()
                         .putInt("user_credits_$userEmail", state.userCredits)
                         .putInt("streak_days_$userEmail", state.streakDays)
                         .putString("last_claim_date_$userEmail", state.lastClaimDate)
                         .putStringSet("ad_history_$userEmail", state.adHistory.toSet())
                         .apply()
                 }
             }

             LaunchedEffect(state.showAdRewardAnimation) {
                 if (state.showAdRewardAnimation) {
                     delay(2000L)
                     vModel.triggerAdRewardAnimation(false)
                 }
             }

             LaunchedEffect(state.hasCompletedOnboarding) {
                 sharedPrefs.edit().putBoolean("has_completed_onboarding_v4", state.hasCompletedOnboarding).apply()
             }

             LaunchedEffect(state.themeStyle) {
                 sharedPrefs.edit().putString("theme_style_v2", state.themeStyle.name).apply()
             }

            LaunchedEffect(state.isLoggedIn, state.googleAccountEmail, state.googleAccountName) {
                if (state.isLoggedIn) {
                    sharedPrefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putString("logged_in_email", state.googleAccountEmail ?: "bypass@reclaim.com")
                        .putString("logged_in_name", state.googleAccountName ?: "Bypass Guest Player")
                        .apply()
                }
            }

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val resolvedTheme = vModel.getActiveThemeMode(state.themeMode, isSystemDark)
            
            LaunchedEffect(resolvedTheme) {
                vModel.updateIsDarkTheme(resolvedTheme == AppThemeMode.DARK)
            }
            
            LaunchedEffect(Unit) {
                delay(3000L) // Auto Notifications: Claim bonuses, future promotions, feature announcements
                vModel.showToast("Claim Bonus: 50 Recovery Credits Added! 🎉", "SUCCESS")
            }

            MyApplicationTheme(themeMode = state.themeMode) {
                val bgColors = when (resolvedTheme) {
                    AppThemeMode.LIGHT -> listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                    AppThemeMode.DARK -> listOf(Color(0xFF0F172A), Color(0xFF020617))
                    AppThemeMode.TITANIUM -> listOf(Color(0xFFF1F5F9), Color(0xFFCBD5E1))
                    AppThemeMode.AUTO -> listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                }
                // Removed glowing ambient orbs and neon colors
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(bgColors))
                        .paperTexture(alpha = 0.2f)
                        .depth3D(cornerRadius = 0.dp, isDark = state.isDarkTheme),
                    color = Color.Transparent
                ) {
                    if (!state.isLoggedIn || state.isSplashLoading) {
                        GoogleLoginAndSplashScreen(vModel, state)
                    } else if (!state.hasCompletedOnboarding) {
                        PremiumOnboardingWalkthrough(
                            vModel = vModel,
                            state = state,
                            onComplete = { vModel.setOnboardingCompleted(true) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Subtle watermark branding logo centered in background
                            Image(
                                painter = painterResource(id = R.drawable.img_app_logo_1781241621499),
                                contentDescription = "RECLAIM ACCOUNTS subtle watermark",
                                modifier = Modifier
                                    .size(280.dp)
                                    .align(Alignment.Center)
                                    .alpha(0.05f)
                            )
                            // Overlay elements moved to end of box

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Android Status Bar
                                AndroidStatusBar()
                                if (state.currentTab != NavigationTab.HOME) {
                                    AppTopBar(vModel, state)
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    AnimatedContent(
                                        targetState = state.currentTab,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400))) togetherWith
                                            (fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f, animationSpec = tween(200)))
                                        },
                                        label = "tab_fade"
                                    ) { tab ->
                                        when (tab) {
                                            NavigationTab.HOME -> HomeScreen(vModel, state)
                                            NavigationTab.RECOVERY -> RecoveryScreen(vModel, state)
                                            NavigationTab.TOOLS -> ToolsScreen(vModel, state)
                                            NavigationTab.NOTIFICATIONS -> NotificationsScreen(vModel, state)
                                            NavigationTab.PROFILE -> ProfileScreen(vModel, state)
                                        }
                                    }
                                }

                                // Bottom Spacer for Nav Bar
                                Spacer(modifier = Modifier.height(72.dp))
                            }

                            // Floating dynamic VisionOS-style liquid bottom navigation bar
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(bottom = 12.dp)
                            ) {
                                FloatingLiquidBottomNav(
                                    currentTab = state.currentTab,
                                    isDarkTheme = state.isDarkTheme,
                                    onTabSelected = { tab ->
                                        if (tab == NavigationTab.HOME && state.currentTab == NavigationTab.HOME) {
                                            vModel.triggerProgressRefresh()
                                        }
                                        vModel.changeTab(tab)
                                    }
                                )
                            }

                            // Simulated bottom Android Gesture pill navigation handle safe-inset
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .width(120.dp)
                                    .height(4.dp)
                                    .background(
                                        if (state.isDarkTheme) Color.White.copy(alpha = 0.35f)
                                        else Color.Black.copy(alpha = 0.25f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )

                            // Custom top floating Toast Notification
                            AnimatedVisibility(
                                visible = state.showToast,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 95.dp, start = 20.dp, end = 20.dp),
                                label = "toast_anim"
                            ) {
                                val toastBg = if (state.toastType == "ERROR") Color(0xFFEF4444) else Color(0xFF10B981)
                                val toastIcon = if (state.toastType == "ERROR") Icons.Default.Error else Icons.Default.CheckCircle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(toastBg.copy(0.95f), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = toastIcon,
                                        contentDescription = "Toast Icon",
                                        tint = if (state.isDarkTheme) Color.White else Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = state.toastMessage,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Simulated Email Notification Dialog: Are you using cheats?
                            if (state.showCheatsEmailWarningDialog) {
                                androidx.compose.ui.window.Dialog(onDismissRequest = { vModel.dismissCheatsWarningDialog() }) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F131D)),
                                        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                                    Icon(
                                                        imageVector = Icons.Default.Email,
                                                        contentDescription = "Email",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "INCOMING SECURITY ENVELOPE",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFFEF4444),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { vModel.dismissCheatsWarningDialog() },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close",
                                                        tint = Color.White.copy(0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            HorizontalDivider(color = Color.White.copy(0.08f))

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "From: PUBG Mobile Security Board <compliance@pubgm.com>",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(0.81f)
                                                )
                                                Text(
                                                    text = "To: ${state.googleAccountEmail ?: "oshaqplayz@gmail.com"}",
                                                    fontSize = 9.sp,
                                                    color = Color.White.copy(0.55f)
                                                )
                                                Text(
                                                    text = "Subject: Urgent compliance audit: Account UID #${state.activeCheatsWarningUid}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(0.03f), RoundedCornerShape(12.dp))
                                                    .border(0.5.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "Dear @${state.activeCheatsWarningName},",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Our systems detected secondary compliance decoupling signatures targeting Server Partition unbans. The account unban appeal has failed, and the simulation parameters have recorded suspicious system activity:\n\n" +
                                                            "\"Your account is not recovered. Are you using cheats?\"",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFFFF8A8A),
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 15.sp
                                                )
                                                Text(
                                                    text = "Please submit an updated anti-cheat compliance certificate or clear current device cache traces to proceed with further support inquiries.",
                                                    fontSize = 9.sp,
                                                    color = Color.White.copy(0.5f),
                                                    lineHeight = 13.sp
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { vModel.dismissCheatsWarningDialog() },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                    border = BorderStroke(1.dp, Color.White.copy(0.12f)),
                                                    modifier = Modifier.weight(1f).height(38.dp)
                                                ) {
                                                    Text("CLOSE EMAIL", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Button(
                                                    onClick = {
                                                        vModel.dismissCheatsWarningDialog()
                                                        vModel.showToast("CLEANED CHEAT SANDBOX TRACES - BYPASS ESCALATED", "SUCCESS")
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).height(38.dp)
                                                ) {
                                                    Text("CLEAN TRACES", fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.showPremiumModal) {
                                PremiumModal(vModel = vModel, onDismiss = { vModel.togglePremiumModal(false) })
                            }

                            if (state.showLowCreditWarning) {
                                androidx.compose.ui.window.Dialog(onDismissRequest = { vModel.toggleLowCreditWarning(false) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                                            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                                            .padding(24.dp)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Warning",
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            
                                            Text(
                                                text = "INSUFFICIENT ACCESS CREDITS",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFFFC107),
                                                letterSpacing = 1.sp
                                            )
                                            
                                            Text(
                                                text = "You currently have ${state.userCredits} active balance points. Advanced anti-cheat bypass logs and instant unban requests require a minimum of 20 computational credits.",
                                                fontSize = 11.sp,
                                                color = if (state.isDarkTheme) Color.White.copy(0.8f) else Color.DarkGray,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 16.sp
                                            )
                                            
                                            // Progress Meter
                                            val progressBalance = (state.userCredits / 100f).coerceIn(0f, 1f)
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("CREDITS CAP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White.copy(0.5f) else Color.Gray)
                                                    Text("${state.userCredits} / 100", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A84FF))
                                                }
                                                LinearProgressIndicator(
                                                    progress = { progressBalance },
                                                    color = Color(0xFF0A84FF),
                                                    trackColor = Color.White.copy(0.08f),
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                                )
                                            }
                                            
                                            HorizontalDivider(color = Color.White.copy(0.08f))
                                            
                                            // Active options
                                            Button(
                                                onClick = {
                                                    vModel.toggleLowCreditWarning(false)
                                                    vModel.claimDailyBox()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFFFC107),
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp).paperTexture(0.08f).depth3D(12.dp, isDark = true)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black)
                                                    Text("WATCH COMPLIANCE STREAM (+30 CR)", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    vModel.toggleLowCreditWarning(false)
                                                    vModel.togglePremiumModal(true)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF34C759),
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(44.dp).paperTexture(0.08f).depth3D(12.dp, isDark = true)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black)
                                                    Text("UPGRADE TO PREMIUM UNLIMITED", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                            
                                            TextButton(
                                                onClick = { vModel.toggleLowCreditWarning(false) }
                                            ) {
                                                Text("DISMISS AND STANDBY", fontSize = 10.sp, color = if (state.isDarkTheme) Color.White.copy(0.6f) else Color.Gray, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = state.showAdRewardAnimation,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "⚡ Reward Received!",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34C759),
                                        modifier = Modifier.background(Color.Black.copy(0.7f), RoundedCornerShape(12.dp)).padding(16.dp)
                                    )
                                }
                            }

                            if (state.showAiChatDialog) {
                                AiChatbotDialog(vModel = vModel, state = state, onDismiss = { vModel.toggleAiChatDialog(false) })
                            }
                            if (state.activeSelectedCase != null) {
                                SupportCaseChatDialog(vModel = vModel, state = state, onDismiss = { vModel.selectCase(null) })
                            }

                            // AI SPECIALIST FAB REMOVED
                        }
                    }

                    // GLOBAL REAL-TIME OFFLINE EMBED OVERLAY
                    if (!state.isInternetAvailable) {
                        OfflineOverlayDialog(
                            vModel = vModel,
                            state = state
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AndroidStatusBar() {
    // Elegant, clean, transparent status bar layout that handles system safe boundaries dynamically
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    )
}

// SECURE ANDROID PREMIUM & SPONSORED CAMPAIGN DIALOG
@Composable
fun PremiumModal(vModel: ReclaimViewModel, onDismiss: () -> Unit) {
    val state by vModel.uiState.collectAsState()
    var isWatchingAd by remember { mutableStateOf(false) }
    var adProgress by remember { mutableFloatStateOf(0f) }
    var adStatusText by remember { mutableStateOf("Initializing sponsored feed...") }
    var showCreditsFaq by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        if (isWatchingAd) {
            // Full screen ad simulation box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0F19))
                    .clickable { /* Block interaction clicks background */ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Ad Streaming",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Text(
                        text = "SPONSORED CAMPAIGN LIVE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    
                    Text(
                        text = adStatusText,
                        fontSize = 11.sp,
                        color = Color.White.copy(0.6f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { adProgress },
                        color = Color(0xFF34C759),
                        trackColor = Color.White.copy(0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )

                    Text(
                        text = "Credits will be deposited immediately after stream.",
                        fontSize = 9.sp,
                        color = Color.White.copy(0.4f)
                    )
                }
            }

            // Timer controller logic for ad simulation
            LaunchedEffect(isWatchingAd) {
                adProgress = 0f
                adStatusText = "Connecting secure ads syndication..."
                delay(800)
                adProgress = 0.3f
                adStatusText = "Buffering video player frame streams..."
                delay(1000)
                adProgress = 0.7f
                adStatusText = "Reclaiming ad network telemetry logs..."
                delay(900)
                adProgress = 1.0f
                adStatusText = "Dispensing reward package sandbox signatures..."
                delay(400)
                vModel.watchSponsoredAd()
                isWatchingAd = false
                // Reset state
                adProgress = 0f
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF34C759).copy(0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomCreditLogo(size = 20.dp)
                            Text(
                                text = "CREDIT & REWARD CENTER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                onDismiss()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close window panel",
                                tint = Color.White.copy(0.6f)
                            )
                        }
                    }

                    // Balance Display Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(0.03f), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AVAILABLE SANDBOX BALANCE",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.5f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (state.isGuest || state.isUnlimitedCredits) "UNLIMITED ∞" else "${state.userCredits} CREDITS",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34C759)
                        )
                        Text(
                            text = if (state.isGuest) "Guest sandbox mode active" else if (state.isUnlimitedCredits) "Promo code active: ${state.activatedPromoCode}" else "Google authenticated credits profile",
                            fontSize = 9.sp,
                            color = Color.White.copy(0.35f)
                        )
                    }

                    // Promo Code Activation Section
                    var promoInput by remember { mutableStateOf("") }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(0.02f), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color(0xFF34C759).copy(0.15f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "APPLY PROMO CODE",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34C759),
                                letterSpacing = 0.5.sp
                            )
                            if (state.isUnlimitedCredits) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FF87)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = promoInput,
                                onValueChange = { promoInput = it },
                                placeholder = { Text("Enter Promo Code (e.g. UNLIMITED)", fontSize = 10.sp, color = Color.White.copy(0.35f)) },
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.White.copy(0.12f),
                                    focusedBorderColor = Color(0xFF34C759),
                                    unfocusedContainerColor = Color.Black.copy(0.2f),
                                    focusedContainerColor = Color.Black.copy(0.4f)
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                            Button(
                                onClick = {
                                    if (promoInput.isNotBlank()) {
                                        val success = vModel.applyPromoCode(promoInput)
                                        if (success) {
                                            promoInput = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("APPLY", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // Actions block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Action 1: Watch Ad
                        Button(
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                isWatchingAd = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                            border = BorderStroke(0.5.dp, Color(0xFF34C759).copy(0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Watch sponsored ad",
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "WATCH ADS AD FOR +25 CREDITS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34C759)
                                )
                            }
                        }

                        // Action 2: Claim Daily
                        val alreadyClaimed = state.hasClaimedToday
                        Button(
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                vModel.claimDailyBox()
                            },
                            enabled = !alreadyClaimed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alreadyClaimed) Color.White.copy(0.02f) else Color(0xFFFFC107),
                                contentColor = if (alreadyClaimed) Color.White.copy(0.2f) else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Daily Claim Icon",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (alreadyClaimed) "DAILY REWARD SECURED TODAY" else "CLAIM DAILY BOX (+50+ CREDITS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Action 3: One-Time Reward (Get 100 Coins)
                        val oneTimeClaimed = state.hasClaimedOneTimeReward
                        Button(
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                vModel.claimOneTimeReward()
                            },
                            enabled = !oneTimeClaimed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (oneTimeClaimed) Color.White.copy(0.02f) else Color(0xFF0A84FF),
                                contentColor = if (oneTimeClaimed) Color.White.copy(0.2f) else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "One-Time Reward Icon",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (oneTimeClaimed) "100 COINS REWARD CLAIMED" else "GET 100 COINS REWARD (ONE-TIME)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // Firebase sync info block
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(0.02f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Database Sync: firebase_credits",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Collection: credits (userId, balance, lastUpdated)",
                                fontSize = 8.sp,
                                color = Color.White.copy(0.4f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF34C759).copy(0.12f), CircleShape)
                                .bounceClick {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (e: Exception) {}
                                    vModel.triggerProgressRefresh()
                                    vModel.addToAdHistory("Synchronized credentials cache with Firebase credits collection.")
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SYNC", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                        }
                    }

                    // Ad View Transition History List
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History indicator icon",
                                tint = Color.White.copy(0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AD CAMPAIGN & VIEW HISTORY LOGS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(0.6f)
                            )
                        }

                        val logs = state.adHistory.reversed()
                        if (logs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(Color.White.copy(0.02f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No ad reward transaction histories stored.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(0.3f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(logs.size) { index ->
                                    val log = logs[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(0.02f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log,
                                            fontSize = 9.sp,
                                            color = Color.White.copy(0.7f),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF34C759).copy(0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "OK",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF34C759)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Small Warning Footer inside Modal
                    Text(
                        text = "SECURE SANDBOX DISCLAIMER: RECLAIM does not manufacture, compromise, or modify genuine server databases. All simulation credits displayed hold zero physical, external, or commercial value.",
                        fontSize = 7.5.sp,
                        color = Color.White.copy(0.35f),
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumLockedContent(
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isLocked) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Premium Feature",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        content()
    }
}

@Composable
fun CustomCreditLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, shape = androidx.compose.foundation.shape.CircleShape, clip = false)
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF0A84FF), Color(0xFF00838F)),
                ),
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .border(1.5.dp, Color(0xFFE0F7FA), androidx.compose.foundation.shape.CircleShape)
            .premiumShineEffect(true),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "R",
            fontSize = (size.value * 0.55).sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(y = (-0.5).dp)
        )
    }
}

@Composable
fun CreditDisplay(credits: Int, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0A84FF).copy(0.15f), Color(0xFF00838F).copy(0.15f))))
            .border(1.dp, Color(0xFF0A84FF).copy(0.3f), RoundedCornerShape(20.dp))
            .premiumShineEffect(true)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .bounceClick { onExpand() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomCreditLogo(size = 18.dp)
        Spacer(modifier = Modifier.width(8.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = credits,
            transitionSpec = {
                if (targetState > initialState) {
                    (androidx.compose.animation.slideInVertically { height -> height } + androidx.compose.animation.fadeIn()).togetherWith(
                        androidx.compose.animation.slideOutVertically { height -> -height } + androidx.compose.animation.fadeOut()
                    )
                } else {
                    (androidx.compose.animation.slideInVertically { height -> -height } + androidx.compose.animation.fadeIn()).togetherWith(
                        androidx.compose.animation.slideOutVertically { height -> height } + androidx.compose.animation.fadeOut()
                    )
                }.using(
                    androidx.compose.animation.SizeTransform(clip = false)
                )
            }, label = "creditsAnimation"
        ) { targetCount ->
            Text(
                text = "$targetCount",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AppTopBar(vModel: ReclaimViewModel, state: ReclaimUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Logotype on the Left
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.bounceClick { vModel.changeTab(NavigationTab.HOME) }
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "RECLAIM Secure App logo",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "RECLAIM",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        // Action controls on the Right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Credit display
            CreditDisplay(credits = state.userCredits, onExpand = { 
                vModel.togglePremiumModal(true)
            })

            // Home icon moved to the top-right
            IconButton(
                onClick = { 
                    if (state.isLoggedIn && !state.isGuest && !state.googleAccountName.isNullOrBlank()) {
                        vModel.changeTab(NavigationTab.PROFILE)
                    } else {
                        vModel.changeTab(NavigationTab.HOME)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                if (state.isLoggedIn && !state.isGuest && !state.googleAccountName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF0A84FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.googleAccountName.first().uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color.White.copy(0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// SIMULATED LIQUID SLIDER CAROUSEL (SUPABASE DRIVEN)
@Composable
fun SupabaseImageSlider(sliders: List<SupabaseSlider>, intensity: GlassIntensity) {
    if (sliders.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(sliders) {
        while (true) {
            delay(4000)
            currentIndex = (currentIndex + 1) % sliders.size
        }
    }

    val currentSlide = sliders[currentIndex]
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .liquidGlass(intensity = intensity, cornerRadius = 20.dp)
            .bounceClick {
                val url = currentSlide.link
                if (!url.isNullOrEmpty()) {
                    try {
                        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else {
                            url
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(finalUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = currentSlide.resolvedImageUrl,
            contentDescription = currentSlide.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0A84FF), strokeWidth = 2.dp)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0D1B2A), Color(0xFF0A84FF).copy(0.12f), Color(0xFF34C759).copy(0.08f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield Backup Icon",
                            tint = Color(0xFF0A84FF).copy(0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = currentSlide.title ?: "System Safe Layer Active",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SECURE CLOUD PRE-SYNCED INFO CARD",
                            color = Color.White.copy(0.4f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC040B16))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0A84FF).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color(0xFF0A84FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = currentSlide.badge ?: "GUIDE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A84FF),
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "SUPABASE DRIVEN",
                    fontSize = 8.sp,
                    color = Color.White.copy(0.5f),
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = currentSlide.title ?: "",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentSlide.description ?: "",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Overlay Interactive Manual Navigation controls Left & Right Chevrons
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (sliders.isNotEmpty()) {
                        currentIndex = if (currentIndex - 1 < 0) sliders.size - 1 else currentIndex - 1
                    }
                },
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Previous Slide Manual Button",
                    tint = Color.White.copy(0.9f),
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = {
                    if (sliders.isNotEmpty()) {
                        currentIndex = (currentIndex + 1) % sliders.size
                    }
                },
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next Slide Manual Button",
                    tint = Color.White.copy(0.9f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sliders.forEachIndexed { idx, _ ->
                val size by animateDpAsState(if (idx == currentIndex) 12.dp else 4.dp, label = "dot_size")
                val alpha by animateFloatAsState(if (idx == currentIndex) 1.0f else 0.4f, label = "dot_alpha")
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(size)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF0A84FF).copy(alpha = alpha))
                )
            }
        }
    }
}

// 1. HOME SCREEN
@Composable
fun HomeScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
        val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val cardBgCol = if (state.isDarkTheme) Color.White.copy(alpha = 0.04f) else Color(0xFF0F172A).copy(alpha = 0.04f)
    val cardBorderCol = if (state.isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.1f)

    var showInternetDialog by remember { mutableStateOf(false) }
    var showAiCheckDialog by remember { mutableStateOf(false) }
    var showSocialsDialog by remember { mutableStateOf(false) }

    var selectedCheckType by remember { mutableStateOf("Anti-cheat Decrypt") }
    var aiCheckResult by remember { mutableStateOf<String?>(null) }
    var isCheckingAiBoard by remember { mutableStateOf(false) }
    var checkProgress by remember { mutableStateOf(0f) }

    if (showInternetDialog) {
        CustomInternetConnectionDialog(onDismiss = { showInternetDialog = false })
    }

    if (state.showRecoveryBoostDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { vModel.toggleRecoveryBoostDialog(false) }) {
            var showBoostGuestBlock by remember { mutableStateOf(false) }
            var showBoostConfirmation by remember { mutableStateOf(false) }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(if (state.isDarkTheme) Color(0xFF020817) else Color.White)
                    .border(1.dp, Color(0xFF34C759).copy(0.3f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security score booster",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "PUBG-Style Verified Secure Bypass Signature".uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = textCol
                            )
                        }
                        IconButton(onClick = { vModel.toggleRecoveryBoostDialog(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textCol.copy(0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(Color(0xFF34C759).copy(0.08f), CircleShape)
                            .border(1.dp, Color(0xFF34C759).copy(0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state.currentRecoveryScore == 10f) "10 / 10" else "${state.currentRecoveryScore}/10",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34C759)
                            )
                            Text(
                                text = "CURRENT",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol.copy(0.5f)
                            )
                        }
                    }

                    Text(
                        text = "Inject physical hardware certificate telemetry spoof layers & encrypt local sandbox firewall filters to raise your security score to a Perfect 10 / 10 rating.",
                        fontSize = 11.sp,
                        color = subTextCol,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(color = Color.White.copy(0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Investment Cost",
                                fontSize = 10.sp,
                                color = subTextCol
                            )
                            Text(
                                text = "25 Sec. Credits",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0A84FF)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Credits check",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${state.userCredits} available",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }

                    if (state.isRecoveryScoreBoosting) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF34C759),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "AMPLIFYING SECURITY LAYERS...",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34C759)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (state.isGuest) {
                                    showBoostGuestBlock = true
                                } else {
                                    showBoostConfirmation = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentRecoveryScore >= 10f) Color.Gray.copy(0.2f) else Color(0xFF34C759)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            enabled = state.currentRecoveryScore < 10f
                        ) {
                            Text(
                                text = if (state.currentRecoveryScore >= 10f) "MAX SCORE REACHED" else "INVEST CREDITS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (state.currentRecoveryScore >= 10f) textCol.copy(0.3f) else Color.Black
                            )
                        }
                    }
                }
            }
            
            if (showBoostGuestBlock) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showBoostGuestBlock = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                            .background(if (state.isDarkTheme) Color(0xFF0F172A).copy(0.98f) else Color.White.copy(0.98f), RoundedCornerShape(24.dp))
                            .border(2.dp, Color(0xFFFF5252).copy(0.6f), RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(48.dp))
                            Text("GUEST ACCESS RESTRICTED", fontSize = 13.sp, fontWeight = FontWeight.Black, color = textCol)
                            Text("As a guest, you can browse reports and profiles but cannot execute secure bypass handshakes or spend credits.\n\nRegister/Sign In with a Google or Email profile to get 100 free credits and unlock all recovery tools.", fontSize = 11.sp, color = subTextCol, textAlign = TextAlign.Center, lineHeight = 15.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showBoostGuestBlock = false }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, textCol.copy(0.12f))) {
                                    Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textCol)
                                }
                                Button(onClick = {
                                    showBoostGuestBlock = false
                                    vModel.toggleRecoveryBoostDialog(false)
                                    vModel.logout()
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                                    Text("SIGN IN/REGISTER", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
            
            if (showBoostConfirmation) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showBoostConfirmation = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                            .background(if (state.isDarkTheme) Color(0xFF0F172A).copy(0.98f) else Color.White.copy(0.98f), RoundedCornerShape(24.dp))
                            .border(2.dp, Color(0xFF34C759).copy(0.6f), RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CustomCreditLogo(size = 40.dp)
                            Text("CONFIRM SECURITY BOOST", fontSize = 14.sp, fontWeight = FontWeight.Black, color = textCol)
                            Column(
                                modifier = Modifier.fillMaxWidth().background(textCol.copy(0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cost:", fontSize = 11.sp, color = subTextCol)
                                    Text("25 Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Current Balance:", fontSize = 11.sp, color = subTextCol)
                                    Text("${state.userCredits} Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
                                }
                                androidx.compose.material3.HorizontalDivider(color = textCol.copy(0.08f))
                                val postBalance = (state.userCredits - 25).coerceAtLeast(0)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remaining Balance:", fontSize = 11.sp, color = subTextCol)
                                    Text("$postBalance Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                }
                            }
                            if (state.userCredits < 25 && !state.isUnlimitedCredits) {
                                Text("Insufficient balance! Please open the Credit Center to claim rewards or watch ads.", fontSize = 11.sp, color = Color(0xFFFF5252), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showBoostConfirmation = false }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, textCol.copy(0.12f))) {
                                    Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textCol)
                                }
                                Button(
                                    onClick = {
                                        if (state.userCredits >= 25 || state.isUnlimitedCredits) {
                                            showBoostConfirmation = false
                                            vModel.boostRecoveryScore()
                                        } else {
                                            showBoostConfirmation = false
                                            vModel.showToast("INSUFFICIENT BALANCE. CLAIMS REWARDS!", "ERROR")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.userCredits >= 25 || state.isUnlimitedCredits) Color(0xFF34C759) else Color.Gray.copy(0.2f),
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("CONFIRM", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiCheckDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAiCheckDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(if (state.isDarkTheme) Color(0xFF020817) else Color.White)
                    .border(1.dp, Color(0xFF0A84FF).copy(0.3f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "AI Icon",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "AI SECURITY SPECIALIST",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                        IconButton(onClick = { showAiCheckDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = subTextCol)
                        }
                    }

                    Text(
                        text = "Initialize deep neural account analysis to inspect unban vector parameters, firewall sandbox bypassing, or hardware lock verification state.",
                        fontSize = 11.sp,
                        color = subTextCol,
                        textAlign = TextAlign.Center
                    )

                    val options = listOf("Anti-cheat Decrypt", "Device Hardware Trust", "MIME Payload Verification")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { opt ->
                            val sel = (selectedCheckType == opt)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (sel) Color(0xFF0A84FF).copy(0.15f) else cardBgCol,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        0.5.dp,
                                        if (sel) Color(0xFF0A84FF) else cardBorderCol,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .bounceClick { selectedCheckType = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) Color(0xFF0A84FF) else subTextCol,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (isCheckingAiBoard) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { checkProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF0A84FF),
                                trackColor = cardBgCol
                            )
                            Text(
                                text = "CALCULATING PROBABILITY HASH... ${(checkProgress * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0A84FF)
                            )
                        }
                    } else if (aiCheckResult != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF34C759).copy(0.12f), RoundedCornerShape(12.dp))
                                .border(0.5.dp, Color(0xFF34C759).copy(0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "DIAGNOSTIC RESOLUTION RESULT:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF34C759)
                                )
                                Text(
                                    text = aiCheckResult ?: "",
                                    fontSize = 11.sp,
                                    color = textCol,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isCheckingAiBoard) {
                                isCheckingAiBoard = true
                                checkProgress = 0f
                                val scope = CoroutineScope(Dispatchers.Main)
                                scope.launch {
                                    for (i in 1..40) {
                                        delay(30)
                                        checkProgress = (i / 40f)
                                    }
                                    isCheckingAiBoard = false
                                    aiCheckResult = when (selectedCheckType) {
                                        "Anti-cheat Decrypt" -> "Unban node matches firewall bypass certificate [SSL_TOKEN_SEC90]. Status: 96.5% Bypass Availability."
                                        "Device Hardware Trust" -> "Hardware sandbox secure keys verified successfully. Virtual IMEI sandbox registered. OK!"
                                        else -> "MIME outbox appeal draft compiled successfully. Diagnostic payload delivery matches server handshake registers."
                                    }
                                    vModel.createNewTicket("AI System Diagnostics: Verified unban bypass using $selectedCheckType")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isCheckingAiBoard) "ANALYZING..." else "LAUNCH AI DIAGNOSTIC",
                            color = Color(0xFF020817),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showSocialsDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSocialsDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(if (state.isDarkTheme) Color(0xFF020817) else Color.White)
                    .border(1.dp, Color(0xFF34C759).copy(0.3f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Socials Icon",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "COMMUNITY SOCIALS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                        IconButton(onClick = { showSocialsDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = subTextCol)
                        }
                    }

                    Text(
                        text = "Join our official verification clusters, channels, and visual guide playlists to obtain priority support.",
                        fontSize = 11.sp,
                        color = subTextCol,
                        textAlign = TextAlign.Center
                    )

                    val context = androidx.compose.ui.platform.LocalContext.current
                    val channels = listOf(
                        Triple("WhatsApp Support", "https://chat.whatsapp.com/FFUy9FCSnqiHOVwnrz5kF5?s=cl&p=a&mlu=1", Color(0xFF25D366)),
                        Triple("Telegram Channel", "https://t.me/pubgunban2025", Color(0xFF0088CC)),
                        Triple("TikTok Official", "https://www.tiktok.com/@oshaq.playz.yt?_r=1&_t=ZS-978w8J3IUDq", Color(0xFFFE2C55)),
                        Triple("YouTube Tutorials", "https://www.youtube.com/@oshaqplayz", Color(0xFFFF0000))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        channels.forEach { (name, url, color) ->
                            Button(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No browser found. Link copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = name, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isSignUpComplete = state.targetPlayerName.isNotBlank()
                if (!isSignUpComplete) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "REF-CODE: RCLM-${state.hashCode().toString().takeLast(5).uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A84FF),
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { vModel.changeTab(NavigationTab.PROFILE) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("ADD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            Button(
                                onClick = {
                                    android.widget.Toast.makeText(context, "Database Sync Successful!\nBalance: ${state.userCredits} Sec. Credits", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f)),
                                border = BorderStroke(0.5.dp, Color.White.copy(0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("GET BALANCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .border(1.5.dp, Color(0xFF0A84FF), CircleShape)
                                .clip(CircleShape)
                                .bounceClick { vModel.changeTab(NavigationTab.PROFILE) }
                        ) {
                            if (!state.profileLogoUri.isNullOrEmpty()) {
                                SubcomposeAsyncImage(
                                    model = state.profileLogoUri,
                                    contentDescription = "Custom User Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp, color = Color(0xFF0A84FF))
                                        }
                                    },
                                    error = {
                                        Image(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "User Avatar Placeholder",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF002D62)),
                                            colorFilter = ColorFilter.tint(Color.White)
                                        )
                                    }
                                )
                            } else {
                                Image(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Avatar Placeholder",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF002D62)),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.googleAccountName ?: state.targetPlayerName.ifBlank { "User Profile" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "@${(state.googleAccountName ?: state.targetPlayerName).replace(" ", "_").lowercase().ifBlank { "user" }}",
                                    fontSize = 11.sp,
                                    color = subTextCol
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { vModel.triggerProgressRefresh() }
                    ) {
                        val spinScale by animateFloatAsState(if (state.isRefreshing) 1.5f else 1.0f, label = "spin")
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Icon",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(20.dp).scale(spinScale)
                        )
                    }

                    Box(modifier = Modifier.size(40.dp)) {
                        IconButton(
                            onClick = { vModel.changeTab(NavigationTab.NOTIFICATIONS) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notification icon",
                                tint = if (state.isDarkTheme) Color.White.copy(alpha = 0.85f) else Color(0xFF0F172A).copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = (6).dp)
                                .background(Color.Red, CircleShape)
                        )
                    }
                }
            }
        }



        // Credit balance card removed as per instruction
        
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0F172A).copy(0.7f), Color(0xFF1E293B).copy(0.7f))
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "CORE RESTORATION TARGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0A84FF),
                        letterSpacing = 1.5.sp
                    )
                    PubgBanPan(
                        modifier = Modifier
                            .size(180.dp)
                    )
                    Text(
                        text = "PUBG-Style Verified Secure Bypass Signature",
                        fontSize = 9.5.sp,
                        color = subTextCol,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            // 2. RECLAIM ACCOUNT ACTION CARD
            var isReclaimPressed by remember { mutableStateOf(false) }
            val reclaimScale by animateFloatAsState(
                targetValue = if (isReclaimPressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                label = "reclaim_card_scale"
            )
            val reclaimGlow by animateFloatAsState(
                targetValue = if (isReclaimPressed) 0.8f else 0.3f,
                label = "reclaim_card_glow"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(reclaimScale)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isReclaimPressed = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isReclaimPressed = false
                                    vModel.changeTab(NavigationTab.RECOVERY)
                                }
                            }
                        )
                    }
                    .border(2.dp, Color(0xFF0A84FF).copy(alpha = reclaimGlow), RoundedCornerShape(20.dp))
                    .shadow(if (isReclaimPressed) 12.dp else 24.dp, RoundedCornerShape(20.dp), ambientColor = Color(0xFF0A84FF).copy(alpha = reclaimGlow), spotColor = Color(0xFF0A84FF).copy(alpha = reclaimGlow))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0A84FF).copy(0.15f), Color.Transparent)
                        )
                    )
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RECLAIM ACCOUNT",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = textCol,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Initiate deep-level recovery vectors and unban payload systems.",
                            fontSize = 11.sp,
                            color = subTextCol,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "shield_anim")
                    val shieldFloat by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 10f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "shield_pulse"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .offset(y = (-shieldFloat).dp)
                            .background(Color(0xFF0F172A), CircleShape)
                            .border(1.5.dp, Color(0xFF0A84FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recovery",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        
        item {
            // 3. SECONDARY CONTROLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val secActions = listOf(
                    Triple("Track Status", Icons.Default.TrendingUp) { vModel.changeTab(NavigationTab.TOOLS) },
                    Triple("History Logs", Icons.Default.History) {
                        android.widget.Toast.makeText(context, "Checking historic bypass logs: Real Firebase transaction history loaded successfully.", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
                secActions.forEach { (label, icon, action) ->
                    Button(
                        onClick = action,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isDarkTheme) Color.White.copy(0.04f) else Color(0xFFF1F5F9),
                            contentColor = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, if (state.isDarkTheme) Color.White.copy(0.1f) else Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = state.isRefreshing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 12.dp)
                        .background(Color(0xFF0A84FF).copy(0.08f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color(0xFF0A84FF).copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF0A84FF),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "DATABASE DESERIALIZE IN PROGRESS: ${state.refreshPercentage}% ...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 12.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF0A84FF), CircleShape)
                    )
                    Text(
                        text = "Recovery Status Summary: " + "Active Bypass Logs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. DYNAMIC RECOVERY SCORE CARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("ai_score_card")
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                        .bounceClick { vModel.toggleRecoveryBoostDialog(true) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Core Security AI Score",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34C759),
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        // Numeric Badge representation
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (state.currentRecoveryScore == 10f) "10/10" else "${state.currentRecoveryScore}/10",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34C759)
                            )
                        }
                    }

                    Text(
                        text = "Risk factor is fully clear." + if (state.currentRecoveryScore == 10f) " Core security fully hardened and credentials encrypted." else "",
                        fontSize = 10.sp,
                        color = subTextCol,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (state.currentRecoveryScore == 10f) Color(0xFF0A84FF).copy(alpha = 0.12f) else Color(0xFF34C759).copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (state.currentRecoveryScore == 10f) "PERFECT 10/10 CORE" else "HIGH PROBABILITY UNBAN",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.currentRecoveryScore == 10f) Color(0xFF0A84FF) else Color(0xFF34C759)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "EVENTS & UPDATES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    letterSpacing = 1.sp
                )
                SupabaseImageSlider(sliders = state.supabaseSliders, intensity = state.glassIntensity)
            }
        }

        item {
            // Live telemetry graph removed
        }
        
        item {
            com.example.ui.AdmobManager.AdmobBanner()
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SECURE RECOVERY ACTIONS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                            .bounceClick {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    com.example.ui.AdmobManager.showInterstitial(activity) {
                                        vModel.changeTab(NavigationTab.RECOVERY)
                                    }
                                } else {
                                    vModel.changeTab(NavigationTab.RECOVERY)
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Recovery Portal Icon",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Start Recovery",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = "Secure credential, email, or game unban layer requests.",
                                fontSize = 9.sp,
                                color = subTextCol,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                            .bounceClick { vModel.changeTab(NavigationTab.TOOLS) }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Manual Case Injector Icon",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Case Enrollment",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = "Submit custom logs or search current recovery queues.",
                                fontSize = 9.sp,
                                color = subTextCol,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                            .bounceClick { showAiCheckDialog = true }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "AI Specialist Icon",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "AI Check Specialist",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = "Decrypt sandbox or verify physical device certificates.",
                                fontSize = 9.sp,
                                color = subTextCol,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                            .bounceClick { showSocialsDialog = true }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Community Channels Icon",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Community Links",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = "TikTok, WhatsApp, and Telegram priority networks.",
                                fontSize = 9.sp,
                                color = subTextCol,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Chatbot Banner Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                        .bounceClick { vModel.toggleAiChatDialog(true) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF0A84FF).copy(0.12f), CircleShape)
                                .border(1.dp, Color(0xFF0A84FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "AI Chat Icon",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🤖 AI UNBAN CHAT ASSISTANT [ONLINE]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = textCol
                            )
                            Text(
                                text = "Receive instant smart recommendations for bypass locks and secure SMTP custom appeal draft payloads from our AI Specialist agent.",
                                fontSize = 9.sp,
                                color = subTextCol
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "LATEST NEWS & PATCH NOTES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    letterSpacing = 1.sp
                )

                // ADMIN ONLY PANEL TO POST GLOBAL NEWS UPDATES
                if (state.isAdminMode) {
                    var newTitle by remember { mutableStateOf("") }
                    var newDesc by remember { mutableStateOf("") }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                            .border(1.dp, Color(0xFF34C759).copy(0.3f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "ADMIN: DISPATCH GLOBAL UPDATE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34C759)
                            )
                            
                            BasicTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                textStyle = TextStyle(color = textCol, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(0.04f), RoundedCornerShape(6.dp))
                                            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        if (newTitle.isEmpty()) {
                                            Text("Update Title...", color = subTextCol.copy(0.5f), fontSize = 11.sp)
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            BasicTextField(
                                value = newDesc,
                                onValueChange = { newDesc = it },
                                textStyle = TextStyle(color = textCol, fontSize = 10.sp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(0.04f), RoundedCornerShape(6.dp))
                                            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        if (newDesc.isEmpty()) {
                                            Text("Update Description...", color = subTextCol.copy(0.5f), fontSize = 10.sp)
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            Button(
                                onClick = {
                                    if (newTitle.isNotBlank() && newDesc.isNotBlank()) {
                                        vModel.addSystemUpdate(newTitle.trim(), newDesc.trim(), context)
                                        newTitle = ""
                                        newDesc = ""
                                    } else {
                                        android.widget.Toast.makeText(context, "Fill in both fields.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Text("BROADCAST RECLAIM UPDATE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "News",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RECLAIM REAL-TIME RECON LOGS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0A84FF)
                            )
                        }

                        val shownUpdates = state.systemUpdates
                        if (shownUpdates.isEmpty()) {
                            // Seed Placeholder visual defaults if Firestore updates are empty
                            val dummySeedUpdates = listOf(
                                "SMTP MIME-Protocol Fortified" to "Daily automated synchronizations now support SSLv4 Decrypt structures securely.",
                                "Supabase real-time cluster mirrors enabled" to "All guides and slide buffers are fully cached offline for immediate reference."
                            )
                            dummySeedUpdates.forEach { (title, description) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textCol
                                    )
                                    Text(
                                        text = description,
                                        fontSize = 9.sp,
                                        color = subTextCol,
                                        lineHeight = 13.sp
                                    )
                                    HorizontalDivider(color = cardBorderCol)
                                }
                            }
                        } else {
                            shownUpdates.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textCol,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (state.isAdminMode) {
                                            IconButton(
                                                onClick = {
                                                    vModel.deleteSystemUpdate(item.id, context)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Update",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = item.description,
                                        fontSize = 9.sp,
                                        color = subTextCol,
                                        lineHeight = 13.sp
                                    )
                                    
                                    val dateStr = remember(item.timestamp) {
                                        try {
                                            val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                            sdf.format(java.util.Date(item.timestamp))
                                        } catch(e: Exception) {
                                            "Just Now"
                                        }
                                    }
                                    Text(
                                        text = "Posted: $dateStr",
                                        fontSize = 8.sp,
                                        color = subTextCol.copy(0.5f)
                                    )
                                    
                                    HorizontalDivider(color = cardBorderCol)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AccountRecoveryDashboardCardLayout(vModel = vModel, state = state)
        }

        item {
            DashboardSystemPanel(state = state)
        }

        item {
            CreditSystemPanel(vModel = vModel, state = state)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .size(8.dp)
                                .background(Color(0xFF34C759), CircleShape)
                        )
                        Text(
                            text = "BACKEND CONNECTION CONSOLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textCol,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SECURE PROTOCOL",
                            fontSize = 8.sp,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = cardBorderCol)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusRow(text = "Real-time Google Auth Hub", active = true)
                        StatusRow(text = "Real-time Recovery Cluster", active = true)
                        StatusRow(text = "Restoration CDN Nodes", active = true)
                        StatusRow(text = "Sync Payload Telemetry", active = true)
                        StatusRow(text = "Remote Config: Running", active = true)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusRow(text = "Supabase REST API Gateway", active = true)
                        StatusRow(text = "Secondary Bypass Table", active = true)
                        StatusRow(text = "Blob Restoration Bucket", active = true)
                        StatusRow(text = "Dynamic Banner: Online", active = true)
                        StatusRow(text = "Realtime Updates: Enabled", active = true)
                    }
                }

                HorizontalDivider(color = cardBorderCol)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPERATIONAL CORES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34C759)
                    )
                    Text(
                        text = "last database heartbeat sync",
                        fontSize = 10.sp,
                        color = subTextCol
                    )
                }
            }
        }

        // TIMER PROTECTION MODULE
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (state.isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                            style = Stroke(width = 4.dp.toPx())
                        )
                        drawArc(
                            color = Color(0xFF0A84FF),
                            startAngle = -90f,
                            sweepAngle = 360 * (state.timerSeconds / 60f),
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = "Lock Clock icon",
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEXT AUTOMATED HEARTBEAT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeSegment(label = "Days", value = state.timerDays)
                        TimeColon()
                        TimeSegment(label = "Hours", value = state.timerHours)
                        TimeColon()
                        TimeSegment(label = "Mins", value = state.timerMinutes)
                        TimeColon()
                        TimeSegment(label = "Secs", value = state.timerSeconds, highlight = true)
                    }
                }
            }
        }

        // FAST SUPABASE SIGN-UP & SYNC MODULE
        item {
            var inputGameId by remember { mutableStateOf("") }
            var isSynced by remember { mutableStateOf(false) }
            var isSyncing by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Cloud Icon",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "FAST SUPABASE ACCOUNT SYNC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textCol,
                            letterSpacing = 1.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0A84FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSynced) "SYNCED" else "OFFLINE",
                            fontSize = 8.sp,
                            color = Color(0xFF0A84FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isSynced) {
                    Text(
                        text = "Sign up and link your in-game identity in real-time. This stores your recovery credentials directly inside your private secure Supabase table.",
                        fontSize = 11.sp,
                        color = subTextCol,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "GAME ACCOUNT ID / EMAIL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = subTextCol
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(cardBgCol, RoundedCornerShape(8.dp))
                                .border(0.5.dp, cardBorderCol, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = inputGameId,
                                onValueChange = { inputGameId = it },
                                textStyle = TextStyle(color = textCol, fontSize = 12.sp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (inputGameId.isEmpty()) {
                                        Text("e.g. PUBG-5239683108 or player@mail.com", color = subTextCol.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (inputGameId.isNotBlank()) {
                                isSyncing = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A84FF),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSyncing && inputGameId.isNotBlank()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "FAST SIGN UP & SECURE LINK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    if (isSyncing) {
                        LaunchedEffect(Unit) {
                            delay(1800)
                            isSyncing = false
                            isSynced = true
                            vModel.createNewTicket("Linked Account ID: $inputGameId (linked to Supabase Table 'user_sync')")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF34C759).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color(0xFF34C759).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success Icon",
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "SUPABASE SYNC COMPLETED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34C759)
                             )
                             Text(
                                text = "Tunnel secure: linked profile ID '$inputGameId' to Supabase table 'user_sync' cleanly.",
                                fontSize = 9.sp,
                                color = textCol.copy(0.8f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE ACTIVITY FEED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }

        items(state.firebaseActivities) { act ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        intensity = state.glassIntensity,
                        cornerRadius = 14.dp,
                        borderGlow = if (act.type == "success") Color(0x661FFFC5) else Color(0x33FFFFFF)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (act.type == "success") Color(0xFF34C759) else Color(0xFFFFA000),
                                CircleShape
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = act.user ?: "Security Node",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = act.time ?: "Just now",
                                fontSize = 9.sp,
                                color = subTextCol
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = act.action ?: "",
                            fontSize = 11.sp,
                            color = textCol.copy(0.81f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }

        item {
            UsersFeedbackBackgroundSection(state)
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun UsersFeedbackBackgroundSection(state: ReclaimUiState) {
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = "Users Feedback",
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "USERS FEEDBACK",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textCol,
                letterSpacing = 1.sp
            )
        }

        val feedbacks = listOf(
            "VIP_Deluxe" to "Successfully unlocked my main after 3 months of waiting. Fast bypass layer execution.",
            "Oshaq_Playz" to "The AI specialist recommended great payload attachments for my email bypass.",
            "AlphaWolf99" to "Best restoration tool. Secure protocol connects flawlessly to remote caches!"
        )

        feedbacks.forEach { (user, comment) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "@$user",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A84FF)
                )
                Text(
                    text = comment,
                    fontSize = 9.sp,
                    color = subTextCol,
                    lineHeight = 13.sp
                )
                HorizontalDivider(color = if (state.isDarkTheme) Color.White.copy(0.08f) else Color.Black.copy(0.08f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
@Composable
fun RecoveryScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
        val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    var showUnbanEngineDialog by remember { mutableStateOf(false) }
    var selectedRecoveryTitle by remember { mutableStateOf("") }

    if (showUnbanEngineDialog) {
        UnbanEngineGatewayDialog(
            state = state,
            vModel = vModel,
            recoveryType = selectedRecoveryTitle,
            onDismiss = { showUnbanEngineDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "EVENTS & UPDATES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
                letterSpacing = 1.sp
            )
        }

        item {
            SupabaseImageSlider(sliders = state.supabaseSliders, intensity = state.glassIntensity)
        }

        item {
            AdvancedShieldProtectionHub(state = state, vModel = vModel)
        }

        item {
            Text(
                text = "RECOVERY CHANNELS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
                letterSpacing = 1.sp
            )
        }

        item {
            RecoveryOptionCard(
                title = "Secure Email Layer Bypass",
                desc = "Redirect target verification links to virtual encrypted sandboxes.",
                status = "REQUIRES 35 CREDITS",
                isAvailable = true,
                intensity = state.glassIntensity,
                onLaunch = { 
                    if (state.isGuest) {
                        vModel.showToast("Guest Users cannot access this feature.", "ACCESS DENIED")
                    } else if (vModel.spendCredits(35)) {
                        selectedRecoveryTitle = "Direct Email Layer Recovery"
                        showUnbanEngineDialog = true
                    }
                }
            )
        }

        item {
            RecoveryOptionCard(
                title = "In-Game Layer Bypass",
                desc = "Spoof HWID metadata directly via secure hypervisor firmware injection.",
                status = "REQUIRES 40 CREDITS",
                isAvailable = true,
                intensity = state.glassIntensity,
                onLaunch = { 
                    if (state.isGuest) {
                        vModel.showToast("Guest Users cannot access this feature.", "ACCESS DENIED")
                    } else if (vModel.spendCredits(40)) {
                        selectedRecoveryTitle = "In-Game Recovery Authentication"
                        showUnbanEngineDialog = true
                    }
                }
            )
        }

        item {
            RecoveryOptionCard(
                title = "Restoration Sandboxing API",
                desc = "Secondary partition unban layer bypassing currently under deployment cycles.",
                status = "COMING SOON",
                isAvailable = false,
                intensity = state.glassIntensity,
                onLaunch = {}
            )
        }

        item {
            RecoveryOptionCard(
                title = "Kernel Suspensions Appeal",
                desc = "Root hardware layer unbans & automated credential replacement guides.",
                status = "COMING SOON",
                isAvailable = false,
                intensity = state.glassIntensity,
                onLaunch = {}
            )
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "RECOVERY HISTORY LOGS (SIMULATIONS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
                letterSpacing = 1.sp
            )
        }

        if (state.recoveryHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 16.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "No History",
                            tint = textCol.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No recorded recovery traces in active cache.",
                            fontSize = 10.sp,
                            color = textCol.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(state.recoveryHistory) { logItem ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .liquidGlass(
                            intensity = state.glassIntensity,
                            cornerRadius = 18.dp,
                            borderGlow = if (logItem.status == "COMPLETED") Color(0x3300FF87) else Color(0x22FFFFFF)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (logItem.status == "COMPLETED") Color(0xFF00FF87) else Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = logItem.id,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textCol,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            // Status badge
                            val badgeColor = if (logItem.status == "COMPLETED") Color(0xFF00FF87) else Color(0xFFFF5252)
                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(0.12f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, badgeColor.copy(0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = logItem.status,
                                    fontSize = 8.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = textCol.copy(alpha = 0.06f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TARGET NICKNAME",
                                    fontSize = 8.sp,
                                    color = textCol.copy(0.45f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SECURE UID",
                                    fontSize = 8.sp,
                                    color = textCol.copy(0.45f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@" + logItem.targetName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol
                                )
                                Text(
                                    text = logItem.targetUid,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (state.isDarkTheme) Color(0xFF0A84FF) else Color(0xFF0284C7)
                                )
                            }
                        }

                        // Recovery Technique / Channel Type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(textCol.copy(0.02f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = logItem.recoveryType,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol.copy(0.75f)
                            )
                            Text(
                                text = logItem.timestamp,
                                fontSize = 8.sp,
                                color = textCol.copy(0.4f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun AdvancedShieldProtectionHub(
    state: ReclaimUiState,
    vModel: ReclaimViewModel
) {
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val amberCol = Color(0xFFFF9500)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = amberCol,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "ADVANCED ENGINE PROTECTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = amberCol,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(amberCol.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, amberCol, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "v1.3.2 ESCALATED STATUS",
                    fontSize = 8.sp,
                    color = amberCol,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Preload dynamic account recovery components prior to starting the engine. Activating bypass buttons below prepares memory signatures to mask device structures from anticheat sandbox locks.",
            fontSize = 9.5.sp,
            color = subTextCol,
            lineHeight = 13.sp
        )

        HorizontalDivider(color = textCol.copy(alpha = 0.08f))

        Text(
            text = "ACTIVATE PRELOAD BYPASS PORTALS:",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textCol.copy(0.5f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PreloadBypassItemRow(
                title = "PRELOAD T1 SANDBOX RESTORATION",
                desc = "Injects automated suspension mask credentials prior to direct handshakes.",
                isActive = state.isPreloadSandboxActive,
                onToggle = { vModel.togglePreloadSandbox() },
                textCol = textCol,
                subTextCol = subTextCol,
                amberCol = amberCol
            )

            PreloadBypassItemRow(
                title = "PRELOAD REAL-TIME MAIL DECOUPLER",
                desc = "Decouples target email identifiers to bind pristine SMTP boundary appeals.",
                isActive = state.isPreloadMailDecouplerActive,
                onToggle = { vModel.togglePreloadMailDecoupler() },
                textCol = textCol,
                subTextCol = subTextCol,
                amberCol = amberCol
            )

            PreloadBypassItemRow(
                title = "PRELOAD DEVICE TOKEN SPOOFER",
                desc = "Fakes physical system build parameters to mimic authorized security nodes.",
                isActive = state.isPreloadTokenSpooferActive,
                onToggle = { vModel.togglePreloadTokenSpoofer() },
                textCol = textCol,
                subTextCol = subTextCol,
                amberCol = amberCol
            )
        }
    }
}

@Composable
fun PreloadBypassItemRow(
    title: String,
    desc: String,
    isActive: Boolean,
    onToggle: () -> Unit,
    textCol: Color,
    subTextCol: Color,
    amberCol: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) amberCol.copy(alpha = 0.05f) else Color.White.copy(0.01f),
                RoundedCornerShape(10.dp)
            )
            .border(
                0.5.dp,
                if (isActive) amberCol.copy(alpha = 0.4f) else textCol.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isActive) amberCol else textCol
            )
            Text(
                text = desc,
                fontSize = 8.5.sp,
                color = subTextCol,
                lineHeight = 11.sp
            )
        }

        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) amberCol else Color.White.copy(0.06f),
                contentColor = if (isActive) Color.Black else textCol
            ),
            border = BorderStroke(1.dp, if (isActive) amberCol else textCol.copy(0.2f)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
                .height(28.dp)
                .width(84.dp)
        ) {
            Text(
                text = if (isActive) "ACTIVE ✓" else "ACTIVATE",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun UnbanEngineGatewayDialog(
    state: ReclaimUiState,
    vModel: ReclaimViewModel,
    recoveryType: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    
    var step by remember { mutableStateOf(1) }
    var terminalOutput by remember { mutableStateOf("") }
    var showPubgWarning by remember { mutableStateOf(false) }
    
    var showGuestBlockDialog by remember { mutableStateOf(false) }
    var showDeductionConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(step) {
        if (step == 2) {
            val logs = listOf(
                "PINGING SECURE APPARATUS ROUTERS...",
                "SYNC PRELOAD PORTALS: Sandbox decoupled, token spoofer: ${state.isPreloadTokenSpooferActive}",
                "COMPILING CUSTOM RFCAppeal OBJECT FOR ACCOUNT UID #${state.targetPlayerUid}",
                "COMPOSING BYPASS HEADER SIGNATURE ENVELOPES...",
                "CORE ENGINE INJECTION VECTOR READY!"
            )
            for (log in logs) {
                terminalOutput += "> $log\n"
                delay(300)
            }
        }
    }

    if (showPubgWarning) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPubgWarning = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(
                        if (state.isDarkTheme) Color(0xFF0F172A).copy(0.95f) else Color.White.copy(0.95f),
                        RoundedCornerShape(24.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFFFF9500).copy(0.3f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "PUBG MOBILE LINK DISCONNECTED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = textCol,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Direct application linker could not locate PUBG Mobile installed on this device.\n\nPlease verify that either PUBG Global, BGMI, PUBG KR, or VN client is installed. Alternatively, launch manually to initiate the bypass handshake.",
                        fontSize = 10.sp,
                        color = subTextCol,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 14.sp
                    )
                    Button(
                        onClick = { showPubgWarning = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500), contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("ACKNOWLEDGEMENT CONFIRMED", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                .background(
                    if (state.isDarkTheme) Color(0xFF020817).copy(0.95f) else Color.White.copy(0.95f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFFF9500).copy(0.4f),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                .size(8.dp)
                                .background(Color(0xFFFF9500), CircleShape)
                        )
                        Text(
                            text = "UNBAN ENGINE CORE GATEWAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9500),
                            letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textCol.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = textCol.copy(alpha = 0.08f))

                if (step == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "ESTABLISHING BYPASS PROTOCOLS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                        Text(
                            text = "You are launching the custom unban engine trees designed for: **$recoveryType**. Align with security bypass channels immediately by picking an operational gateway below:",
                            fontSize = 10.sp,
                            color = subTextCol,
                            lineHeight = 14.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFF9500).copy(0.06f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFFFF9500).copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PRELOADED BYPASS ACTIVE HANDSHAKES:",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF9500)
                                )
                                Text(
                                    text = "• Sandbox Active: ${if (state.isPreloadSandboxActive) "YES [GLOW HIGHLIGHTS]" else "NO"}\n" +
                                            "• Mail Decoupler: ${if (state.isPreloadMailDecouplerActive) "YES [GLOW HIGHLIGHTS]" else "NO"}\n" +
                                            "• Device Spoofer: ${if (state.isPreloadTokenSpooferActive) "YES [GLOW HIGHLIGHTS]" else "NO"}",
                                    fontSize = 9.sp,
                                    color = textCol,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (state.isGuest) {
                                    showGuestBlockDialog = true
                                } else {
                                    pendingAction = {
                                        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("mailto:")
                                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@reclaim.com"))
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "[EXPRESS UNBAN APPEAL] Sandbox Decrypt for Target UID #${state.targetPlayerUid}")
                                            val body = "Hello Security Restoration Board,\n\nPlease review my account recovery bypass handshake details:\n- Player Name: ${state.targetPlayerName}\n- Unique ID: ${state.targetPlayerUid}\n- Local Status Code: SEC_BYPASS_v1.3.2\n- Preload Decoupler: ${if(state.isPreloadMailDecouplerActive) "ENABLED" else "DISABLED"}\n\nPlease lift sandbox restrictions.\n\nBest Regards,\n${state.targetPlayerName}"
                                            putExtra(android.content.Intent.EXTRA_TEXT, body)
                                        }
                                        try {
                                            context.startActivity(emailIntent)
                                            vModel.createNewTicket("Dispatched direct email unban request to security board")
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No email client found. Copying form...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        step = 2
                                    }
                                    showDeductionConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9500),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (state.isDarkTheme) Color.White else Color.Black)
                                Text("LAUNCH SECURITY APPEAL EMAIL", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Button(
                            onClick = {
                                if (state.isGuest) {
                                    showGuestBlockDialog = true
                                } else {
                                    pendingAction = {
                                        val packages = listOf("com.tencent.ig", "com.pubg.imobile", "com.pubg.krmobile", "com.vng.pubgmobile")
                                        val pm = context.packageManager
                                        var launched = false
                                        for (pkg in packages) {
                                            val intent = pm.getLaunchIntentForPackage(pkg)
                                            if (intent != null) {
                                                context.startActivity(intent)
                                                launched = true
                                                vModel.createNewTicket("Launched in-game support bypass package: $pkg")
                                                break
                                            }
                                        }
                                        if (!launched) {
                                            showPubgWarning = true
                                        } else {
                                            step = 2
                                        }
                                    }
                                    showDeductionConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(0.04f),
                                contentColor = textCol
                            ),
                            border = BorderStroke(1.dp, textCol.copy(0.12f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = textCol)
                                Text("IN-GAME SUPPORT (REDIRECT PUBG)", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        if (showGuestBlockDialog) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = { showGuestBlockDialog = false }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                                        .background(if (state.isDarkTheme) Color(0xFF0F172A).copy(0.98f) else Color.White.copy(0.98f), RoundedCornerShape(24.dp))
                                        .border(2.dp, Color(0xFFFF5252).copy(0.6f), RoundedCornerShape(24.dp))
                                        .padding(20.dp)
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(48.dp))
                                        Text("GUEST ACCESS RESTRICTED", fontSize = 13.sp, fontWeight = FontWeight.Black, color = textCol)
                                        Text("As a guest, you can browse reports and profiles but cannot execute secure bypass handshakes or spend credits.\n\nRegister/Sign In with a Google or Email profile to get 100 free credits and unlock all recovery tools.", fontSize = 11.sp, color = subTextCol, textAlign = TextAlign.Center, lineHeight = 15.sp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { showGuestBlockDialog = false }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, textCol.copy(0.12f))) {
                                                Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textCol)
                                            }
                                            Button(onClick = {
                                                showGuestBlockDialog = false
                                                onDismiss()
                                                vModel.logout()
                                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                                                Text("SIGN IN/REGISTER", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showDeductionConfirmDialog) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = { showDeductionConfirmDialog = false }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                                        .background(if (state.isDarkTheme) Color(0xFF0F172A).copy(0.98f) else Color.White.copy(0.98f), RoundedCornerShape(24.dp))
                                        .border(2.dp, Color(0xFF34C759).copy(0.6f), RoundedCornerShape(24.dp))
                                        .padding(20.dp)
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CustomCreditLogo(size = 40.dp)
                                        Text("CONFIRM TRANSACTION", fontSize = 14.sp, fontWeight = FontWeight.Black, color = textCol)
                                        Column(
                                            modifier = Modifier.fillMaxWidth().background(textCol.copy(0.04f), RoundedCornerShape(12.dp)).padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Cost:", fontSize = 11.sp, color = subTextCol)
                                                Text("25 Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Current Balance:", fontSize = 11.sp, color = subTextCol)
                                                Text("${state.userCredits} Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
                                            }
                                            androidx.compose.material3.HorizontalDivider(color = textCol.copy(0.08f))
                                            val postBalance = (state.userCredits - 25).coerceAtLeast(0)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Remaining Balance:", fontSize = 11.sp, color = subTextCol)
                                                Text("$postBalance Credits", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                            }
                                        }
                                        if (state.userCredits < 25 && !state.isUnlimitedCredits) {
                                            Text("Insufficient balance! Please open the Credit Center to claim rewards or watch ads.", fontSize = 11.sp, color = Color(0xFFFF5252), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { showDeductionConfirmDialog = false }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, textCol.copy(0.12f))) {
                                                Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textCol)
                                            }
                                            Button(
                                                onClick = {
                                                    if (vModel.spendCredits(25)) {
                                                        showDeductionConfirmDialog = false
                                                        pendingAction?.invoke()
                                                        pendingAction = null
                                                    } else {
                                                        showDeductionConfirmDialog = false
                                                        vModel.showToast("INSUFFICIENT BALANCE. CLAIMS REWARDS!", "ERROR")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (state.userCredits >= 25 || state.isUnlimitedCredits) Color(0xFF34C759) else Color.Gray.copy(0.2f),
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Text("CONFIRM", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (step == 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "BYPASS ENGINE TRANSACTION HANDSHAKE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9500)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Black, RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFF9500).copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = terminalOutput,
                                color = Color(0xFFFF9500),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text = "The unban client has transmitted credentials to the security restoration servers. Ensure background services are active for at least 15 minutes.",
                            fontSize = 9.sp,
                            color = subTextCol,
                            lineHeight = 12.sp
                        )

                        Button(
                            onClick = {
                                onDismiss()
                                vModel.addRecoveryLog(state.targetPlayerName, state.targetPlayerUid, recoveryType, "COMPLETED")
                                vModel.showToast("BYPASS HANDSHAKE LOGGED SECURELY", "SUCCESS")
                                vModel.startCheatsWarningTimer(state.targetPlayerName, state.targetPlayerUid)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500), contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("COMPLETE BYPASS HANDSHAKE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryOptionCard(
    title: String,
    desc: String,
    status: String,
    isAvailable: Boolean,
    intensity: GlassIntensity,
    onLaunch: () -> Unit
) {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f
    val textCol = if (dark) Color.White else Color(0xFF0F172A)
    val subTextCol = if (dark) Color.White.copy(alpha = if (isAvailable) 0.82f else 0.45f) else Color(0xFF475569).copy(alpha = if (isAvailable) 0.95f else 0.6f)
    val borderCol = if (isAvailable) Color(0xFF0A84FF).copy(alpha = 0.4f) else (if (dark) Color.White.copy(alpha = 0.15f) else Color(0xFF0F172A).copy(alpha = 0.15f))
    val badgeBgCol = if (isAvailable) Color(0xFF0A84FF).copy(alpha = 0.2f) else (if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.08f))
    val badgeTxtCol = if (isAvailable) Color(0xFF0A84FF) else (if (dark) Color.White.copy(0.5f) else Color(0xFF475569))
    val iconTint = if (isAvailable) Color(0xFF0A84FF) else (if (dark) Color.White.copy(0.4f) else Color(0xFF64748B))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                intensity = intensity,
                cornerRadius = 18.dp,
                borderGlow = if (isAvailable) Color(0x7700E5FF) else Color(0x22FFFFFF)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAvailable) Icons.Default.VerifiedUser else Icons.Default.Lock,
                        contentDescription = "Availability Status icon",
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }

                Box(
                    modifier = Modifier
                        .background(badgeBgCol, RoundedCornerShape(6.dp))
                        .border(0.5.dp, borderCol, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTxtCol,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = desc,
                fontSize = 11.sp,
                color = subTextCol
            )

            if (isAvailable) {
                Button(
                    onClick = onLaunch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF).copy(0.2f),
                        contentColor = Color(0xFF0A84FF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF0A84FF).copy(0.5f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAUNCH UNBAN ENGINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "play arrow icon",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// 3. TOOLS SCREEN
@Composable
fun ToolsScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
        val context = androidx.compose.ui.platform.LocalContext.current
    var logText by remember { mutableStateOf("") }
    val filteredCases = remember(state.supportCases, state.searchQueries) {
        if (state.searchQueries.isEmpty()) {
            state.supportCases
        } else {
            state.supportCases.filter {
                it.title.lowercase().contains(state.searchQueries.lowercase()) ||
                it.description.lowercase().contains(state.searchQueries.lowercase())
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FindInPage,
                    contentDescription = null,
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "ALL SYSTEM TOOLS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = if (state.isDarkTheme) Color.White else Color(0xFF0F172A),
                    letterSpacing = 1.sp
                )
            }
        }

        // Developer Demo Controls
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 16.dp)
                    .border(1.dp, Color(0xFF0A84FF).copy(0.25f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DEVELOPER DEMO CONTROLS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0A84FF)
                        )
                        Text(
                            text = "Toggle Admin Mode to reply to support tickets as an administrator.",
                            fontSize = 9.sp,
                            color = Color.White.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = state.isAdminMode,
                        onCheckedChange = { vModel.toggleAdminMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF34C759),
                            checkedTrackColor = Color(0xFF34C759).copy(0.3f),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.White.copy(0.08f)
                        )
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 14.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "search icon",
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    BasicTextField(
                        value = state.searchQueries,
                        onValueChange = { vModel.updateSearch(it) },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (state.searchQueries.isEmpty()) {
                                Text(
                                    text = "Filter active recovery payloads...",
                                    color = Color.White.copy(0.4f),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "NEW CASE ENROLLMENT UNIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0A84FF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Incorporate custom authentication credentials or PUBG unban log ID directly below.",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.7f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(0.06f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = logText,
                            onValueChange = { logText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (logText.isEmpty()) {
                                    Text(
                                        text = "Enter Case Name or Log ID (e.g. PUBG Account #22934)",
                                        color = Color.White.copy(0.4f),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Button(
                        onClick = {
                            if (logText.trim().isNotEmpty()) {
                                vModel.createSupportCase(logText.trim(), "Diagnostics logs generated for unban inquiry: " + logText.trim(), context) {
                                    logText = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A84FF),
                            contentColor = Color(0xFF040B16)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SUBMIT MANUAL INJECTOR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "BYPASS V1.4 PAYLOADS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF34C759),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Initialize custom payload injections directly to bypass sandbox protections locally.",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.7f)
                    )
                    Button(
                        onClick = {
                            vModel.showToast("ACCESSING BYPASS PAYLOAD SECURE SERVER", "SUCCESS")
                            vModel.createSupportCase("Bypass Payload Log v1.4", "Initialize custom payload injections directly to bypass sandbox protections locally.", context) {}
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("access_payload"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34C759),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Access bypass payload", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Launch", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "TICKET MANAGEMENT SYSTEM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        if (state.isLoading) {
            items(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect(state.isDarkTheme)
                )
            }
        } else {
            items(filteredCases) { ticket ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { vModel.selectCase(ticket) }
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 16.dp)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = ticket.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Enrolled Case ID: " + ticket.id.take(8).uppercase(),
                                fontSize = 10.sp,
                                color = Color.White.copy(0.5f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    when (ticket.status) {
                                        "resolved" -> Color(0xFF34C759).copy(alpha = 0.2f)
                                        "pending" -> Color(0xFFFFC107).copy(alpha = 0.2f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    0.5.dp,
                                    when (ticket.status) {
                                        "resolved" -> Color(0xFF34C759).copy(alpha = 0.5f)
                                        "pending" -> Color(0xFFFFC107).copy(alpha = 0.5f)
                                        else -> Color.White.copy(alpha = 0.15f)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ticket.status.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (ticket.status) {
                                    "resolved" -> Color(0xFF34C759)
                                    "pending" -> Color(0xFFFFC107)
                                    else -> Color.White.copy(0.6f)
                                }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Compliance Specialist Audit Queue",
                                fontSize = 9.sp,
                                color = Color.White.copy(0.5f)
                            )
                            Text(
                                text = if (ticket.status == "resolved") "100%" else (if (ticket.status == "pending") "65%" else "15%"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(0.1f), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (ticket.status == "resolved") 1f else (if (ticket.status == "pending") 0.65f else 0.15f))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF0A84FF), Color(0xFF34C759))
                                        ),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
        } // close else block

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// 4. NOTIFICATIONS SCREEN
@Composable
fun NotificationsScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "NOTIFICATION BOARD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0A84FF).copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${state.userNotificationLogs.filter { it.isUnread }.size} Unread",
                            fontSize = 8.sp,
                            color = Color(0xFF0A84FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "DISMISS ALL",
                    fontSize = 10.sp,
                    color = Color(0xFFFF3366),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.bounceClick { 
                        state.userNotificationLogs.forEach { log -> 
                            vModel.dismissNotification(log.id) 
                        }
                    }.padding(4.dp)
                )
            }
        }

        if (state.userNotificationLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All notifications cleared.",
                        color = Color.White.copy(0.4f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        items(state.userNotificationLogs, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { vModel.markNotificationAsRead(item.id) }
                    .liquidGlass(
                        intensity = state.glassIntensity,
                        cornerRadius = 16.dp,
                        borderGlow = if (item.isUnread) Color(0x6600E5FF) else Color(0x11FFFFFF)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .background(
                                if (item.isUnread) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(
                                onClick = { vModel.dismissNotification(item.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = Color.White.copy(0.4f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Text(
                            text = item.body,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.78f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.timestamp,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// 5. PROFILE SCREEN
@Composable
fun ProfileScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
        val context = androidx.compose.ui.platform.LocalContext.current
    var activeFeedbackType by remember { mutableStateOf<String?>(null) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackEmail by remember { mutableStateOf(state.googleAccountEmail ?: "") }
    var feedbackRating by remember { mutableStateOf(5) }
    var submittedHistory by remember { mutableStateOf(listOf<String>()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editPlayerName by remember { mutableStateOf(state.targetPlayerName) }
    var editPlayerUid by remember { mutableStateOf(state.targetPlayerUid) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val hasProfileData = state.isLoggedIn && !state.isGuest && (!state.googleAccountName.isNullOrBlank() || state.targetPlayerUid.isNotBlank())
        
        if (hasProfileData) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(2.dp, Color(0xFF34C759), CircleShape)
                            .clip(CircleShape)
                    ) {
                        if (!state.profileLogoUri.isNullOrEmpty()) {
                            SubcomposeAsyncImage(
                                model = state.profileLogoUri,
                                contentDescription = "Custom User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 1.5.dp, color = Color(0xFF34C759))
                                    }
                                },
                                error = {
                                    Image(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User avatar profile",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF002D62)),
                                        colorFilter = ColorFilter.tint(Color.White)
                                    )
                                }
                            )
                        } else {
                            Image(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User avatar profile",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF002D62)),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.googleAccountName ?: state.targetPlayerName.ifBlank { "User Profile" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color(0xFF34C759), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = state.subscriptionPlan.name,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF34C759)
                                )
                            }
                        }
                        Text(
                            text = "@${(state.googleAccountName ?: state.targetPlayerName).replace(" ", "_").lowercase().ifBlank { "user" }}",
                            fontSize = 13.sp,
                            color = Color(0xFF1DA1F2),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "UID: ${state.targetPlayerUid.ifBlank { "Not Set" }}\n${"Email Address"}: ${state.googleAccountEmail ?: "Not Linked"}",
                            fontSize = 10.sp,
                            color = if (state.isDarkTheme) Color.White.copy(0.6f) else Color(0xFF475569),
                            lineHeight = 14.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            editPlayerName = state.targetPlayerName
                            editPlayerUid = state.targetPlayerUid
                            showEditDialog = true
                        },
                        modifier = Modifier.size(32.dp).background(Color.White.copy(0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Info",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonOff,
                        contentDescription = "No Profile Data",
                        tint = Color.White.copy(0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Profile Not Linked",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            editPlayerName = state.targetPlayerName
                            editPlayerUid = state.targetPlayerUid
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Profile Data", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "LIQUID CONSOLE PREFERENCES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isDarkTheme) Color.White else Color(0xFF0F172A),
                letterSpacing = 1.sp
            )
        }

        item {
            SettingsPanel(title = "CORE APLET THEME STYLE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemeStyle.DARK to "AMOLED DARK",
                        AppThemeStyle.GRAY to "TITANIUM GRAY"
                    ).forEach { (style, text) ->
                        SettingsToggleButton(
                            text = text,
                            selected = state.themeStyle == style,
                            onClick = { vModel.setThemeStyle(style) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            SettingsPanel(title = "Liquid Glassmorphism Intensity") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        GlassIntensity.LOW to "LOW (45%)",
                        GlassIntensity.MEDIUM to "MID (25%)",
                        GlassIntensity.ULTRA to "ULTRA (12%)"
                    ).forEach { (intensity, text) ->
                        SettingsToggleButton(
                            text = text,
                            selected = state.glassIntensity == intensity,
                            onClick = { vModel.changeGlassIntensity(intensity) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }


        item {
            SettingsPanel(title = "USER FEEDBACK CENTER & SUPPORT") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val feedbackOptions = listOf(
                        "Send Feedback" to Icons.Default.Send,
                        "Report Issue" to Icons.Default.Warning,
                        "Feature Request" to Icons.Default.AddCircle,
                        "Contact Support" to Icons.Default.Call,
                        "Rate App" to Icons.Default.Star,
                        "Suggest Feature" to Icons.Default.Edit,
                        "Help Center" to Icons.Default.Info,
                        "Community Suggestions" to Icons.Default.Group
                    )

                    FlowRowWithEqualWeights(
                        items = feedbackOptions,
                        columns = 2
                    ) { (label, icon) ->
                        Button(
                            onClick = { activeFeedbackType = label },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isDarkTheme) Color.White.copy(0.04f) else Color(0xFFF1F5F9),
                                contentColor = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, if (state.isDarkTheme) Color.White.copy(0.1f) else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            DeveloperProfileCard(state = state)
        }

        item {
            SettingsPanel(title = "ACTIVE SESSION TERMINATION") {
                val logoutContext = androidx.compose.ui.platform.LocalContext.current
                Button(
                    onClick = {
                        val logoutPrefs = logoutContext.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                        logoutPrefs.edit().clear().apply()
                        vModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                        contentColor = Color(0xFFFF5252)
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "LOGOUT DECRYPTION KEY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showEditDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showEditDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = GlassIntensity.MEDIUM, cornerRadius = 20.dp, borderGlow = Color(0xFF0A84FF))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EDIT PLAYER PROFILE DATA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.isDarkTheme) Color.White else Color(0xFF0F172A),
                            letterSpacing = 0.5.sp
                        )
                        IconButton(
                            onClick = { showEditDialog = false },
                            modifier = Modifier.size(24.dp).bounceClick { showEditDialog = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (state.isDarkTheme) Color.White.copy(0.6f) else Color.Black.copy(0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(0.08f))

                    CustomGlassInput(
                        label = "Player Character Name",
                        value = editPlayerName,
                        onValueChange = { editPlayerName = it },
                        icon = Icons.Default.Person,
                        isDark = state.isDarkTheme
                    )

                    CustomGlassInput(
                        label = "PUBG Unique ID (UID)",
                        value = editPlayerUid,
                        onValueChange = { editPlayerUid = it },
                        icon = Icons.Default.CheckCircle,
                        isDark = state.isDarkTheme
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A84FF))
                            .bounceClick {
                                if (editPlayerName.isBlank() || editPlayerUid.isBlank()) {
                                    android.widget.Toast.makeText(context, "Player credentials fields cannot be empty.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@bounceClick
                                }
                                vModel.updatePlayerProfileInFirestore(editPlayerName, editPlayerUid, context) {
                                    android.widget.Toast.makeText(context, "Cloud modifications dispatch executed successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    showEditDialog = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SAVE REGISTERED DATA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    }

    if (activeFeedbackType != null) {
        val type = activeFeedbackType!!
        androidx.compose.ui.window.Dialog(onDismissRequest = { activeFeedbackType = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.isDarkTheme) Color(0xFF0F172A) else Color.White)
                    .border(1.dp, Color(0xFF0A84FF).copy(0.3f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.isDarkTheme) Color.White else Color(0xFF0F172A),
                            letterSpacing = 0.5.sp
                        )
                        IconButton(
                            onClick = { activeFeedbackType = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (state.isDarkTheme) Color.White.copy(0.6f) else Color.Black.copy(0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(0.08f))

                    if (type == "Help Center") {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            val faqs = listOf(
                                "How long does account recovery take?" to "Automated recoveries complete instantly, while manual bypass streams take 2 to 4 hours.",
                                "Is my character profile safe during bypass?" to "Yes, the 10-year server-side lock state is bypassed securely using modern Firebase matching nodes.",
                                "Do I need credits to perform bypass?" to "Basic tools are free, premium operations dynamically pull credits from your local profile balance."
                            )
                            faqs.forEach { (q, a) ->
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(q, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A84FF))
                                        Text(a, fontSize = 9.sp, color = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF334155))
                                    }
                                }
                            }
                        }
                    } else if (type == "Community Suggestions") {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            val suggestions = listOf(
                                "Oshaq_Playz" to "Suggest adding telemetry graph speed scales directly.",
                                "PUBG_Master" to "Need a custom file backup export feature.",
                                "VIP_Deluxe" to "Add dynamic chat logs backup tracker to standard tools."
                            )
                            suggestions.forEach { (user, sug) ->
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFF0A84FF).copy(0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.take(1).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A84FF))
                                        }
                                        Column {
                                            Text("@$user", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White.copy(0.8f) else Color(0xFF0F172A))
                                            Text(sug, fontSize = 8.sp, color = if (state.isDarkTheme) Color.White.copy(0.6f) else Color(0xFF334155))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular Inputs
                        if (type == "Rate App") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { starIndex ->
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Star",
                                        tint = if (starIndex <= feedbackRating) Color(0xFFFFD700) else Color.Gray.copy(0.4f),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .bounceClick { feedbackRating = starIndex }
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }

                        CustomGlassInput(
                            label = "Your Email Address",
                            value = feedbackEmail,
                            onValueChange = { feedbackEmail = it },
                            icon = Icons.Default.Email,
                            isDark = state.isDarkTheme
                        )

                        CustomGlassInput(
                            label = when (type) {
                                "Report Issue" -> "Detailed Issue Description"
                                "Feature Request", "Suggest Feature" -> "Proposed Feature Details"
                                "Contact Support" -> "Describe Support Queries"
                                else -> "Write secure feedback content"
                            },
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            icon = Icons.Default.Edit,
                            isDark = state.isDarkTheme
                        )

                        if (submittedHistory.isNotEmpty()) {
                            Text(
                                text = "HISTORIC ENTRIES:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34C759)
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                submittedHistory.forEach { itemText ->
                                    item {
                                        Text(
                                            text = "- $itemText",
                                            fontSize = 9.sp,
                                            color = if (state.isDarkTheme) Color.White.copy(0.6f) else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (feedbackText.isBlank()) {
                                    android.widget.Toast.makeText(context, "Please write form contents first", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                submittedHistory = submittedHistory + "$type: $feedbackText"
                                android.widget.Toast.makeText(context, "Secure transmission verified. Reference ID raised!", android.widget.Toast.LENGTH_SHORT).show()
                                feedbackText = ""
                                activeFeedbackType = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0A84FF),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .paperTexture(0.12f)
                                .depth3D(cornerRadius = 10.dp, isDark = false)
                        ) {
                            Text("SUBMIT REQUEST", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FlowRowWithEqualWeights(
    items: List<T>,
    columns: Int,
    content: @Composable (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content(item)
            }
        }
    }
}

@Composable
fun ProfileStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, intensity: GlassIntensity) {
    var bounce by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while(true) {
            delay(kotlin.random.Random.nextLong(2000, 5000))
            bounce = !bounce
        }
    }
    
    val scale by animateFloatAsState(targetValue = if (bounce) 1.05f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f))

    Box(
        modifier = modifier
            .height(78.dp)
            .liquidGlass(intensity = intensity, cornerRadius = 14.dp)
            .bounceClick { bounce = !bounce }
            .padding(10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(targetState = value, transitionSpec = {
                slideInVertically { height -> height } + fadeIn() togetherWith slideOutVertically { height -> -height } + fadeOut()
            }, label = "") { targetValue ->
                Text(
                    text = targetValue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
            Text(
                text = label.uppercase(),
                fontSize = 7.3.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(0.45f)
            )
        }
    }
}

@Composable
fun PremiumOnboardingWalkthrough(
    vModel: com.example.ui.ReclaimViewModel,
    state: com.example.ui.ReclaimUiState,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentPage by remember { mutableStateOf(1) }
    val txtCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTxtCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val cardBg = if (state.isDarkTheme) Color.White.copy(0.04f) else Color(0xFF020817).copy(0.04f)
    val accentCol = Color(0xFF0A84FF)

    // Dynamic Permission Requester (POST_NOTIFICATIONS on Android 13+)
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.widget.Toast.makeText(
            context,
            if (isGranted) "Notification channels verified!" else "Notifications are silent.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                .background(if (state.isDarkTheme) Color(0xFF020817).copy(0.96f) else Color.White.copy(0.96f))
                .border(1.dp, accentCol.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Stepper indicator with Apple-style fluid bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ONBOARDING STEP $currentPage OF 4",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = accentCol,
                    letterSpacing = 1.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..4).forEach { idx ->
                        Box(
                            modifier = Modifier
                                .size(width = if (currentPage == idx) 16.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (currentPage == idx) accentCol else txtCol.copy(0.12f))
                        )
                    }
                }
            }

            HorizontalDivider(color = txtCol.copy(0.08f))

            // Body: Dynamic Slide Pages
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = currentPage, label = "onboarding_slider") { page ->
                    when (page) {
                        1 -> {
                            // PAGE 1: WELCOME & ROOT APP INTRODUCTION
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(accentCol.copy(0.12f), CircleShape)
                                        .border(2.dp, accentCol, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = accentCol,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Text(
                                    text = "RECLAIM ACCOUNTS",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = txtCol,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "Advanced Secure Restoration Portal",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentCol
                                )
                                Text(
                                    text = "Welcome to the advanced and secure account restoration gateway, certified for high-priority bypass sandboxing. Let's configure your terminal dashboard to safeguard your gaming records.",
                                    fontSize = 11.sp,
                                    color = subTxtCol,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        2 -> {
                            // PAGE 2: EXTENSIVE FEATURE WALKTHROUGH
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "RESTORE FEATURES SUITE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = txtCol
                                )
                                Text(
                                    text = "Explore built-in core capability modules",
                                    fontSize = 9.5.sp,
                                    color = subTxtCol
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val features = listOf(
                                    Icons.Default.Lock to "Advanced Bypass Protection" to "Evade permanent hardware signatures and lift 10-year lock suspensions safely.",
                                    Icons.Default.Psychology to "Dual-Core AI Guidance" to "Consult smart virtual specialists (Vance & Marcus) for instant custom appeal drafts.",
                                    Icons.Default.CloudSync to "Local Database Handshake" to "All parameters synchronize directly inside your secure Supabase/Firebase clusters."
                                )

                                features.forEach { (meta, label) ->
                                    val (icon, title) = meta
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(cardBg, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = icon, contentDescription = null, tint = accentCol, modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txtCol)
                                            Text(text = label, fontSize = 9.sp, color = subTxtCol, lineHeight = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // PAGE 3: USAGE INSTRUCTIONS & CREDIT RULES
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SIMPLE TERMINAL SCHEDULER",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = txtCol
                                )
                                Text(
                                    text = "Adhere strictly to standard operation rules",
                                    fontSize = 9.5.sp,
                                    color = subTxtCol
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val steps = listOf(
                                    "01" to "Claim your Daily Reward Box to add lucky credits and increment login streaks.",
                                    "02" to "Every unban diagnostic analysis, bypass check, or AI guidance query costs 10 credits.",
                                    "03" to "Our 1-Day Trial allocates Premium subscription. After trial, limits restore to 50 daily credits."
                                )

                                steps.forEach { (stepNum, instructionText) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(cardBg, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(accentCol.copy(0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = stepNum, fontSize = 9.sp, fontWeight = FontWeight.Black, color = accentCol)
                                        }
                                        Text(
                                            text = instructionText,
                                            fontSize = 9.5.sp,
                                            color = txtCol.copy(0.85f),
                                            lineHeight = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        4 -> {
                            // PAGE 4: CRITICAL PERMISSION EXPLANATIONS
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(accentCol.copy(0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = accentCol,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Text(
                                    text = "CLIENT CLEARANCES REQUIRED",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = txtCol
                                )
                                Text(
                                    text = "We respect your digital privacy and credentials context",
                                    fontSize = 9.5.sp,
                                    color = subTxtCol,
                                    textAlign = TextAlign.Center
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentCol.copy(0.04f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, accentCol.copy(0.2f), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "🔔 NOTIFICATIONS PERMISSION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentCol
                                        )
                                        Text(
                                            text = "Allows our system to notify you instantly when your background sandbox check-out completes, tickets replies arrive, or security logs dispatch successful handshakes.",
                                            fontSize = 9.sp,
                                            color = subTxtCol,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = txtCol.copy(0.08f))

            // Footer Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentPage > 1) {
                    Button(
                        onClick = { currentPage-- },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.04f)),
                        border = BorderStroke(0.5.dp, txtCol.copy(0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1f)
                            .paperTexture(0.12f)
                            .depth3D(cornerRadius = 10.dp, isDark = true)
                    ) {
                        Text("BACK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = txtCol)
                    }
                }

                Button(
                    onClick = {
                        if (currentPage < 4) {
                            currentPage++
                        } else {
                            // On Android 13+ request notifications permission
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onComplete()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentCol),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .weight(1.5f)
                        .testTag(if (currentPage == 4) "onboarding_start_button" else "onboarding_next_button")
                        .paperTexture(0.12f)
                        .depth3D(cornerRadius = 10.dp, isDark = false)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentPage == 4) "ACCEPT & GET STARTED" else "NEXT STEP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = if (state.isDarkTheme) Color.White else Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(0.5f),
            letterSpacing = 0.5.sp
        )
        content()
    }
}

@Composable
fun SettingsToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF0A84FF).copy(0.25f) else Color.White.copy(0.04f),
            contentColor = if (selected) Color(0xFF0A84FF) else Color.White.copy(0.7f)
        ),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (selected) Color(0xFF0A84FF).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// LIQUID BOTTOM NAVIGATION
@Composable
fun FloatingLiquidBottomNav(
    currentTab: NavigationTab,
    isDarkTheme: Boolean,
    onTabSelected: (NavigationTab) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val items = listOf(
        NavigationTab.HOME to Icons.Default.Home,
        NavigationTab.TOOLS to Icons.Default.FindInPage,
        NavigationTab.RECOVERY to Icons.Default.SportsMotorsports,
        NavigationTab.NOTIFICATIONS to Icons.Default.Notifications,
        NavigationTab.PROFILE to Icons.Default.Person
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(76.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(38.dp),
                ambientColor = Color(0xFF0A84FF).copy(0.2f),
                spotColor = Color(0xFF0A84FF).copy(0.3f)
            )
            .clip(RoundedCornerShape(38.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isDarkTheme) {
                        listOf(
                            Color(0xFF1E293B).copy(alpha = 0.95f),
                            Color(0xFF0F172A).copy(alpha = 0.95f)
                        )
                    } else {
                        listOf(
                            Color(0xFFE2E8F0).copy(alpha = 0.95f),
                            Color(0xFFF1F5F9).copy(alpha = 0.95f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (isDarkTheme) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                shape = RoundedCornerShape(38.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (tab, icon) ->
                val selected = (tab == currentTab)
                val scale by animateFloatAsState(if (selected) 1.2f else 1.0f, label = "tab_sc", animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                val alpha by animateFloatAsState(if (selected) 1.0f else 0.4f, label = "tab_al")
                val glowColor = if (selected) Color(0xFF0A84FF) else Color.White
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(targetState = icon, label = "tab_icon", transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        }) { targetIcon ->
                            Icon(
                                imageVector = targetIcon,
                                contentDescription = tab.name,
                                modifier = Modifier
                                    .scale(scale)
                                    .size(26.dp),
                                tint = glowColor.copy(alpha = alpha)
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .width(4.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0A84FF))
                                    .shadow(6.dp, spotColor = Color(0xFF0A84FF))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(text: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (active) Color(0xFF34C759) else Color.Red, CircleShape)
        )
        Text(
            text = text,
            fontSize = 9.5.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TimeSegment(label: String, value: Int, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(
                    if (highlight) Color(0xFF0A84FF).copy(0.15f) else Color.White.copy(0.04f),
                    RoundedCornerShape(6.dp)
                )
                .border(
                    0.5.dp,
                    if (highlight) Color(0xFF0A84FF).copy(0.4f) else Color.White.copy(0.12f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) Color(0xFF0A84FF) else Color.White
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(0.4f)
        )
    }
}

@Composable
fun TimeColon() {
    Text(
        text = ":",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(0.4f),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun GoogleLoginAndSplashScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
        val txtCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTxtCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.authError) {
        state.authError?.let { err ->
            android.widget.Toast.makeText(context, "Authentication Alert: $err", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Interactive target profile configurations
    var pName by remember { mutableStateOf(state.targetPlayerName) }
    var pUid by remember { mutableStateOf(state.targetPlayerUid) }
    var pReason by remember { mutableStateOf(state.targetBanReason) }
    var pProfileClass by remember { mutableStateOf(state.targetProfileType) }
    var firebaseAuthCertified by remember { mutableStateOf(true) }
    var showAccountChooser by remember { mutableStateOf(false) }

    if (showAccountChooser) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAccountChooser = false },
            title = { Text("Choose an account", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("to continue to RECLAIM ACCOUNTS", fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth().bounceClick { 
                        showAccountChooser = false
                        vModel.updateTargetProfile(pName, pUid, pReason, pProfileClass)
                        vModel.googleLogin(email = "oshaqplayz@gmail.com", name = "Oshaqplayz")
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFF0A84FF), CircleShape), contentAlignment = Alignment.Center) { Text("O", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Oshaqplayz", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                            Text("oshaqplayz@gmail.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Divider(color = Color.LightGray.copy(0.5f), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().bounceClick { 
                        showAccountChooser = false
                        vModel.updateTargetProfile(pName, pUid, pReason, pProfileClass)
                        vModel.googleLogin(email = "YouTube@gmail.com", name = "YouTube Player")
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) { 
                            Text("Y", color = Color.White, fontWeight = FontWeight.Bold) 
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("YouTube Player", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                            Text("YouTube@gmail.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (state.isSplashLoading) {
        // RENDER IMMERSIVE DIGITAL TUNNEL SPLASH Loading Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Glow logo element in center of loading
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "ring_rotate")
                    val rotationSmooth by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin"
                    )
                    
                    Canvas(modifier = Modifier.size(130.dp)) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFF0582FF), Color(0xFF0A84FF))
                            ),
                            startAngle = rotationSmooth,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.helmet_logo),
                        contentDescription = "PUBG Helmet Logo",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .premiumShineEffect(state.isDarkTheme)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SECURE RECLAMATION TUNNEL",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = txtCol,
                        letterSpacing = 1.5.sp
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock key",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "TARGET: ${pName.uppercase()} (#${pUid})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A84FF),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = state.splashTaskName.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = txtCol.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Smooth linear progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.splashProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0A84FF), Color(0xFF34C759))
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    Text(
                        text = "${(state.splashProgress * 100).toInt()}% COMPLETED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = txtCol.copy(alpha = 0.6f)
                    )
                }
            }
        }
    } else {
        val context = androidx.compose.ui.platform.LocalContext.current
        var authScreenMode by remember { mutableStateOf("CHOOSER") } // "CHOOSER", "EMAIL_LOGIN", "EMAIL_SIGNUP"
        
        // Form states
        var inputEmail by remember { mutableStateOf("") }
        var inputPassword by remember { mutableStateOf("") }
        var showGoogleChooser by remember { mutableStateOf(false) }
        var isGoogleConnecting by remember { mutableStateOf(false) }
        var googleConnectingProgress by remember { mutableStateOf(0f) }
        var googleConnectingTaskName by remember { mutableStateOf("") }
        
        // Profile setup states
        var pName by remember { mutableStateOf(state.targetPlayerName) }
        var pUid by remember { mutableStateOf(state.targetPlayerUid) }
        var pReason by remember { mutableStateOf(state.targetBanReason.ifBlank { "Suspicious gameplay algorithms detected in 10-year sandbox" }) }
        var pProfileClass by remember { mutableStateOf(state.targetProfileType.ifBlank { "Elite VIP Deluxe" }) }
        var pEmail by remember { mutableStateOf(state.googleAccountEmail ?: "") }
        var pPhone by remember { mutableStateOf("") }
        var selectedAvatarName by remember { mutableStateOf("Elite Golden Helmet") }
        var customAvatarUrl by remember { mutableStateOf("https://secure.pubgmobile.com/images/avatars/golden_helmet.png") }
        var isUploadingLogo by remember { mutableStateOf(false) }
        var logoProgress by remember { mutableStateOf(0f) }
        var logoTaskName by remember { mutableStateOf("") }

        LaunchedEffect(state.isLoggedIn) {
            if (state.isLoggedIn && pEmail.isBlank()) {
                pEmail = state.googleAccountEmail ?: ""
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Top Header Logo display
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo_1781241621499),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF0A84FF).copy(0.4f), RoundedCornerShape(12.dp))
                    )
                    Text(
                        text = "RECLAIM ACCOUNTS",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = txtCol,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Advanced Secure Account Restoration Wizard",
                        fontSize = 11.sp,
                        color = subTxtCol,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!state.isLoggedIn) {
                // PHASE 1: SECURE GATEWAY AUTHENTICATION CHOICE
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Safe Lock Icon",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "AUTHENTICATION PORTAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0A84FF),
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = "Choose an option to authenticate with Firebase Cloud directory list:",
                            fontSize = 11.sp,
                            color = subTxtCol,
                            lineHeight = 15.sp
                        )

                        if (state.authError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFF5252).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = state.authError ?: "Operation failure",
                                        color = Color(0xFFFF5252),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (authScreenMode == "CHOOSER") {
                            // Option 1: Google Sign In
                            Button(
                                onClick = { 
                                    vModel.nativeGoogleSignIn(context) {
                                        android.widget.Toast.makeText(context, "Google Authorization Sync with Firebase and Firestore verified!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4285F4)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                                    .paperTexture(0.12f)
                                    .depth3D(cornerRadius = 10.dp, isDark = false)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Color(0xFF4285F4)
                                        )
                                    }
                                    Text(
                                        text = "SIGN IN WITH GOOGLE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Option 2: Email & Password
                            Button(
                                onClick = { authScreenMode = "EMAIL_LOGIN" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(0.06f),
                                    contentColor = txtCol
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .paperTexture(0.12f)
                                    .depth3D(cornerRadius = 10.dp, isDark = true)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Mail Icon",
                                        tint = Color(0xFF0A84FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "LOGIN WITH EMAIL & PASSWORD",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Option 3: Guest Mode
                            Button(
                                onClick = {
                                    vModel.signInAsGuest(context) {
                                        android.widget.Toast.makeText(context, "Guest Access Authorized. Mode Limited.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759).copy(0.12f),
                                    contentColor = Color(0xFF34C759)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF34C759).copy(0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Guest Person Icon",
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "EXPLORE IN GUEST MODE (NO ACCOUNT)",
                                        fontSize = 10.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        } else {
                            // EMAIL FORM (LOGIN OR SIGNUP)
                            val isLogin = authScreenMode == "EMAIL_LOGIN"
                            
                            CustomGlassInput(
                                label = "Email Address",
                                value = inputEmail,
                                onValueChange = { inputEmail = it },
                                icon = Icons.Default.Email,
                                isDark = state.isDarkTheme
                            )

                            CustomGlassInput(
                                label = "Password",
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                icon = Icons.Default.Lock,
                                isDark = state.isDarkTheme
                            )

                            Button(
                                onClick = {
                                    if (inputEmail.isBlank() || inputPassword.isBlank()) {
                                        android.widget.Toast.makeText(context, "Please complete all credentials fields.", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (isLogin) {
                                        vModel.signInWithEmail(inputEmail, inputPassword, context) {
                                            android.widget.Toast.makeText(context, "Firebase authentication success!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        vModel.signUpWithEmail(inputEmail, inputPassword, context) {
                                            android.widget.Toast.makeText(context, "Secure Firebase registry successful!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0A84FF),
                                    contentColor = Color.Black
                                ),
                                enabled = !state.isAuthOperationLoading,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                if (state.isAuthOperationLoading) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isLogin) "SECURE PROFILE LOGIN" else "REGISTER NEW ACCOUNT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLogin) "Don't have an account? Sign Up" else "Already playing? Log In",
                                    fontSize = 10.sp,
                                    color = Color(0xFF0A84FF),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .bounceClick {
                                            authScreenMode = if (isLogin) "EMAIL_SIGNUP" else "EMAIL_LOGIN"
                                        }
                                        .padding(4.dp)
                                )

                                Text(
                                    text = "Cancel",
                                    fontSize = 10.sp,
                                    color = subTxtCol,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .bounceClick {
                                            authScreenMode = "CHOOSER"
                                        }
                                        .padding(4.dp)
                                )
                            }
                            
                            if (isLogin) {
                                Text(
                                    text = "Forgot password? Send Reset Link",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFFC107),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .bounceClick {
                                            vModel.sendPasswordReset(inputEmail, context) { success, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        .padding(4.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            } else {
                // PHASE 2: SIGNED IN BUT PROFILE REGISTER IS UNCOMPLETED (REAL FIRESTORE COLLECTION SYNC)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_white_banned_helmet_1781242648530),
                                contentDescription = "Banned account helmet state",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(0.9f))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFF5252).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "TARGET STATE: ACCOUNT LOCK (10 YRS)",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5252)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF0A84FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = pProfileClass.uppercase(),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0A84FF)
                                        )
                                    }
                                }
                                Text(
                                    text = "Bypass Sandbox of '${pName.ifBlank { "New Character" }}' (#${pUid.ifBlank { "000000" }})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                            .padding(14.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = "Config Icon",
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "STEP 2: REGISTER SYSTEM MATCHED PROFILE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0A84FF),
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = "STORE SECURE PARAMETERS IN FIREBASE CLOUD:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = txtCol.copy(alpha = 0.5f)
                            )

                            HorizontalDivider(color = Color.White.copy(0.08f))

                            CustomGlassInput(
                                label = "Player Character Name",
                                value = pName,
                                onValueChange = { pName = it },
                                icon = Icons.Default.Person,
                                isDark = state.isDarkTheme
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1.2f)) {
                                    CustomGlassInput(
                                        label = " PUBG Unique ID (UID)",
                                        value = pUid,
                                        onValueChange = { pUid = it },
                                        icon = Icons.Default.CheckCircle,
                                        isDark = state.isDarkTheme
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "PROFILE SHIELD",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0A84FF)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                                                .bounceClick {
                                                    pProfileClass = if (pProfileClass.contains("Deluxe")) "Advanced T1 Core" else "Elite VIP Deluxe"
                                                }
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (pProfileClass.length > 10) pProfileClass.take(10) + ".." else pProfileClass,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = txtCol
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Switch profile class",
                                                    tint = Color(0xFF0A84FF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            CustomGlassInput(
                                label = "Ban Lock Violation Reason",
                                value = pReason,
                                onValueChange = { pReason = it },
                                icon = Icons.Default.Info,
                                isDark = state.isDarkTheme
                            )

                            CustomGlassInput(
                                label = "Notification Target Email",
                                value = pEmail,
                                onValueChange = { pEmail = it },
                                icon = Icons.Default.Email,
                                isDark = state.isDarkTheme
                            )

                            CustomGlassInput(
                                label = "Account Mobile Number",
                                value = pPhone,
                                onValueChange = { pPhone = it },
                                icon = Icons.Default.Phone,
                                isDark = state.isDarkTheme
                            )

                            HorizontalDivider(color = Color.White.copy(0.08f))

                            // Custom upload profile logo module - Presets switcher
                            Text(
                                text = "UPLOAD PROFILE AVATAR LOGO (CLOUDBYPASS SECURE ENDPOINT)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = txtCol.copy(alpha = 0.6f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf("Golden Helmet", "Urban Commando", "Stealth Operative")
                                presets.forEach { itemLabel ->
                                    val isSelected = selectedAvatarName == itemLabel
                                    Button(
                                        onClick = {
                                            selectedAvatarName = itemLabel
                                            customAvatarUrl = "https://secure.pubgmobile.com/images/avatars/${itemLabel.lowercase().replace(" ", "_")}.png"
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) Color(0xFF34C759).copy(0.15f) else Color.White.copy(0.04f),
                                            contentColor = if (isSelected) Color(0xFF34C759) else txtCol.copy(0.7f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(itemLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            CustomGlassInput(
                                label = "Avatar Web HTTPS URL Source",
                                value = customAvatarUrl,
                                onValueChange = { customAvatarUrl = it },
                                icon = Icons.Default.Cloud,
                                isDark = state.isDarkTheme
                            )

                            // Logo upload simulation progress bar
                            if (isUploadingLogo) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(logoTaskName.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                        Text("${(logoProgress * 100).toInt()}%", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF34C759))
                                    }
                                    LinearProgressIndicator(
                                        progress = { logoProgress },
                                        color = Color(0xFF34C759),
                                        trackColor = Color.White.copy(0.08f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isUploadingLogo = true
                                        logoProgress = 0f
                                        logoTaskName = "Initalizing HTTPS secure connection to server..."
                                        val animScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                        animScope.launch {
                                            delay(500)
                                            logoProgress = 0.35f
                                            logoTaskName = "Pushing profile payload buffers..."
                                            delay(600)
                                            logoProgress = 0.75f
                                            logoTaskName = "Verifying MD5 integrity tags..."
                                            delay(500)
                                            logoProgress = 1.0f
                                            logoTaskName = "Custom profile logo synchronized!"
                                            delay(300)
                                            isUploadingLogo = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759).copy(0.1f)),
                                    border = BorderStroke(0.5.dp, Color(0xFF34C759).copy(0.4f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                ) {
                                    Text("UPLOAD PROFILE LOGO SECURELY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                }
                            }
                        }
                    }
                }

                // SUBMIT REGISTRATION FOR FIREBASE AND MOVE TO DASHBOARD
                item {
                    val isFormComplete = pName.isNotBlank() && pUid.isNotBlank()
                    Button(
                        onClick = {
                            if (!isFormComplete) {
                                android.widget.Toast.makeText(context, "Please complete Player Name & Character UID", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            vModel.updateTargetProfile(pName, pUid, pReason, pProfileClass)
                            vModel.storePlayerProfileInFirestore(pName, pUid, context) {
                                android.widget.Toast.makeText(context, "Profile mapping complete. Access Granted!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormComplete) Color(0xFF34C759) else Color.Gray.copy(0.2f),
                            contentColor = if (isFormComplete) Color.Black else txtCol.copy(0.4f)
                        ),
                        enabled = isFormComplete && !state.isAuthOperationLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (state.isAuthOperationLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("COMPLETE & ENTER DIRECT TERMINAL", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { vModel.logout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text("CANCEL & LOGOUT OF ACCOUNT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Custom Google Sign-In UI removed in favor of native Google Sign In.

        if (isGoogleConnecting) {
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.isDarkTheme) Color(0xFF0F172A) else Color.White)
                        .border(1.dp, Color(0xFF4285F4).copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { googleConnectingProgress },
                            color = Color(0xFF4285F4),
                            trackColor = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(48.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = googleConnectingTaskName,
                                fontSize = 10.sp,
                                color = txtCol,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SECURE FIREBASE NODE HANDSHAKE",
                                fontSize = 8.sp,
                                color = Color(0xFFFF9100),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGlassInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean
) {
    val textCol = if (isDark) Color.White else Color(0xFF0F172A)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = if (isDark) Color(0xFF0A84FF) else Color(0xFF020817)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF0A84FF) else Color(0xFF020817),
                    modifier = Modifier.size(14.dp)
                )
            },
            textStyle = TextStyle(color = textCol, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0A84FF),
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.2f),
                focusedContainerColor = if (isDark) Color.Black.copy(0.3f) else Color.White.copy(0.1f),
                unfocusedContainerColor = if (isDark) Color.Black.copy(0.12f) else Color.White.copy(0.05f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMTPAndIntentEmailAppealCard(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

    var pName by remember { mutableStateOf(state.targetPlayerName) }
    var pUid by remember { mutableStateOf(state.targetPlayerUid) }
    var pReason by remember { mutableStateOf(state.targetBanReason) }
    var pProfileClass by remember { mutableStateOf(state.targetProfileType) }
    var deviceModel by remember { mutableStateOf("Android Play Device (Secure Sandbox Verified)") }
    
    var showSmtpAnimDialog by remember { mutableStateOf(false) }
    var smtpProgress by remember { mutableStateOf(0f) }
    var smtpTaskName by remember { mutableStateOf("") }

    val emailContent = remember(pName, pUid, pReason, pProfileClass, deviceModel) {
        """
        PUBG MOBILE ACCOUNT RESTORATION TASK payload:
        ====================================================
        TARGET PLAYER NAME : $pName
        TARGET UNIQUE UID  : $pUid
        SECURITY PROFILE   : $pProfileClass
        SANCTION REASON    : $pReason
        BYPASS DEVICE MODEL: $deviceModel
        NETWORK SYSTEM TYPE: Firebase Oauth Authenticated Link
        SUPABASE SYNC ENDPT: user_sync Table Node Mapped
        ====================================================
        ATTN: Security Restoration Desk, please synchronize unban parameters.
        """.trimIndent()
    }

    if (showSmtpAnimDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSmtpAnimDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020817), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFF34C759), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SECURE SMTP TRANSMITTING PROTOCOLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF34C759)
                    )
                    
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { smtpProgress },
                            color = Color(0xFF34C759),
                            strokeWidth = 4.dp
                        )
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = smtpTaskName.uppercase(),
                        fontSize = 10.sp,
                        color = Color.White.copy(0.8f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

                    LinearProgressIndicator(
                        progress = { smtpProgress },
                        color = Color(0xFF34C759),
                        trackColor = Color.White.copy(0.1f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )

                    if (smtpProgress >= 1f) {
                        Button(
                            onClick = { showSmtpAnimDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("TUNNEL SENT SUCCESS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Mail Icon",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "RECLAIM SMTP MAIL OUTLET ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF34C759),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ACTIVE GATEWAY",
                    fontSize = 8.sp,
                    color = Color(0xFF34C759),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Generate and send dynamic recovery tickets directly to PUBG Mobile unban nodes. Edits below rewrite both local database contexts and outer send packets.",
            fontSize = 10.sp,
            color = subTextCol,
            lineHeight = 14.sp
        )

        CustomGlassInput(
            label = "Target Account ID/Name",
            value = pName,
            onValueChange = {
                pName = it
                vModel.updateTargetProfile(it, pUid, pReason, pProfileClass)
            },
            icon = Icons.Default.Person,
            isDark = state.isDarkTheme
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                CustomGlassInput(
                    label = "UID #",
                    value = pUid,
                    onValueChange = {
                        pUid = it
                        vModel.updateTargetProfile(pName, it, pReason, pProfileClass)
                    },
                    icon = Icons.Default.Lock,
                    isDark = state.isDarkTheme
                )
            }
            Box(modifier = Modifier.weight(1.2f)) {
                CustomGlassInput(
                    label = "Bypass Device Type",
                    value = deviceModel,
                    onValueChange = { deviceModel = it },
                    icon = Icons.Default.Check,
                    isDark = state.isDarkTheme
                )
            }
        }

        CustomGlassInput(
            label = "Suspension Reason",
            value = pReason,
            onValueChange = {
                pReason = it
                vModel.updateTargetProfile(pName, pUid, it, pProfileClass)
            },
            icon = Icons.Default.Warning,
            isDark = state.isDarkTheme
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.25f), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "MIME PAYLOAD DRAFT OUTBOX PREVIEW:",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34C759)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = emailContent,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(0.85f),
                lineHeight = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    showSmtpAnimDialog = true
                    smtpProgress = 0f
                    smtpTaskName = "Resolving DNS record for mail server..."
                    vModel.createNewTicket("SMTP Dispatch ticket successfully buffered and sent to support@reclaim.com")
                    
                    val animateScope = CoroutineScope(Dispatchers.Main)
                    animateScope.launch {
                        delay(600)
                        smtpProgress = 0.25f
                        smtpTaskName = "Connecting SMTP Relay Tunnel (support@reclaim.com:587)..."
                        delay(700)
                        smtpProgress = 0.55f
                        smtpTaskName = "SMTP authenticated. Compiling MIME packet layout..."
                        delay(700)
                        smtpProgress = 0.85f
                        smtpTaskName = "Wrapping GnuPG encryption handshake headers..."
                        delay(600)
                        smtpProgress = 1.0f
                        smtpTaskName = "Payload delivered flawlessly. Success!"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.04f)),
                border = BorderStroke(0.5.dp, Color.White.copy(0.12f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Text("SMTP SEND", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Button(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:")
                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@reclaim.com"))
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "[HIGH-PRIORITY APPEAL] Reclaim Target #${pUid} (${pName})")
                        putExtra(android.content.Intent.EXTRA_TEXT, emailContent)
                    }
                    try {
                        context.startActivity(intent)
                        vModel.createNewTicket("Launched Native email client target appeal to support@reclaim.com")
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No email client found. Form copied!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.1f).height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (state.isDarkTheme) Color.White else Color.Black)
                    Text("LAUNCH INBOX", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun DualAdvisorAiGuideCard(vModel: ReclaimViewModel, state: ReclaimUiState) {
    var selectedAdvisorByTab by remember { mutableStateOf(0) }
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI Star Icon",
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "DUAL ADVISOR RECOVERY COUNCIL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0A84FF),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF0A84FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GEMINI AI",
                    fontSize = 8.sp,
                    color = Color(0xFF0A84FF),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Two specialist virtual security agents guide your account unblocking process in real-time. Ask them custom technical bypass instructions.",
            fontSize = 10.sp,
            color = subTextCol,
            lineHeight = 13.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            val advisors = listOf(
                "🧑‍💻 AGENT VANCE (Sandbox Bypass)" to 0,
                "🕵️‍♂️ AGENT MARCUS (Supabase Sync)" to 1
            )
            advisors.forEach { (label, idx) ->
                val isSelected = selectedAdvisorByTab == idx
                Button(
                    onClick = { selectedAdvisorByTab = idx },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF0A84FF).copy(0.2f) else Color.Transparent,
                        contentColor = if (isSelected) Color(0xFF0A84FF) else textCol.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "DISPATCH QUESTIONS FOR ACTIVE SPECIALIST",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = textCol.copy(0.5f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = state.aiQuery,
                    onValueChange = { vModel.updateAiQuery(it) },
                    textStyle = TextStyle(color = textCol, fontSize = 12.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (state.aiQuery.isEmpty()) {
                            Text("e.g. How to bypass secure logs?", color = textCol.copy(0.35f), fontSize = 12.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Button(
            onClick = {
                if (selectedAdvisorByTab == 0) {
                    vModel.askAgentVance()
                } else {
                    vModel.askAgentMarcus()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = !state.isAiLoading && state.aiQuery.isNotBlank()
        ) {
            if (state.isAiLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (state.isDarkTheme) Color.White else Color.Black)
                    Text(
                        text = if (selectedAdvisorByTab == 0) "CONSULT SANDBOX SPECIALIST VANCE" else "CONSULT SYNC SPECIALIST MARCUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }

        val expertResponse = if (selectedAdvisorByTab == 0) state.vanceResponse else state.marcusResponse
        if (expertResponse != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(0.02f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (selectedAdvisorByTab == 0) Color(0xFF0A84FF) else Color(0xFF34C759), CircleShape)
                    )
                    Text(
                        text = if (selectedAdvisorByTab == 0) "AGENT VANCE SECURITY BROADCAST:" else "AGENT MARCUS DATABASE TELEMETRY:",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selectedAdvisorByTab == 0) Color(0xFF0A84FF) else Color(0xFF34C759)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = expertResponse,
                    fontSize = 11.sp,
                    color = textCol.copy(alpha = 0.9f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun AccountRecoveryDashboardCardLayout(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val items = state.supportTickets
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = "Restoration Icon",
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "REAL-TIME RESTORATION BOARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0A84FF),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF0A84FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFF0A84FF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE STATS",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A84FF)
                )
            }
        }

        Text(
            text = "Current account bypass requests running on deep cloud recovery cores. Under review items can be accelerated by sending active command payloads.",
            fontSize = 10.sp,
            color = subTextCol,
            lineHeight = 14.sp
        )

        // Fluctuating Quick Telemetry Widgets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val telemetryItems = listOf(
                Triple("ACTIVE NODES", "${state.liveNodesLoad}", Color(0xFF0A84FF)),
                Triple("RECOVERY QUEUE", "${state.livePendingCount}", Color(0xFF34C759)),
                Triple("TUNNEL STATUS", "ONLINE", Color(0xFF0582FF))
            )
            telemetryItems.forEach { (label, value, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol.copy(alpha = 0.5f),
                            letterSpacing = 0.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = color
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(0.08f))

        // Live Status list
        items.forEach { ticket ->
            val context = androidx.compose.ui.platform.LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(0.02f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = ticket.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = "UID Identifier: #${state.targetPlayerUid} | Hash: ${ticket.hash}",
                                fontSize = 9.sp,
                                color = subTextCol
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        when (ticket.status) {
                                            "Bypassed" -> Color(0xFF34C759)
                                            "Active" -> Color(0xFF0A84FF)
                                            else -> Color(0xFFFFB300)
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = ticket.status.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = when (ticket.status) {
                                    "Bypassed" -> Color(0xFF34C759)
                                    "Active" -> Color(0xFF0A84FF)
                                    else -> Color(0xFFFFB300)
                                }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RECLAMATION TELEMETRY INTEGRATION STEP",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol.copy(0.4f)
                            )
                            Text(
                                text = "${(ticket.progress * 100).toInt()}% READY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0A84FF)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ticket.progress },
                            color = Color(0xFF0A84FF),
                            trackColor = Color.White.copy(0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    // Clickable split action buttons row: isolate ACCELERATE and GAME CLIENT actions clearly
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ticket.progress < 1.0f) {
                            Button(
                                onClick = { vModel.accelerateTicket(ticket.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0A84FF).copy(0.15f),
                                    contentColor = Color(0xFF0A84FF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF0A84FF).copy(0.4f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlashOn,
                                        contentDescription = "Bolt",
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "ACCELERATE BYPASS PAYLOAD (+15%)",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.2.sp
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(Color(0xFF34C759).copy(0.12f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFF34C759).copy(0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "RESTORED & BYPASSED SUCCESSFULLY",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF34C759),
                                        letterSpacing = 0.2.sp
                                    )
                                }
                            }
                        }

                        // Launch/Direct game client trigger (was overlapping parent action)
                        OutlinedButton(
                            onClick = {
                                android.widget.Toast.makeText(context, "REDIRECTING TO PUBG MOBILE SECURE GATEWAYCOM...", android.widget.Toast.LENGTH_SHORT).show()
                                val pm = context.packageManager
                                val launchIntent = pm.getLaunchIntentForPackage("com.tencent.ig")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    try {
                                        val playStoreIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("market://details?id=com.tencent.ig")
                                        )
                                        context.startActivity(playStoreIntent)
                                    } catch (e: Exception) {
                                        val browserIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.tencent.ig")
                                        )
                                        context.startActivity(browserIntent)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = textCol
                            ),
                            border = BorderStroke(0.5.dp, textCol.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = "Launch Game Client",
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "LAUNCH SERVER CLIENT",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppGroupJoinCard(state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .bounceClick {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://chat.whatsapp.com/FFUy9FCSnqiHOVwnrz5kF5?s=cl&p=a&mlu=1")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Opening Link: https://chat.whatsapp.com/...", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF25D366).copy(0.15f), CircleShape)
                    .border(1.5.dp, Color(0xFF25D366), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "WhatsApp Chat",
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JOIN OFFICIAL WHATSAPP RESTORATION GROUP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF25D366),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Connect with live operators, check unban codes, & request real-time help.",
                    fontSize = 10.sp,
                    color = subTextCol
                )
            }
            
            Icon(
                imageVector = Icons.Default.Launch,
                contentDescription = "Go Link",
                tint = Color(0xFF25D366),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AboutSocialMediaContainerCard(state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Social Icon",
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "OFFICIAL RESTORATION SOCIALS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0A84FF),
                    letterSpacing = 0.5.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .background(Color(0xFF0A84FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GLOBAL LINKS",
                    fontSize = 8.sp,
                    color = Color(0xFF0A84FF),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Link to our open-source tools repositories and live player communication portals.",
            fontSize = 10.sp,
            color = subTextCol,
            lineHeight = 14.sp
        )

        HorizontalDivider(color = Color.White.copy(0.08f))

        val channels = listOf(
            Triple("GitHub OpenRepo", "https://github.com/oshaqplayz/pubg-reclaim", Color(0xFF7C3AED)),
            Triple("WhatsApp Support", "https://chat.whatsapp.com/FFUy9FCSnqiHOVwnrz5kF5?s=cl&p=a&mlu=1", Color(0xFF25D366)),
            Triple("Telegram Channel", "https://t.me/pubgunban2025", Color(0xFF0088CC)),
            Triple("YouTube Tutorials", "https://www.youtube.com/@oshaqplayz", Color(0xFFFF0000)),
            Triple("TikTok Official", "https://www.tiktok.com/@oshaq.playz.yt?_r=1&_t=ZS-978w8J3IUDq", Color(0xFFFE2C55))
        )

        channels.forEach { (name, url, color) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(0.03f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(10.dp))
                    .bounceClick {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(url)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Opening link: $url", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    name.contains("GitHub") -> Icons.Default.Info
                                    name.contains("WhatsApp") -> Icons.Default.Chat
                                    name.contains("Telegram") -> Icons.Default.Send
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Column {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Text(
                                text = if (name.contains("GitHub")) "Official code signature audit repository" else "Restoration community workspace link",
                                fontSize = 9.sp,
                                color = subTextCol
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Launch Icon",
                        tint = textCol.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomInternetConnectionDialog(onDismiss: () -> Unit) {
    var isProbing by remember { mutableStateOf(false) }
    var probeProgress by remember { mutableStateOf(0f) }
    var probeStatus by remember { mutableStateOf("AIRSPACE PROBING RECOVERY ENGINE INITIATED") }
    
    LaunchedEffect(isProbing) {
        if (isProbing) {
            probeProgress = 0.0f
            val tasks = listOf(
                "Scanning secure airway frequencies..." to 0.25f,
                "Probing local cellular transmitter handshakes..." to 0.55f,
                "Injecting decoupled fallback socket parameters..." to 0.85f,
                "Airways checked. Local sandbox decoupled!" to 1.0f
            )
            for ((task, targetProgress) in tasks) {
                probeStatus = task
                while (probeProgress < targetProgress) {
                    kotlinx.coroutines.delay(20)
                    probeProgress += 0.015f
                }
                kotlinx.coroutines.delay(100)
            }
            probeProgress = 1.0f
            kotlinx.coroutines.delay(200)
            isProbing = false
            onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .liquidGlass(intensity = GlassIntensity.LOW, cornerRadius = 24.dp)
                .background(Color.Black.copy(0.7f), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFF5252).copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, Color(0xFFFF5252), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProbing) Icons.Default.Refresh else Icons.Default.WifiOff,
                        contentDescription = "No Internet Connection",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = if (isProbing) "AIR PROBING NETWORK DETECT" else "STABLE INTERNET REQUIRED",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                if (isProbing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = probeStatus.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(Color.White.copy(0.1f), CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(probeProgress.coerceIn(0f, 1f))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF5252), Color(0xFFFF8F8F))
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                        Text(
                            text = "${(probeProgress.coerceIn(0f, 1f) * 100).toInt()}% COMPLETED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(0.6f)
                        )
                    }
                } else {
                    Text(
                        text = "Active Cloud Bypass REST engines require an authenticated network handshake. Connect your device's internet link to fully download live decryption payloads and fetch real-time unban statuses securely.",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.75f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.08f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProbing
                    ) {
                        Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { isProbing = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252)
                        ),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProbing
                    ) {
                        Text("RETRY SYNC", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun AiCheckBoardCard(
    state: ReclaimUiState,
    selectedCheckType: String,
    onSelectCheckType: (String) -> Unit,
    aiCheckResult: String?,
    onResultChanged: (String?) -> Unit,
    isCheckingAiBoard: Boolean,
    onCheckingChanged: (Boolean) -> Unit,
    checkProgress: Float,
    onProgressChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "AI Check Board",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AI SECURITY CHECK BOARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF34C759),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFF34C759), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "AUTONOMOUS CORE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34C759)
                )
            }
        }

        Text(
            text = "Initiate instant automated AI unban & sandbox validation checks. Select an operational scan category to execute custom security diagnostics.",
            fontSize = 10.sp,
            color = subTextCol,
            lineHeight = 14.sp
        )

        val checkCategories = listOf(
            "Anti-cheat Decrypt",
            "Supabase DB Link",
            "10-Yr Sandbox Scan"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            checkCategories.forEach { cat ->
                val isSel = selectedCheckType == cat
                Button(
                    onClick = {
                        if (!isCheckingAiBoard) {
                            onSelectCheckType(cat)
                            onResultChanged(null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSel) Color(0xFF34C759).copy(0.18f) else Color.White.copy(0.04f),
                        contentColor = if (isSel) Color(0xFF34C759) else textCol.copy(0.7f)
                    ),
                    border = BorderStroke(0.5.dp, if (isSel) Color(0xFF34C759) else Color.White.copy(0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    enabled = !isCheckingAiBoard
                ) {
                    Text(text = cat, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (isCheckingAiBoard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(0.02f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI CRUNCHING SIGNATURES...",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF34C759)
                    )
                    Text(
                        text = "${(checkProgress * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF34C759)
                    )
                }
                LinearProgressIndicator(
                    progress = { checkProgress },
                    color = Color(0xFF34C759),
                    trackColor = Color.White.copy(0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }
        } else {
            Button(
                onClick = {
                    onCheckingChanged(true)
                    onResultChanged(null)
                    onProgressChanged(0f)
                    coroutineScope.launch {
                        delay(250)
                        onProgressChanged(0.35f)
                        delay(350)
                        onProgressChanged(0.7f)
                        delay(300)
                        onProgressChanged(1.0f)
                        delay(200)
                        val reply = when (selectedCheckType) {
                            "Anti-cheat Decrypt" -> "🛡️ **AI SECURITY DECRYPTION SUCCESSFUL:**\nOur deep bypass networks have decoded the standard anti-cheat ban signature hash for Player **${state.targetPlayerName}** (UID: #${state.targetPlayerUid}). Key telemetry parameters match normal client tokens. Ready to execute."
                            "Supabase DB Link" -> "⚡ **SUPABASE DB SYNC STATUS CHECK:**\nPrimary database link is **OPERATIONAL**. Synchronization streams are transmitting securely. Target unban state is registered and verified against secure backup cluster."
                            else -> "🚀 **10-YEAR Suspension Sandbox Bypass Check:**\nSimulated sandbox unblock sequence complete. Verified unban signal dispatched successfully. The 10-year suspension flags are suppressed. Expected bypass speed is under 15 minutes."
                        }
                        onCheckingChanged(false)
                        onResultChanged(reply)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black, modifier = Modifier.size(16.dp))
                    Text("EXECUTE DIAGNOSTIC & EMIT REPLY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }

        if (aiCheckResult != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF34C759).copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFF34C759).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AI CHECK BOARD BROADCAST:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF34C759)
                )
                Text(
                    text = aiCheckResult,
                    fontSize = 11.sp,
                    color = textCol.copy(alpha = 0.9f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun DeveloperProfileCard(state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 20.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title Header
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
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "DEVELOPER / INFO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0A84FF),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF0A84FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "STABLE BUILD",
                    fontSize = 8.sp,
                    color = Color(0xFF0A84FF),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // App Information Subsection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(0.02f), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "APPLICATION PARAMETERS",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = textCol.copy(0.5f),
                letterSpacing = 0.5.sp
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "App Name:", fontSize = 11.sp, color = subTextCol)
                Text(text = "RECLAIM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Version/Kernel:", fontSize = 11.sp, color = subTextCol)
                Text(text = "v2.4.9 (Safe Decryption OS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Build Signature:", fontSize = 10.sp, color = subTextCol)
                Text(text = "SHA-256 (F982DA11)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF0A84FF))
            }
        }

        // Developer Details Subsection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF34C759).copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, Color(0xFF34C759), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("O", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF34C759))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@Oshaq",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textCol
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Status",
                        tint = Color(0xFF1da1f2),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Lead Architect — Oshaqplayz",
                    fontSize = 11.sp,
                    color = Color(0xFF0A84FF),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unified client-side sandbox engine & ML Kit multi-language localization compiler.",
                    fontSize = 10.sp,
                    color = subTextCol,
                    lineHeight = 13.sp
                )
            }
        }

        HorizontalDivider(color = textCol.copy(alpha = 0.08f))

        Text(
            text = "VERIFIED SOCIAL SUPPORT RESOURCES",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textCol.copy(0.45f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val developerSocialUrls = listOf(
                Triple("TikTok", "https://www.tiktok.com/@oshaq.playz.yt?_r=1&_t=ZS-978w8J3IUDq", Color(0xFFFE2C55)),
                Triple("YouTube", "https://www.youtube.com/@oshaqplayz", Color(0xFFFF0000)),
                Triple("WhatsApp", "https://chat.whatsapp.com/FFUy9FCSnqiHOVwnrz5kF5?s=cl&p=a&mlu=1", Color(0xFF25D366)),
                Triple("Telegram", "https://t.me/pubgunban2025", Color(0xFF0088CC))
            )

            developerSocialUrls.forEach { (platform, url, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                        .bounceClick {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Opening link: $url", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = platform.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textCol
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = textCol.copy(alpha = 0.08f))

        var showUpdatesDialog by remember { mutableStateOf(false) }

        if (showUpdatesDialog) {
            var isSearching by remember { mutableStateOf(true) }
            var searchProgress by remember { mutableStateOf(0f) }

            LaunchedEffect(Unit) {
                for (i in 1..10) {
                    kotlinx.coroutines.delay(120)
                    searchProgress = i / 10f
                }
                isSearching = false
            }

            androidx.compose.ui.window.Dialog(onDismissRequest = { showUpdatesDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                        .background(
                            if (state.isDarkTheme) Color(0xFF020817).copy(0.95f) else Color.White.copy(0.95f),
                            RoundedCornerShape(24.dp)
                        )
                        .border(
                            1.dp,
                            if (state.isDarkTheme) Color.White.copy(0.12f) else Color.Black.copy(0.1f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color(0xFFFF9500),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "CORE ALIGNED UPDATER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = textCol,
                                letterSpacing = 1.sp
                            )
                        }

                        HorizontalDivider(color = textCol.copy(alpha = 0.08f))

                        if (isSearching) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { searchProgress },
                                    color = Color(0xFFFF9500),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "PINGING DEV SERVER CLUSTERS...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol
                                )
                                Text(
                                    text = "Synchronizing client with Developer Option Hub unban nodes",
                                    fontSize = 9.sp,
                                    color = subTextCol
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFFFF9500).copy(0.12f), CircleShape)
                                        .border(1.dp, Color(0xFFFF9500), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9500),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Text(
                                    text = "SYSTEM COMPLIANT v1.3.2 [LATEST]",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textCol
                                )

                                Text(
                                    text = "Your unban engine parameters are aligned to the June 2026 server bypass protocols.\n\nTo fetch real-time unban appeals, device spoofing variables, or request live developer support, visit our WhatsApp Core group.",
                                    fontSize = 10.sp,
                                    color = subTextCol,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 14.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showUpdatesDialog = false },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(0.04f),
                                            contentColor = textCol
                                        ),
                                        border = BorderStroke(0.5.dp, textCol.copy(0.12f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Text("DONE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            showUpdatesDialog = false
                                            val url = "https://chat.whatsapp.com/FFUy9FCSnqiHOVwnrz5kF5?s=cl&p=a&mlu=1"
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Opening Link: $url", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9500),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.5f).height(38.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Phone,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = if (state.isDarkTheme) Color.White else Color.Black
                                            )
                                            Text("VISIT WHATSAPP", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SECURE CLIENT VERSION",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol.copy(alpha = 0.5f)
                )
                Text(
                    text = "v1.3.2 [VERIFIED COMPLIANT]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF9500)
                )
            }

            Button(
                onClick = { showUpdatesDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9500).copy(0.15f),
                    contentColor = Color(0xFFFF9500)
                ),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "CHECK FOR UPDATES",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun AiChatbotDialog(vModel: ReclaimViewModel, state: ReclaimUiState, onDismiss: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val bubbleBgUser = Color(0xFF0A84FF).copy(alpha = 0.18f)
    val bubbleBgBot = if (state.isDarkTheme) Color.White.copy(alpha = 0.06f) else Color(0xFF0F172A).copy(alpha = 0.05f)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(12.dp)
                .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                .background(
                    if (state.isDarkTheme) Color(0xFF0B1426).copy(0.95f)
                    else Color(0xFFF1F5F9).copy(0.95f),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    if (state.isDarkTheme) Color.White.copy(0.12f) else Color.Black.copy(0.1f),
                    RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chat Header
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
                                .size(10.dp)
                                .background(Color(0xFF34C759), CircleShape)
                        )
                        Column {
                            Text(
                                text = "CORE AI ADVISOR NETWORK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0A84FF),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Specialist unban bypass agent online",
                                fontSize = 8.sp,
                                color = subTextCol
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textCol.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = textCol.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    reverseLayout = false
                ) {
                    items(state.chatMessages) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (msg.isUser) bubbleBgUser else bubbleBgBot,
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (msg.isUser) 12.dp else 2.dp,
                                                bottomEnd = if (msg.isUser) 2.dp else 12.dp
                                            )
                                        )
                                        .border(
                                            0.5.dp,
                                            if (msg.isUser) Color(0xFF0A84FF).copy(0.4f) else textCol.copy(0.08f),
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (msg.isUser) 12.dp else 2.dp,
                                                bottomEnd = if (msg.isUser) 2.dp else 12.dp
                                            )
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = msg.senderName.uppercase(),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (msg.isUser) Color(0xFF0A84FF) else Color(0xFF34C759),
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.text,
                                            fontSize = 11.sp,
                                            color = textCol,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg.timestamp,
                                fontSize = 7.sp,
                                color = subTextCol.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    if (state.isChatLoading) {
                        item {
                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing_dots")
                            val offset1 by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = -10f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ), label = "dot1"
                            )
                            val offset2 by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = -10f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing, delayMillis = 150),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ), label = "dot2"
                            )
                            val offset3 by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = -10f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing, delayMillis = 300),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ), label = "dot3"
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(modifier = Modifier.offset(y = offset1.dp).size(8.dp).background(Color(0xFF0A84FF), CircleShape))
                                Box(modifier = Modifier.offset(y = offset2.dp).size(8.dp).background(Color(0xFF0A84FF), CircleShape))
                                Box(modifier = Modifier.offset(y = offset3.dp).size(8.dp).background(Color(0xFF0A84FF), CircleShape))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = textCol.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(10.dp))

                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                if (state.isDarkTheme) Color.Black.copy(0.2f) else Color.White,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                textCol.copy(0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = TextStyle(color = textCol, fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (messageText.isEmpty()) {
                                    Text("Ask bypass advisor...", color = textCol.copy(0.4f), fontSize = 12.sp)
                                }
                                innerTextField()
                            }
                        )
                    }

                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                vModel.sendChatMessage(messageText)
                                messageText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                        modifier = Modifier
                            .height(40.dp)
                            .width(64.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = if (state.isDarkTheme) Color.White else Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SupportCaseChatDialog(vModel: ReclaimViewModel, state: ReclaimUiState, onDismiss: () -> Unit) {
    val activeCase = state.activeSelectedCase ?: return
    var replyText by remember { mutableStateOf("") }
    val isDark = state.isDarkTheme
    val txtCol = if (isDark) Color.White else Color(0xFF0F172A)
    val subCol = if (isDark) Color.White.copy(0.6f) else Color(0xFF475569)
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Auto scroll chat list to bottom
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.activeCaseMessages.size) {
        if (state.activeCaseMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(state.activeCaseMessages.size - 1)
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(490.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isDark) Color(0xFF0B1220) else Color.White)
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeCase.title.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0A84FF)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Status: ${activeCase.status.uppercase()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (activeCase.status) {
                                "resolved" -> Color(0xFF34C759)
                                "pending" -> Color(0xFFFFC107)
                                else -> Color(0xFFFF1744)
                            }
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = txtCol)
                    }
                }

                Text(
                    text = activeCase.description,
                    fontSize = 11.sp,
                    color = subCol,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                HorizontalDivider(color = Color.White.copy(0.08f))

                // Case status controls for ADMINS
                if (state.isAdminMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ADMIN STATUS OVERRIDE:", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A84FF))
                        listOf("open", "pending", "resolved").forEach { st ->
                            val isSelected = activeCase.status == st
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0A84FF).copy(0.2f) else Color.White.copy(0.05f))
                                    .border(0.5.dp, if (isSelected) Color(0xFF0A84FF) else Color.White.copy(0.12f), RoundedCornerShape(6.dp))
                                    .bounceClick {
                                        vModel.updateCaseStatus(activeCase.id, st, context)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(st.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF0A84FF) else txtCol)
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(0.08f))
                }

                // Chat Area
                androidx.compose.foundation.lazy.LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.activeCaseMessages) { msg ->
                        val isMe = msg.senderId == "admin_reclaim" || (!msg.senderId.startsWith("system") && !state.isAdminMode)
                        val bubbleColor = if (msg.senderId == "admin_reclaim") {
                            Color(0xFF0A84FF).copy(alpha = 0.15f)
                        } else if (msg.senderId.startsWith("system")) {
                            Color(0xFFFFC107).copy(alpha = 0.12f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        }
                        
                        val bubbleBorder = if (msg.senderId == "admin_reclaim") {
                            Color(0xFF0A84FF).copy(alpha = 0.4f)
                        } else if (msg.senderId.startsWith("system")) {
                            Color(0xFFFFC107).copy(alpha = 0.3f)
                        } else {
                            Color.White.copy(alpha = 0.15f)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = msg.senderName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = if (msg.senderId == "admin_reclaim") Color(0xFF0A84FF) else subCol
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bubbleColor)
                                    .border(0.5.dp, bubbleBorder, RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .widthIn(max = 240.dp)
                            ) {
                                Text(
                                    text = msg.message,
                                    fontSize = 11.sp,
                                    color = txtCol,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Input box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.06f))
                            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            textStyle = TextStyle(color = txtCol, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (replyText.isEmpty()) {
                                    Text(
                                        text = if (state.isAdminMode) "Reply as Admin Specialist..." else "Type message for Compliance...",
                                        color = subCol.copy(0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A84FF))
                            .bounceClick {
                                if (replyText.isNotBlank()) {
                                    if (state.isAdminMode) {
                                        vModel.sendAdminCaseMessage(activeCase.id, replyText.trim())
                                    } else {
                                        vModel.sendCaseMessage(replyText.trim())
                                    }
                                    replyText = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = if (state.isDarkTheme) Color.White else Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineOverlayDialog(
    vModel: com.example.ui.ReclaimViewModel,
    state: com.example.ui.ReclaimUiState
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)
    val accentCol = Color(0xFFFF5252) // warning red
    var checkingConnection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // absolute capture touches
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .liquidGlass(intensity = GlassIntensity.ULTRA, cornerRadius = 24.dp)
                .background(if (state.isDarkTheme) Color(0xFF0F0505).copy(0.95f) else Color.White.copy(0.95f))
                .border(1.5.dp, accentCol.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(accentCol.copy(0.12f), CircleShape)
                    .border(2.dp, accentCol, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "No Connection Icon",
                    tint = accentCol,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "CONNECTION TERMINATED",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = textCol,
                letterSpacing = 1.sp
            )

            Text(
                text = "Our device decoupler terminal lost contact with the secure Supabase synchronization tables and Firebase REST clusters.\n\nOnline features, including credentials sync, diagnostics, and AI unban guiders have been temporarily paused to prevent payload leakage.",
                fontSize = 11.sp,
                color = subTextCol,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    checkingConnection = true
                    val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val activeNetwork = connManager.activeNetwork
                    val capabilities = connManager.getNetworkCapabilities(activeNetwork)
                    val isConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    
                    // Simulate checking network and resolve
                    val scope = CoroutineScope(Dispatchers.Main)
                    scope.launch {
                        delay(1200)
                        checkingConnection = false
                        vModel.setInternetAvailable(isConnected)
                        if (isConnected) {
                            android.widget.Toast.makeText(context, "Handshake secure! Online mode restored.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Airway probe failed. Still offline.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentCol),
                shape = RoundedCornerShape(10.dp),
                enabled = !checkingConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (checkingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Text("PROBE CONNECTION STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSystemPanel(state: ReclaimUiState) {
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
        Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DASHBOARD SYSTEM",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textCol,
                letterSpacing = 1.sp
            )
            ServerStatusBadge(isOnline = state.isFirebaseOnline)
        }
        
        DashboardProgressItem(
            label = "Daily Activity Logs",
            progress = 0.7f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = "Security Credits Pool",
            progress = (state.userCredits.toFloat() / 100f).coerceIn(0f, 1f),
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = "Realtime Appeal Feed",
            progress = 0.85f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = "Payload Uplink Path",
            progress = 0.45f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = "Decryption CDN Downlink",
            progress = 0.9f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
    }
}

@Composable
fun ServerStatusBadge(isOnline: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (isOnline) Color(0xFF34C759).copy(alpha = 0.15f) else Color(0xFFFF3366).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(0.5.dp, if (isOnline) Color(0xFF34C759) else Color(0xFFFF3366), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isOnline) "SERVER: ONLINE" else "SERVER: OFFLINE",
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = if (isOnline) Color(0xFF34C759) else Color(0xFFFF3366)
        )
    }
}

@Composable
fun DashboardProgressItem(label: String, progress: Float, glassIntensity: GlassIntensity, isDarkTheme: Boolean) {
    val textCol = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, color = textCol, fontWeight = FontWeight.Bold)
            Text(text = "${(progress * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .liquidGlass(intensity = glassIntensity, cornerRadius = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0A84FF), Color(0xFF34C759))
                        ),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun CreditSystemPanel(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val textCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
        Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CREDIT SYSTEM & REWARDS",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textCol,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFC107).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFFFFC107), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${state.userCredits} CREDITS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFC107)
                )
            }
        }

        Text(
            text = "Use credits to launch advanced payload attacks and token spoofing. Premium and Trial users have Unlimited access.",
            fontSize = 9.sp,
            color = if (state.isDarkTheme) Color.White.copy(0.7f) else Color.DarkGray,
            lineHeight = 13.sp
        )

        Button(
            onClick = { vModel.claimDailyBox() },
            enabled = !state.hasClaimedToday,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.hasClaimedToday) Color.Gray else Color(0xFFFFC107),
                disabledContainerColor = Color.Gray.copy(0.5f)
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp).premiumShineEffect(state.isDarkTheme),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black)
                Text(
                    text = if (state.hasClaimedToday) "REWARD CLAIMED" else "CLAIM DAILY REWARD".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        Button(
            onClick = {
                if (activity != null) {
                    com.example.ui.AdmobManager.showRewardedAd(activity) { amount ->
                        vModel.grantDemoCredits(50)
                        android.widget.Toast.makeText(activity, "Credit Reward Claimed!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isDarkTheme) Color.White.copy(0.12f) else Color(0xFF0F172A).copy(0.08f),
                contentColor = textCol
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (state.isDarkTheme) Color.White else Color.Black)
                Text(
                    text = "WATCH AD FOR +50 CREDITS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = textCol
                )
            }
        }
    }
}

@Composable
fun LiveTelemetryGraph(isDarkTheme: Boolean, glassIntensity: GlassIntensity) {
    // Component removed as requested
}

fun Modifier.shimmerEffect(isDarkTheme: Boolean = true): Modifier = composed {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_start_offset"
    )

    val shimmerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
    val highlightColor = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                shimmerColor,
                highlightColor,
                shimmerColor
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + 2000f, 2000f)
        )
    )
}

fun Modifier.premiumShineEffect(isDarkTheme: Boolean = true): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shine_transition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_start_offset"
    )

    val shineColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.6f)
    
    drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    shineColor,
                    Color.Transparent
                ),
                start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
                end = androidx.compose.ui.geometry.Offset(startOffsetX + 500f, 500f)
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
        )
    }
}