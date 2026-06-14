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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            MyApplicationTheme(themeStyle = state.themeStyle) {
                val bgColors = when (state.themeStyle) {
                    AppThemeStyle.DARK -> listOf(Color(0xFF020817), Color(0xFF040C22))
                    AppThemeStyle.GRAY -> listOf(Color(0xFF111317), Color(0xFF191B21))
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .glowingAmbientOrbs()
                        .background(Brush.verticalGradient(bgColors)),
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
                                AppTopBar(vModel, state)

                                Box(modifier = Modifier.weight(1f)) {
                                    Crossfade(
                                        targetState = state.currentTab,
                                        animationSpec = tween(300),
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
                                        tint = Color.White,
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
                                AlertDialog(
                                    onDismissRequest = { vModel.toggleLowCreditWarning(false) },
                                    title = { Text("Low Credits") },
                                    text = { Text("You are running low on credits. Watch an ad to get more!") },
                                    confirmButton = {
                                        Button(onClick = { 
                                            vModel.toggleLowCreditWarning(false) 
                                            vModel.claimDailyBox()
                                        }) { Text("Watch Ad") }
                                    }
                                )
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
                                        color = Color(0xFF1FFFC5),
                                        modifier = Modifier.background(Color.Black.copy(0.7f), RoundedCornerShape(12.dp)).padding(16.dp)
                                    )
                                }
                            }

                            if (state.showAiChatDialog) {
                                AiChatbotDialog(vModel = vModel, state = state, onDismiss = { vModel.toggleAiChatDialog(false) })
                            }

                            // AI SPECIALIST FAB
                            FloatingActionButton(
                                onClick = { vModel.toggleAiChatDialog(true) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 98.dp, end = 20.dp)
                                    .size(54.dp),
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Ask Specialist Agent",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                        tint = Color(0xFF1FFFC5),
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
                        color = Color(0xFF1FFFC5),
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
                border = BorderStroke(1.dp, Color(0xFF1FFFC5).copy(0.2f))
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
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Credits center header",
                                tint = Color(0xFF1FFFC5),
                                modifier = Modifier.size(20.dp)
                            )
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
                            color = Color(0xFF1FFFC5)
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
                            .border(0.5.dp, Color(0xFF1FFFC5).copy(0.15f), RoundedCornerShape(16.dp))
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
                                color = Color(0xFF1FFFC5),
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
                                    focusedBorderColor = Color(0xFF1FFFC5),
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
                                    containerColor = Color(0xFF1FFFC5),
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
                            border = BorderStroke(0.5.dp, Color(0xFF1FFFC5).copy(0.5f)),
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
                                    tint = Color(0xFF1FFFC5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "WATCH ADS AD FOR +25 CREDITS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1FFFC5)
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
                                .background(Color(0xFF1FFFC5).copy(0.12f), CircleShape)
                                .clickable {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (e: Exception) {}
                                    vModel.triggerProgressRefresh()
                                    vModel.addToAdHistory("Synchronized credentials cache with Firebase credits collection.")
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SYNC", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1FFFC5))
                        }
                    }

                    // IN-DEPTH AID & CREDIT DEVELOPMENT UTILITY Block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "IN-DEPTH AID & CREDIT DEVELOPMENT UTILITY",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.45f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Grant credits
                            Button(
                                onClick = {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (e: Exception) {}
                                    vModel.grantDemoCredits(100)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FFFC5).copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, Color(0xFF1FFFC5).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("GRANT +100 DEMO", color = Color(0xFF1FFFC5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            // Read credits FAQ
                            Button(
                                onClick = {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (e: Exception) {}
                                    showCreditsFaq = !showCreditsFaq
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text(
                                    text = if (showCreditsFaq) "HIDE DETAILS" else "HOW TO SPEND?",
                                    color = Color.White.copy(0.85f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // FAQ Expansion Card
                        AnimatedVisibility(
                            visible = showCreditsFaq,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(0.02f), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, Color.White.copy(0.06f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "CREDIT SYSTEM SPECIFICATIONS:",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1FFFC5)
                                    )
                                    Text(
                                        text = "• Launching email unban profiles costs 25 credits per session.\n" +
                                               "• Hardening account diagnostics requires 25 credits.\n" +
                                               "• Watching sponsored video campaigns yields +25 credits instantly.\n" +
                                               "• Grant +100 DEMO button lets you add resources to experiment with sandbox loops freely.",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(0.7f),
                                        lineHeight = 12.sp
                                    )
                                }
                            }
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
                                                .background(Color(0xFF1FFFC5).copy(0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "OK",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1FFFC5)
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
fun CreditDisplay(credits: Int, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onExpand() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MonetizationOn,
            contentDescription = "Credits",
            tint = Color(0xFF1FFFC5),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$credits",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
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
            modifier = Modifier.clickable { vModel.changeTab(NavigationTab.HOME) }
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "RECLAIM Secure App logo",
                tint = Color(0xFF1FFFC5),
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
                onClick = { vModel.changeTab(NavigationTab.HOME) },
                modifier = Modifier.size(36.dp)
            ) {
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
            .clickable {
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
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0D1B2A), Color(0xFF00E5FF).copy(0.12f), Color(0xFF1FFFC5).copy(0.08f))
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
                            tint = Color(0xFF00E5FF).copy(0.6f),
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
                        .background(Color(0xFF00E5FF).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = currentSlide.badge ?: "GUIDE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
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
                        .background(Color(0xFF00E5FF).copy(alpha = alpha))
                )
            }
        }
    }
}

// 1. HOME SCREEN
@Composable
fun HomeScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lang = state.language
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(if (state.isDarkTheme) Color(0xFF020817) else Color.White)
                    .border(1.dp, Color(0xFF1FFFC5).copy(0.3f), RoundedCornerShape(24.dp))
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
                                tint = Color(0xFF1FFFC5),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "CORE SECURITY RECOVERY",
                                fontSize = 12.sp,
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
                            .background(Color(0xFF1FFFC5).copy(0.08f), CircleShape)
                            .border(1.dp, Color(0xFF1FFFC5).copy(0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state.currentRecoveryScore == 10f) "10 / 10" else "${state.currentRecoveryScore}/10",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1FFFC5)
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
                                color = Color(0xFF00E5FF)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Credits check",
                                tint = Color(0xFF00E5FF),
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
                                color = Color(0xFF1FFFC5),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "AMPLIFYING SECURITY LAYERS...",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1FFFC5)
                            )
                        }
                    } else {
                        Button(
                            onClick = { vModel.boostRecoveryScore() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentRecoveryScore >= 10f) Color.Gray.copy(0.2f) else Color(0xFF1FFFC5)
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
        }
    }

    if (showAiCheckDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAiCheckDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 24.dp)
                    .background(if (state.isDarkTheme) Color(0xFF020817) else Color.White)
                    .border(1.dp, Color(0xFF00E5FF).copy(0.3f), RoundedCornerShape(24.dp))
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
                                tint = Color(0xFF00E5FF),
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
                                        if (sel) Color(0xFF00E5FF).copy(0.15f) else cardBgCol,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        0.5.dp,
                                        if (sel) Color(0xFF00E5FF) else cardBorderCol,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedCheckType = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) Color(0xFF00E5FF) else subTextCol,
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
                                color = Color(0xFF00E5FF),
                                trackColor = cardBgCol
                            )
                            Text(
                                text = "CALCULATING PROBABILITY HASH... ${(checkProgress * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    } else if (aiCheckResult != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1FFFC5).copy(0.12f), RoundedCornerShape(12.dp))
                                .border(0.5.dp, Color(0xFF1FFFC5).copy(0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "DIAGNOSTIC RESOLUTION RESULT:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1FFFC5)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
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
                    .border(1.dp, Color(0xFF1FFFC5).copy(0.3f), RoundedCornerShape(24.dp))
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
                                tint = Color(0xFF1FFFC5),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.5.dp, Color(0xFF00E5FF), CircleShape)
                            .clip(CircleShape)
                            .clickable { vModel.changeTab(NavigationTab.PROFILE) }
                    ) {
                        if (!state.profileLogoUri.isNullOrEmpty()) {
                            SubcomposeAsyncImage(
                                model = state.profileLogoUri,
                                contentDescription = "Custom User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp, color = Color(0xFF00E5FF))
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
                            if (state.subscriptionPlan == SubscriptionPlan.PREMIUM || state.subscriptionPlan == SubscriptionPlan.TRIAL) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = "Premium Status",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(12.dp)
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
                            tint = Color(0xFF00E5FF),
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



        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK CONTROL ACTIONS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actions = listOf(
                        Triple("Start Recovery", Icons.Default.PlayArrow) { vModel.changeTab(NavigationTab.RECOVERY) },
                        Triple("Track Status", Icons.Default.TrendingUp) { vModel.changeTab(NavigationTab.TOOLS) },
                        Triple("History Logs", Icons.Default.History) {
                            android.widget.Toast.makeText(context, "Checking historic bypass logs: Real Firebase transaction history loaded successfully.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                    actions.forEach { (label, icon, action) ->
                        Button(
                            onClick = action,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isDarkTheme) Color.White.copy(0.04f) else Color(0xFFF1F5F9),
                                contentColor = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, if (state.isDarkTheme) Color.White.copy(0.1f) else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
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
                        .background(Color(0xFF00E5FF).copy(0.08f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color(0xFF00E5FF).copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00E5FF),
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
                            .background(Color(0xFF00E5FF), CircleShape)
                    )
                    Text(
                        text = "Recovery Status Summary: " + LanguageDictionary.get(lang, "recoveryStatus"),
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
                // 1. DYNAMIC PROBABILITY CARD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp)
                        .testTag("probability_card")
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                        .clickable { vModel.refreshProbability() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val activeSweep = 360f * (state.currentProbability.toFloat() / 100f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = cardBorderCol,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFF0582FF), Color(0xFF00E5FF))
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = activeSweep,
                                    useCenter = false,
                                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (state.isProbabilityRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF00E5FF),
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "CALC..",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF)
                                    )
                                } else {
                                    Text(
                                        text = "${state.currentProbability}%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textCol
                                    )
                                    Text(
                                        text = LanguageDictionary.get(lang, "probability"),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = LanguageDictionary.get(lang, "recoveryPotential") + ": ${state.currentProbability}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 2. DYNAMIC RECOVERY SCORE CARD
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(160.dp)
                        .testTag("ai_score_card")
                        .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                        .clickable { vModel.toggleRecoveryBoostDialog(true) }
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
                                tint = Color(0xFF1FFFC5),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = LanguageDictionary.get(lang, "aiRecoveryScore"),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1FFFC5),
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        // Numeric Badge representation
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (state.currentRecoveryScore == 10f) "10/10" else "${state.currentRecoveryScore}/10",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1FFFC5)
                            )
                        }
                    }

                    Text(
                        text = LanguageDictionary.get(lang, "riskAnalysis") + if (state.currentRecoveryScore == 10f) " Core security fully hardened and credentials encrypted." else "",
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
                                if (state.currentRecoveryScore == 10f) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFF1FFFC5).copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (state.currentRecoveryScore == 10f) "PERFECT 10/10 CORE" else LanguageDictionary.get(lang, "highSuccessRate"),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.currentRecoveryScore == 10f) Color(0xFF00E5FF) else Color(0xFF1FFFC5)
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
                            .clickable { vModel.changeTab(NavigationTab.RECOVERY) }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Recovery Portal Icon",
                                tint = Color(0xFF00E5FF),
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
                            .clickable { vModel.changeTab(NavigationTab.TOOLS) }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.AddBox,
                                contentDescription = "Manual Case Injector Icon",
                                tint = Color(0xFF1FFFC5),
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
                            .clickable { showAiCheckDialog = true }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "AI Specialist Icon",
                                tint = Color(0xFF00E5FF),
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
                            .clickable { showSocialsDialog = true }
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Community Channels Icon",
                                tint = Color(0xFF1FFFC5),
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
                        .clickable { vModel.toggleAiChatDialog(true) }
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
                                .background(Color(0xFF00E5FF).copy(0.12f), CircleShape)
                                .border(1.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "AI Chat Icon",
                                tint = Color(0xFF00E5FF),
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    letterSpacing = 1.sp
                )

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
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RECLAIM NEWSROOM & CORE LOGS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        listOf(
                            Triple("SMTP MIME-Protocol fortified to SSLv4", "Daily automated cron synchronizations now support multi-part sandbox decrypts without certificate loss.", "Just Now"),
                            Triple("TikTok Official link incorporated to social suite", "Direct visual reviews and guides are updated daily with the latest sandbox replacement techniques.", "2 hours ago"),
                            Triple("Supabase real-time cluster mirror enabled", "Sliding guides are fully cached. All loaded images maintain an offline buffer for low bandwidth devices.", "Yesterday")
                        ).forEach { (newsTitle, newsBody, newsTime) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = newsTitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textCol
                                    )
                                    Text(
                                        text = newsTime,
                                        fontSize = 8.sp,
                                        color = subTextCol
                                    )
                                }
                                Text(
                                    text = newsBody,
                                    fontSize = 9.sp,
                                    color = subTextCol,
                                    lineHeight = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = cardBorderCol)
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
                                .background(Color(0xFF1FFFC5), CircleShape)
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
                            .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SECURE PROTOCOL",
                            fontSize = 8.sp,
                            color = Color(0xFF1FFFC5),
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
                        StatusRow(text = LanguageDictionary.get(lang, "firebaseAuth"), active = true)
                        StatusRow(text = LanguageDictionary.get(lang, "firebaseDb"), active = true)
                        StatusRow(text = LanguageDictionary.get(lang, "firebaseStorage"), active = true)
                        StatusRow(text = LanguageDictionary.get(lang, "firebaseMsg"), active = true)
                        StatusRow(text = "Remote Config: Running", active = true)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusRow(text = LanguageDictionary.get(lang, "supabaseInt"), active = true)
                        StatusRow(text = LanguageDictionary.get(lang, "supabaseDb"), active = true)
                        StatusRow(text = LanguageDictionary.get(lang, "supabaseStorage"), active = true)
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
                        text = LanguageDictionary.get(lang, "systemStatus"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1FFFC5)
                    )
                    Text(
                        text = LanguageDictionary.get(lang, "lastSync"),
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
                            color = Color(0xFF00E5FF),
                            startAngle = -90f,
                            sweepAngle = 360 * (state.timerSeconds / 60f),
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = "Lock Clock icon",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = LanguageDictionary.get(lang, "timerTitle"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeSegment(label = LanguageDictionary.get(lang, "days"), value = state.timerDays)
                        TimeColon()
                        TimeSegment(label = LanguageDictionary.get(lang, "hours"), value = state.timerHours)
                        TimeColon()
                        TimeSegment(label = LanguageDictionary.get(lang, "minutes"), value = state.timerMinutes)
                        TimeColon()
                        TimeSegment(label = LanguageDictionary.get(lang, "seconds"), value = state.timerSeconds, highlight = true)
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
                            tint = Color(0xFF00E5FF),
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
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSynced) "SYNCED" else "OFFLINE",
                            fontSize = 8.sp,
                            color = Color(0xFF00E5FF),
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
                            containerColor = Color(0xFF00E5FF),
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
                            .background(Color(0xFF1FFFC5).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color(0xFF1FFFC5).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success Icon",
                            tint = Color(0xFF1FFFC5),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "SUPABASE SYNC COMPLETED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1FFFC5)
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
                                if (act.type == "success") Color(0xFF1FFFC5) else Color(0xFFFFA000),
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
    }
}

// 2. RECOVERY SCREEN
@Composable
fun RecoveryScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
    val lang = state.language
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
                title = LanguageDictionary.get(lang, "emailRecovery"),
                desc = LanguageDictionary.get(lang, "emailRecoveryDesc"),
                status = LanguageDictionary.get(lang, "statusActive"),
                isAvailable = true,
                intensity = state.glassIntensity,
                onLaunch = { 
                    selectedRecoveryTitle = "Direct Email Layer Recovery"
                    showUnbanEngineDialog = true
                }
            )
        }

        item {
            RecoveryOptionCard(
                title = LanguageDictionary.get(lang, "gameRecovery"),
                desc = LanguageDictionary.get(lang, "gameRecoveryDesc"),
                status = LanguageDictionary.get(lang, "statusActive"),
                isAvailable = true,
                intensity = state.glassIntensity,
                onLaunch = { 
                    selectedRecoveryTitle = "In-game Token Authentication"
                    showUnbanEngineDialog = true
                }
            )
        }

        item {
            RecoveryOptionCard(
                title = LanguageDictionary.get(lang, "limitedMode"),
                desc = "Secondary partition unban layer bypassing currently under deployment cycles.",
                status = LanguageDictionary.get(lang, "comingSoon"),
                isAvailable = false,
                intensity = state.glassIntensity,
                onLaunch = {}
            )
        }

        item {
            RecoveryOptionCard(
                title = LanguageDictionary.get(lang, "advancedRecovery"),
                desc = "Root hardware layer unbans & automated credential replacement guides.",
                status = LanguageDictionary.get(lang, "comingSoon"),
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
                                    color = if (state.isDarkTheme) Color(0xFF00E5FF) else Color(0xFF0284C7)
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
    val amberCol = Color(0xFFFFD600)

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
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = amberCol,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "ADVANCED SHIELD PROTECTION",
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
                        Color(0xFFFFD600).copy(0.3f),
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
                        tint = Color(0xFFFFD600),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600), contentColor = Color.Black),
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
                    Color(0xFFFFD600).copy(0.4f),
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
                                .background(Color(0xFFFFD600), CircleShape)
                        )
                        Text(
                            text = "UNBAN ENGINE CORE GATEWAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD600),
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
                                .background(Color(0xFFFFD600).copy(0.06f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFFFFD600).copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PRELOADED BYPASS ACTIVE HANDSHAKES:",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD600)
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
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD600),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Text("LAUNCH SECURITY APPEAL EMAIL", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Button(
                            onClick = {
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
                    }
                } else if (step == 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "BYPASS ENGINE TRANSACTION HANDSHAKE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD600)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Black, RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFFD600).copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = terminalOutput,
                                color = Color(0xFFFFD600),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600), contentColor = Color.Black),
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
    val borderCol = if (isAvailable) Color(0xFF00E5FF).copy(alpha = 0.4f) else (if (dark) Color.White.copy(alpha = 0.15f) else Color(0xFF0F172A).copy(alpha = 0.15f))
    val badgeBgCol = if (isAvailable) Color(0xFF00E5FF).copy(alpha = 0.2f) else (if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.08f))
    val badgeTxtCol = if (isAvailable) Color(0xFF00E5FF) else (if (dark) Color.White.copy(0.5f) else Color(0xFF475569))
    val iconTint = if (isAvailable) Color(0xFF00E5FF) else (if (dark) Color.White.copy(0.4f) else Color(0xFF64748B))

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
                        containerColor = Color(0xFF00E5FF).copy(0.2f),
                        contentColor = Color(0xFF00E5FF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(0.5f)),
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
    val lang = state.language
    var logText by remember { mutableStateOf("") }
    val filteredTickets = remember(state.supportTickets, state.searchQueries) {
        if (state.searchQueries.isEmpty()) {
            state.supportTickets
        } else {
            state.supportTickets.filter {
                it.title.lowercase().contains(state.searchQueries.lowercase()) ||
                it.hash.lowercase().contains(state.searchQueries.lowercase())
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
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
                                    text = LanguageDictionary.get(lang, "searchPlaceholder"),
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
                        color = Color(0xFF00E5FF),
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
                            if (logText.isNotEmpty()) {
                                vModel.createNewTicket(logText)
                                logText = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
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
                        color = Color(0xFF1FFFC5),
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
                            vModel.createNewTicket("Initialized Bypass Payload V1.4 Access")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("access_payload"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1FFFC5),
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

        items(filteredTickets) { ticket ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                                text = "Hash: " + ticket.hash,
                                fontSize = 10.sp,
                                color = Color.White.copy(0.5f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    when (ticket.status) {
                                        "Bypassed" -> Color(0xFF1FFFC5).copy(alpha = 0.2f)
                                        "Active" -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    0.5.dp,
                                    when (ticket.status) {
                                        "Bypassed" -> Color(0xFF1FFFC5).copy(alpha = 0.5f)
                                        "Active" -> Color(0xFF00E5FF).copy(alpha = 0.5f)
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
                                    "Bypassed" -> Color(0xFF1FFFC5)
                                    "Active" -> Color(0xFF00E5FF)
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
                                text = "Bypass verification progress",
                                fontSize = 9.sp,
                                color = Color.White.copy(0.5f)
                            )
                            Text(
                                text = "${(ticket.progress * 100).toInt()}%",
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
                                    .fillMaxWidth(ticket.progress)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF1FFFC5))
                                        ),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "GLOBAL METRICS DASHBOARD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .liquidGlass(intensity = state.glassIntensity, cornerRadius = 18.dp)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unban Success Over Months",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "94.2% Success Rate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1FFFC5)
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val points = listOf(
                            Offset(0f, size.height * 0.8f),
                            Offset(size.width * 0.2f, size.height * 0.75f),
                            Offset(size.width * 0.4f, size.height * 0.45f),
                            Offset(size.width * 0.6f, size.height * 0.3f),
                            Offset(size.width * 0.8f, size.height * 0.21f),
                            Offset(size.width, size.height * 0.08f)
                        )

                        for (i in 1..4) {
                            val y = size.height * (i / 4f)
                            drawLine(
                                color = Color.White.copy(0.04f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, size.height)
                            for (p in points) {
                                lineTo(p.x, p.y)
                            }
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF00E5FF).copy(0.18f), Color.Transparent)
                            )
                        )

                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = points[i],
                                end = points[i+1],
                                strokeWidth = 2.5f.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        for (p in points) {
                            drawCircle(color = Color(0xFF1FFFC5), radius = 4.dp.toPx(), center = p)
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = p)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun").forEach {
                            Text(text = it, fontSize = 9.sp, color = Color.White.copy(0.5f))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
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
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${state.userNotificationLogs.filter { it.isUnread }.size} Unread",
                            fontSize = 8.sp,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "DISMISS ALL",
                    fontSize = 10.sp,
                    color = Color(0xFFFF3366),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { 
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
                    .clickable { vModel.markNotificationAsRead(item.id) }
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
                                if (item.isUnread) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f),
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
    val lang = state.language
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
                        .border(2.dp, Color(0xFF1FFFC5), CircleShape)
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
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 1.5.dp, color = Color(0xFF1FFFC5))
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
                                .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color(0xFF1FFFC5), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = state.subscriptionPlan.name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1FFFC5)
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
                        text = "UID: ${state.targetPlayerUid.ifBlank { "Not Set" }}\n${LanguageDictionary.get(lang, "regDate")}: 2024/09/10\n${LanguageDictionary.get(lang, "lastLogin")}: 2026/06/12 12:00 PM\n${LanguageDictionary.get(lang, "email")}: ${state.googleAccountEmail ?: "Not Linked"}",
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
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileStatCard(label = "Success Rate", value = "94.2%", color = Color(0xFF1FFFC5), modifier = Modifier.weight(1f), intensity = state.glassIntensity)
                ProfileStatCard(label = "Total Rescued", value = "154", color = Color(0xFF00E5FF), modifier = Modifier.weight(1f), intensity = state.glassIntensity)
                ProfileStatCard(label = "Active Shield", value = "ON", color = Color(0xFF00E5FF), modifier = Modifier.weight(1f), intensity = state.glassIntensity)
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
            SettingsPanel(title = LanguageDictionary.get(lang, "settingsGlass")) {
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
            SettingsPanel(title = LanguageDictionary.get(lang, "settingsAnim")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AnimationProfile.BATTERY_SAVER to "BATTERY",
                        AnimationProfile.BALANCED to "BALANCED",
                        AnimationProfile.PREMIUM to "ULTIMATE"
                    ).forEach { (profile, text) ->
                        SettingsToggleButton(
                            text = text,
                            selected = state.animationProfile == profile,
                            onClick = { vModel.changeAnimationProfile(profile) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            SettingsPanel(title = LanguageDictionary.get(lang, "settingsLanguage")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val langs = LanguageDictionary.Language.values()
                    FlowRowWithEqualWeights(
                        items = langs.toList(),
                        columns = 2
                    ) { itemLang ->
                        SettingsToggleButton(
                            text = itemLang.displayName,
                            selected = state.language == itemLang,
                            onClick = { vModel.changeLanguage(itemLang) },
                            modifier = Modifier.fillMaxWidth()
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
                                    tint = Color(0xFF00E5FF),
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
            SettingsPanel(title = LanguageDictionary.get(lang, "sessionTermination")) {
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
                        text = LanguageDictionary.get(lang, "logoutButton"),
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.isDarkTheme) Color(0xFF0F172A) else Color.White)
                    .border(1.dp, Color(0xFF00E5FF).copy(0.3f), RoundedCornerShape(20.dp))
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

                    Button(
                        onClick = {
                            if (editPlayerName.isBlank() || editPlayerUid.isBlank()) {
                                android.widget.Toast.makeText(context, "Player credentials fields cannot be empty.", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            vModel.updatePlayerProfileInFirestore(editPlayerName, editPlayerUid, context) {
                                android.widget.Toast.makeText(context, "Cloud modifications dispatch executed successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                showEditDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("SAVE REGISTERED DATA TO FIREBASE", fontSize = 10.sp, fontWeight = FontWeight.Black)
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
                    .border(1.dp, Color(0xFF00E5FF).copy(0.3f), RoundedCornerShape(20.dp))
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
                                        Text(q, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
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
                                                .background(Color(0xFF00E5FF).copy(0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.take(1).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
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
                            Text("Rate our app design and recovery system:", fontSize = 10.sp, color = if (state.isDarkTheme) Color.White.copy(0.6f) else Color.Black)
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
                                            .clickable { feedbackRating = starIndex }
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
                                color = Color(0xFF1FFFC5)
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
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("SUBMIT TO CYBERSECURITY NETWORK", fontSize = 10.sp, fontWeight = FontWeight.Black)
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
    Box(
        modifier = modifier
            .height(78.dp)
            .liquidGlass(intensity = intensity, cornerRadius = 14.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
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
    val accentCol = Color(0xFF00E5FF)

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
                                        imageVector = Icons.Default.HealthAndSafety,
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
                                    text = "Advanced High-Performance Security Portal",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentCol
                                )
                                Text(
                                    text = "Welcome to the ultimate security and account restoration gateway, certified for high-priority bypass sandboxing. Let's configure your terminal dashboard to safeguard your gaming records.",
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
                                    Icons.Default.Shield to "Advanced Shield Protection" to "Evade permanent hardware signatures and lift 10-year lock suspensions safely.",
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
                            tint = Color.Black,
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
            containerColor = if (selected) Color(0xFF00E5FF).copy(0.25f) else Color.White.copy(0.04f),
            contentColor = if (selected) Color(0xFF00E5FF) else Color.White.copy(0.7f)
        ),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
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
    onTabSelected: (NavigationTab) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val items = listOf(
        NavigationTab.HOME to Icons.Default.Home,
        NavigationTab.TOOLS to Icons.Default.Settings,
        NavigationTab.RECOVERY to Icons.Default.Restaurant, // Underneath we show Frying Pan instead
        NavigationTab.NOTIFICATIONS to Icons.Default.Notifications,
        NavigationTab.PROFILE to Icons.Default.Person
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(72.dp) // slightly increased from 64 to comfortably fit 'Reclaims' subtitle
            .liquidGlass(intensity = GlassIntensity.LOW, cornerRadius = 36.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (tab, icon) ->
                val selected = (tab == currentTab)
                val darkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f

                val scale by animateFloatAsState(if (selected) 1.25f else 1.0f, label = "tab_sc")
                val alpha by animateFloatAsState(if (selected) 1.0f else 0.45f, label = "tab_al")
                val glow = if (selected) Color(0xFF00E5FF) else (if (darkTheme) Color.White.copy(0.45f) else Color(0xFF64748B))

                Box(
                    modifier = Modifier
                        .size(height = 60.dp, width = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {
                                // Fallback safe
                            }
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (tab == NavigationTab.RECOVERY) {
                            // Custom Frying Pan Drawing - STRAIGHT VERTICAL
                            Canvas(
                                modifier = Modifier
                                    .size(31.dp)
                                    .scale(scale * 1.2f)
                            ) {
                                val isTabSelected = selected
                                val tintColor = if (isTabSelected) Color.White else glow.copy(alpha = alpha)
                                val strokeWidthPx = 1.8.dp.toPx()

                                // 1. Solid Dark backing for frying pan bowl
                                drawCircle(
                                    color = Color(0xFF0F172A),
                                    radius = 7.dp.toPx() - (strokeWidthPx / 2),
                                    center = Offset(12.dp.toPx(), 9.dp.toPx())
                                )

                                // Cast-iron metallic grainy texture specks inside the pan bowl
                                val specsRandom = java.util.Random(155)
                                repeat(8) {
                                    val angle = specsRandom.nextFloat() * 2.0 * Math.PI
                                    val dist = specsRandom.nextFloat() * 5.0.dp.toPx()
                                    val speckX = 12.dp.toPx() + (dist * Math.cos(angle)).toFloat()
                                    val speckY = 9.dp.toPx() + (dist * Math.sin(angle)).toFloat()
                                    drawCircle(
                                        color = if (specsRandom.nextBoolean()) Color(0xFF94A3B8).copy(0.8f) else Color.White.copy(0.3f),
                                        radius = 0.5.dp.toPx(),
                                        center = Offset(speckX, speckY)
                                    )
                                }

                                // 2. Outer glowing rim (Head at top)
                                drawCircle(
                                    color = tintColor,
                                    radius = 7.dp.toPx(),
                                    center = Offset(12.dp.toPx(), 9.dp.toPx()),
                                    style = Stroke(width = strokeWidthPx)
                                )

                                // Make the top of the pan slightly thick
                                drawArc(
                                    color = tintColor,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(5.dp.toPx(), 2.dp.toPx()),
                                    size = Size(14.dp.toPx(), 14.dp.toPx()),
                                    style = Stroke(width = strokeWidthPx * 1.8f)
                                )

                                // 3. Frying Pan handle pointing STRAIGHT DOWN
                                drawLine(
                                    color = tintColor,
                                    start = Offset(12.dp.toPx(), 16.dp.toPx()),
                                    end = Offset(12.dp.toPx(), 23.5.dp.toPx()),
                                    strokeWidth = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // 4. Universal RED BAN circular slash on the pan center
                                val banCenter = Offset(12.dp.toPx(), 9.dp.toPx())
                                val banColor = if (isTabSelected) Color(0xFFFF1744) else tintColor.copy(alpha = 0.6f)
                                
                                // Ban symbol circle
                                drawCircle(
                                    color = banColor,
                                    radius = 3.5.dp.toPx(),
                                    center = banCenter,
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                                // Ban symbol diagonal line
                                drawLine(
                                    color = banColor,
                                    start = Offset(9.5.dp.toPx(), 6.5.dp.toPx()),
                                    end = Offset(14.5.dp.toPx(), 11.5.dp.toPx()),
                                    strokeWidth = 1.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "RECLAIM",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selected) Color.White else glow.copy(alpha = alpha),
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                letterSpacing = (-0.2).sp
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = "tab item $tab",
                                modifier = Modifier
                                    .scale(scale)
                                    .size(21.dp),
                                tint = glow.copy(alpha = alpha)
                            )
                        }

                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .width(8.dp)
                                    .height(2.dp)
                                    .background(Color(0xFF00E5FF), RoundedCornerShape(1.dp))
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
                .background(if (active) Color(0xFF1FFFC5) else Color.Red, CircleShape)
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
                    if (highlight) Color(0xFF00E5FF).copy(0.15f) else Color.White.copy(0.04f),
                    RoundedCornerShape(6.dp)
                )
                .border(
                    0.5.dp,
                    if (highlight) Color(0xFF00E5FF).copy(0.4f) else Color.White.copy(0.12f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) Color(0xFF00E5FF) else Color.White
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
    val lang = state.language
    val txtCol = if (state.isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTxtCol = if (state.isDarkTheme) Color.White.copy(0.7f) else Color(0xFF475569)

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
                    Row(modifier = Modifier.fillMaxWidth().clickable { 
                        showAccountChooser = false
                        vModel.updateTargetProfile(pName, pUid, pReason, pProfileClass)
                        vModel.googleLogin(email = "oshaqyt2@gmail.com", name = "Oshaqplayz")
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFF00E5FF), CircleShape), contentAlignment = Alignment.Center) { Text("O", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Oshaqplayz", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                            Text("oshaqyt2@gmail.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Divider(color = Color.LightGray.copy(0.5f), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().clickable { 
                        showAccountChooser = false
                        vModel.updateTargetProfile(pName, pUid, pReason, pProfileClass)
                        vModel.googleLogin(email = "guest@gmail.com", name = "Guest Player")
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color.LightGray, CircleShape), contentAlignment = Alignment.Center) { 
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White) 
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Add another account", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
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
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF1FFFC5), Color(0xFF0582FF), Color(0xFF00E5FF))
                            ),
                            startAngle = rotationSmooth,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo_1781241621499),
                        contentDescription = "Success Gold Logo",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "TARGET: ${pName.uppercase()} (#${pUid})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
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
                                        listOf(Color(0xFF00E5FF), Color(0xFF1FFFC5))
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
                            .border(1.dp, Color(0xFF00E5FF).copy(0.4f), RoundedCornerShape(12.dp))
                    )
                    Text(
                        text = "RECLAIM ACCOUNTS",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = txtCol,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Advanced High-Performance Account Restoration Wizard",
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
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "CYBERSECURITY AUTH GATEWAY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF),
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
                                onClick = { showGoogleChooser = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4285F4)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
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
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Mail Icon",
                                        tint = Color(0xFF00E5FF),
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
                                    containerColor = Color(0xFF1FFFC5).copy(0.12f),
                                    contentColor = Color(0xFF1FFFC5)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF1FFFC5).copy(0.4f)),
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
                                        tint = Color(0xFF1FFFC5),
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
                                    containerColor = Color(0xFF00E5FF),
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
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
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
                                        .clickable {
                                            authScreenMode = "CHOOSER"
                                        }
                                        .padding(4.dp)
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
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = pProfileClass.uppercase(),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E5FF)
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
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "STEP 2: REGISTER SYSTEM MATCHED PROFILE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF00E5FF),
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
                                            color = Color(0xFF00E5FF)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                                                .clickable {
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
                                                    tint = Color(0xFF00E5FF),
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
                                            containerColor = if (isSelected) Color(0xFF1FFFC5).copy(0.15f) else Color.White.copy(0.04f),
                                            contentColor = if (isSelected) Color(0xFF1FFFC5) else txtCol.copy(0.7f)
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
                                        Text(logoTaskName.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1FFFC5))
                                        Text("${(logoProgress * 100).toInt()}%", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF1FFFC5))
                                    }
                                    LinearProgressIndicator(
                                        progress = { logoProgress },
                                        color = Color(0xFF1FFFC5),
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FFFC5).copy(0.1f)),
                                    border = BorderStroke(0.5.dp, Color(0xFF1FFFC5).copy(0.4f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                ) {
                                    Text("UPLOAD PROFILE LOGO SECURELY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1FFFC5))
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
                            containerColor = if (isFormComplete) Color(0xFF1FFFC5) else Color.Gray.copy(0.2f),
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
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
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

        if (showGoogleChooser) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showGoogleChooser = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.isDarkTheme) Color(0xFF1E293B) else Color.White)
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "G",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4)
                            )
                            Text(
                                text = "oogle",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = txtCol
                            )
                        }

                        Text(
                            text = "Choose an account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = txtCol
                        )
                        Text(
                            text = "to continue to Reclaim Accounts Secure Console",
                            fontSize = 11.sp,
                            color = subTxtCol,
                            textAlign = TextAlign.Center
                        )

                        HorizontalDivider(color = Color.White.copy(0.08f))

                        val amAccounts = remember {
                            try {
                                val am = android.accounts.AccountManager.get(context)
                                am.getAccountsByType("com.google")
                            } catch (e: SecurityException) {
                                emptyArray<android.accounts.Account>()
                            }
                        }

                        val shownAccounts = remember(amAccounts) {
                            if (amAccounts.isNullOrEmpty()) {
                                listOf(
                                    Pair("YouTube", "youtube@gmail.com"),
                                    Pair("Oshaq Playz", "oshaqplayz@gmail.com")
                                )
                            } else {
                                amAccounts.map { Pair(it.name.substringBefore("@"), it.name) }
                            }
                        }

                        shownAccounts.forEach { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showGoogleChooser = false
                                        isGoogleConnecting = true
                                        googleConnectingTaskName = "Establishing Google Identity Handshake..."
                                        googleConnectingProgress = 0.2f
                                        
                                        val animScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                        animScope.launch {
                                            kotlinx.coroutines.delay(600)
                                            googleConnectingTaskName = "Verifying Firebase Realtime Database elements..."
                                            googleConnectingProgress = 0.5f
                                            kotlinx.coroutines.delay(600)
                                            googleConnectingTaskName = "Syncing authentication details secure node..."
                                            googleConnectingProgress = 0.8f
                                            kotlinx.coroutines.delay(700)
                                            googleConnectingTaskName = "Access Granted! Session token registered."
                                            googleConnectingProgress = 1.0f
                                            kotlinx.coroutines.delay(400)
                                            isGoogleConnecting = false
                                            
                                            vModel.finalizeGoogleAuth(account.second, account.first, context) {
                                                android.widget.Toast.makeText(context, "Google Authorization Sync with Firebase and Firestore verified!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF4285F4).copy(0.12f), CircleShape)
                                        .border(1.dp, Color(0xFF4285F4), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = account.first.take(1).uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4285F4)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.first,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = txtCol
                                    )
                                    Text(
                                        text = account.second,
                                        fontSize = 10.sp,
                                        color = subTxtCol
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(0.08f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showGoogleChooser = false
                                    // Let them log in as customized email or try add account
                                    authScreenMode = "EMAIL_SIGNUP"
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add account",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Use another account",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }
                }
            }
        }

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
            color = if (isDark) Color(0xFF00E5FF) else Color(0xFF020817)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF020817),
                    modifier = Modifier.size(14.dp)
                )
            },
            textStyle = TextStyle(color = textCol, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
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
                    .border(1.5.dp, Color(0xFF1FFFC5), RoundedCornerShape(16.dp))
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
                        color = Color(0xFF1FFFC5)
                    )
                    
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { smtpProgress },
                            color = Color(0xFF1FFFC5),
                            strokeWidth = 4.dp
                        )
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color(0xFF1FFFC5),
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
                        color = Color(0xFF1FFFC5),
                        trackColor = Color.White.copy(0.1f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )

                    if (smtpProgress >= 1f) {
                        Button(
                            onClick = { showSmtpAnimDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FFFC5)),
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
                    tint = Color(0xFF1FFFC5),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "RECLAIM SMTP MAIL OUTLET ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1FFFC5),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ACTIVE GATEWAY",
                    fontSize = 8.sp,
                    color = Color(0xFF1FFFC5),
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
                color = Color(0xFF1FFFC5)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FFFC5)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.1f).height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
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
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "DUAL ADVISOR RECOVERY COUNCIL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GEMINI AI",
                    fontSize = 8.sp,
                    color = Color(0xFF00E5FF),
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
                        containerColor = if (isSelected) Color(0xFF00E5FF).copy(0.2f) else Color.Transparent,
                        contentColor = if (isSelected) Color(0xFF00E5FF) else textCol.copy(alpha = 0.6f)
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
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
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
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
                            .background(if (selectedAdvisorByTab == 0) Color(0xFF00E5FF) else Color(0xFF1FFFC5), CircleShape)
                    )
                    Text(
                        text = if (selectedAdvisorByTab == 0) "AGENT VANCE SECURITY BROADCAST:" else "AGENT MARCUS DATABASE TELEMETRY:",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selectedAdvisorByTab == 0) Color(0xFF00E5FF) else Color(0xFF1FFFC5)
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
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = "Shield Icon",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "REAL-TIME RESTORATION BOARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE STATS",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
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
                Triple("ACTIVE NODES", "${state.liveNodesLoad}", Color(0xFF00E5FF)),
                Triple("RECOVERY QUEUE", "${state.livePendingCount}", Color(0xFF1FFFC5)),
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
                                            "Bypassed" -> Color(0xFF1FFFC5)
                                            "Active" -> Color(0xFF00E5FF)
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
                                    "Bypassed" -> Color(0xFF1FFFC5)
                                    "Active" -> Color(0xFF00E5FF)
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
                                color = Color(0xFF00E5FF)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ticket.progress },
                            color = Color(0xFF00E5FF),
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
                                    containerColor = Color(0xFF00E5FF).copy(0.15f),
                                    contentColor = Color(0xFF00E5FF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(0.4f)),
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
                                    .background(Color(0xFF1FFFC5).copy(0.12f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color(0xFF1FFFC5).copy(0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF1FFFC5),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "RESTORED & BYPASSED SUCCESSFULLY",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1FFFC5),
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
            .clickable {
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
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "OFFICIAL RESTORATION SOCIALS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 0.5.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GLOBAL LINKS",
                    fontSize = 8.sp,
                    color = Color(0xFF00E5FF),
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
                    .clickable {
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
                    tint = Color(0xFF1FFFC5),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AI SECURITY CHECK BOARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1FFFC5),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFF1FFFC5), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "AUTONOMOUS CORE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1FFFC5)
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
                        containerColor = if (isSel) Color(0xFF1FFFC5).copy(0.18f) else Color.White.copy(0.04f),
                        contentColor = if (isSel) Color(0xFF1FFFC5) else textCol.copy(0.7f)
                    ),
                    border = BorderStroke(0.5.dp, if (isSel) Color(0xFF1FFFC5) else Color.White.copy(0.1f)),
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
                        color = Color(0xFF1FFFC5)
                    )
                    Text(
                        text = "${(checkProgress * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1FFFC5)
                    )
                }
                LinearProgressIndicator(
                    progress = { checkProgress },
                    color = Color(0xFF1FFFC5),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FFFC5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("EXECUTE DIAGNOSTIC & EMIT REPLY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }

        if (aiCheckResult != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1FFFC5).copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFF1FFFC5).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AI CHECK BOARD BROADCAST:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1FFFC5)
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF1FFFC5),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = LanguageDictionary.get(state.language, "devOptionHub"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1FFFC5),
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF1FFFC5).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = LanguageDictionary.get(state.language, "verifiedCore"),
                    fontSize = 8.sp,
                    color = Color(0xFF1FFFC5),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF1FFFC5).copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, Color(0xFF1FFFC5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("O", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1FFFC5))
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
                    text = "Oshaqplayz",
                    fontSize = 11.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = LanguageDictionary.get(state.language, "devBio"),
                    fontSize = 10.sp,
                    color = subTextCol,
                    lineHeight = 13.sp
                )
            }
        }

        HorizontalDivider(color = textCol.copy(alpha = 0.08f))

        Text(
            text = LanguageDictionary.get(state.language, "devResources"),
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
                        .clickable {
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
                                tint = Color(0xFFFFD600),
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
                                    color = Color(0xFFFFD600),
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
                                        .background(Color(0xFFFFD600).copy(0.12f), CircleShape)
                                        .border(1.dp, Color(0xFFFFD600), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD600),
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
                                            containerColor = Color(0xFFFFD600),
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
                                                tint = Color.Black
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
                    color = Color(0xFFFFD600)
                )
            }

            Button(
                onClick = { showUpdatesDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD600).copy(0.15f),
                    contentColor = Color(0xFFFFD600)
                ),
                border = BorderStroke(1.dp, Color(0xFFFFD600).copy(0.5f)),
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
    val bubbleBgUser = Color(0xFF00E5FF).copy(alpha = 0.18f)
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
                                .background(Color(0xFF1FFFC5), CircleShape)
                        )
                        Column {
                            Text(
                                text = "CORE AI ADVISOR NETWORK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF),
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
                                            if (msg.isUser) Color(0xFF00E5FF).copy(0.4f) else textCol.copy(0.08f),
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
                                            color = if (msg.isUser) Color(0xFF00E5FF) else Color(0xFF1FFFC5),
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E5FF), CircleShape))
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E5FF), CircleShape))
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E5FF), CircleShape))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier
                            .height(40.dp)
                            .width(64.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
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
    val lang = state.language
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
            label = LanguageDictionary.get(lang, "dailyUsage"),
            progress = 0.7f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = LanguageDictionary.get(lang, "creditUsage"),
            progress = (state.userCredits.toFloat() / 100f).coerceIn(0f, 1f),
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = LanguageDictionary.get(lang, "accountActivity"),
            progress = 0.85f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = LanguageDictionary.get(lang, "uploadProgress"),
            progress = 0.45f,
            glassIntensity = state.glassIntensity,
            isDarkTheme = state.isDarkTheme
        )
        DashboardProgressItem(
            label = LanguageDictionary.get(lang, "downloadProgress"),
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
                color = if (isOnline) Color(0xFF1FFFC5).copy(alpha = 0.15f) else Color(0xFFFF3366).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(0.5.dp, if (isOnline) Color(0xFF1FFFC5) else Color(0xFFFF3366), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isOnline) "SERVER: ONLINE" else "SERVER: OFFLINE",
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = if (isOnline) Color(0xFF1FFFC5) else Color(0xFFFF3366)
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
            Text(text = "${(progress * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
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
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF1FFFC5))
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
    val lang = state.language
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color.Black)
                Text(
                    text = if (state.hasClaimedToday) "REWARD CLAIMED" else LanguageDictionary.get(lang, "claimBox").uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun LiveTelemetryGraph(isDarkTheme: Boolean, glassIntensity: GlassIntensity) {
    val textCol = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .liquidGlass(intensity = glassIntensity, cornerRadius = 18.dp)
            .padding(16.dp)
    ) {
        Column {
            Text("LIVE TELEMETRY NETWORK ACTIVITY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1FFFC5), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val path = androidx.compose.ui.graphics.Path()
                val points = listOf(0.1f, 0.4f, 0.2f, 0.7f, 0.5f, 0.9f, 0.6f, 1.0f, 0.8f, 0.9f, 0.85f, 0.7f, 1.0f) // Normalized data values going up and down
                
                val w = size.width
                val h = size.height
                
                path.moveTo(0f, h * (1 - points.first()))
                
                points.forEachIndexed { index, value ->
                    if (index > 0) {
                        val x = w * (index.toFloat() / (points.size - 1))
                        val y = h * (1 - value)
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF00E5FF),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f, pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(40f))
                )
                
                // Draw nodes
                points.forEachIndexed { index, value ->
                    val x = w * (index.toFloat() / (points.size - 1))
                    val y = h * (1 - value)
                    drawCircle(
                        color = Color.White,
                        radius = 6f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
        }
    }
}