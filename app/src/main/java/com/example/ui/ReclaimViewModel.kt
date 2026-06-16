package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.LiveActivityItem
import com.example.data.SupabaseSlider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val defaultSliders = listOf(
    SupabaseSlider(
        id = 1,
        title = "Account Recovery Guide",
        description = "Master the steps for keeping your account safely protected.",
        image_url = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&auto=format&fit=crop",
        badge = "GUIDE"
    ),
    SupabaseSlider(
        id = 2,
        title = "Email Help Resource",
        description = "Access key information for maintaining email safety and usage.",
        image_url = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=800&auto=format&fit=crop",
        badge = "EMAIL"
    ),
    SupabaseSlider(
        id = 3,
        title = "In-Game Recovery Guide",
        description = "Level up your gaming experience via helpful tips and community advice.",
        image_url = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=800&auto=format&fit=crop",
        badge = "GAMING"
    )
)

val defaultActivities = listOf(
    LiveActivityItem(
        id = "1",
        user = "Support center",
        action = "Replied to ticket ID #99824",
        time = "2 mins ago",
        type = "success"
    ),
    LiveActivityItem(
        id = "2",
        user = "Recovery server 4",
        action = "Completed account unban on PUBG-ID (42231)",
        time = "5 mins ago",
        type = "success"
    ),
    LiveActivityItem(
        id = "3",
        user = "System monitor",
        action = "Automatic cron DB mirror initiated successfully.",
        time = "10 mins ago",
        type = "info"
    )
)

enum class AppThemeStyle {
    DARK, GRAY
}

enum class AppThemeMode {
    LIGHT, DARK, TITANIUM, AUTO
}

enum class SubscriptionPlan {
    FREE, TRIAL, PREMIUM
}

data class ReclaimUiState(
    val currentTab: NavigationTab = NavigationTab.HOME,
    
    val glassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    val animationProfile: AnimationProfile = AnimationProfile.PREMIUM,
    val isDarkTheme: Boolean = false, // Titanium is light/textured by default now
    val themeStyle: AppThemeStyle = AppThemeStyle.GRAY,
    val themeMode: AppThemeMode = AppThemeMode.TITANIUM,
    
    // Onboarding & walkthrough completes
    val hasCompletedOnboarding: Boolean = false,
    val isInternetAvailable: Boolean = true,
    val isGuest: Boolean = false,
    
    // Credit and subscription systems
    val subscriptionPlan: SubscriptionPlan = SubscriptionPlan.TRIAL, // Default trial active for premium experience
    val userCredits: Int = 50,
    val hasClaimedToday: Boolean = false,
    val lastClaimDate: String = "", // Add last claim date (ISO format)
    val streakDays: Int = 0,
    val trialTimeRemainingSeconds: Int = 86400, // 24 hours
    val trialTimeRemainingText: String = "23h 59m 54s",
    
    //Countdown to "Next Major Update" 14 July 2026
    val updateDays: Int = 31,
    val updateHours: Int = 21,
    val updateMinutes: Int = 14,
    val updateSeconds: Int = 33,

    // Google auth and splash state
    val isLoggedIn: Boolean = false,
    val googleAccountName: String? = null,
    val googleAccountEmail: String? = null,
    val isSplashLoading: Boolean = false,
    val splashProgress: Float = 0f,
    val splashTaskName: String = "",
    
    // Customizable Player Target Profile to Reclaim
    val targetPlayerName: String = "",
    val targetPlayerUid: String = "",
    val targetBanReason: String = "",
    val targetProfileType: String = "Standard Elite VIP",
    val playerAccountMail: String = "",
    val playerAccountPhone: String = "",
    val playerPlatformLink: String = "Google Play Games API Gate",
    val profileLogoUri: String? = null, // Path or Uri to custom uploaded logo
    
    // Timer state for 90 days protection window
    val timerDays: Int = 89,
    val timerHours: Int = 23,
    val timerMinutes: Int = 59,
    val timerSeconds: Int = 54,
    
    // Live Service states
    val isSupabaseOnline: Boolean = true,
    val isFirebaseOnline: Boolean = true,
    
    // Real-time Up & Down fluctuating live telemetry parameters
    val liveNodesLoad: Int = 45,
    val livePendingCount: Int = 124,
    val liveBypassBps: Int = 1840,
    
    // Fetched items (initialized with defaults so they are never blank!)
    val supabaseSliders: List<SupabaseSlider> = defaultSliders,
    val firebaseActivities: List<LiveActivityItem> = defaultActivities,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshPercentage: Int = 100,
    
    // Search & History state
    val searchQueries: String = "",
    val supportTickets: List<TicketItem> = defaultTickets,
    val userNotificationLogs: List<NotificationLogItem> = defaultNotificationLogs,

    // AI Dual Advisor Guidance states
    val vanceResponse: String? = "Sandbox bypass protocol is prepared. Ask Agent Vance any firewall questions.",
    val marcusResponse: String? = "Supabase DB cluster is ready. Ask Agent Marcus anything about live REST table synchronization.",
    val aiQuery: String = "Explain how to bypass the 10-year sandbox ban signature.",
    val isAiLoading: Boolean = false,
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            "init_1", 
            "AI Assistant", 
            "Hello and welcome to the secure unban advisor chat. I am here to assist you with unblocking your account. How can I help you today?", 
            false, 
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        )
    ),
    val isChatLoading: Boolean = false,
    val isPreloadSandboxActive: Boolean = false,
    val isPreloadMailDecouplerActive: Boolean = false,
    val isPreloadTokenSpooferActive: Boolean = false,
    val showPremiumModal: Boolean = false,
    val showAdRewardAnimation: Boolean = false,
    val adHistory: List<String> = emptyList(),
    val showLowCreditWarning: Boolean = false,
    
    // Dynamic Probability & Recovery Score states
    val currentProbability: Int = 25,
    val isProbabilityRefreshing: Boolean = false,
    val currentRecoveryScore: Float = 6.8f,
    val isRecoveryScoreBoosting: Boolean = false,
    val showRecoveryBoostDialog: Boolean = false,

    // One-time Home Disclaimer popup
    val hasShownHomeDisclaimer: Boolean = false,
    val showHomeDisclaimerDialog: Boolean = false,
    val showAiChatDialog: Boolean = false,

    // Custom Toast Notifications
    val toastMessage: String = "",
    val toastType: String = "", // "SUCCESS", "ERROR", "INFO", "WARNING"
    val showToast: Boolean = false,

    // Promo Code System (Unlimited Balance Credit System)
    val isUnlimitedCredits: Boolean = false,
    val activatedPromoCode: String = "",
    val hasClaimedOneTimeReward: Boolean = false,
    val redeemedPromoCodes: List<String> = emptyList(),

    // Recovery History logs
    val recoveryHistory: List<RecoveryHistoryLogItem> = defaultRecoveryHistory,

    // Simulated email popup cheats warning states
    val showCheatsEmailWarningDialog: Boolean = false,
    val activeCheatsWarningUid: String = "",
    val activeCheatsWarningName: String = "",

    // Modern Onboarding / Profile Setup State
    val hasCompletedProfileSetup: Boolean = false,
    val authError: String? = null,
    val isAuthOperationLoading: Boolean = false,

    // Real-time support system states
    val supportCases: List<SupportCase> = emptyList(),
    val activeCaseMessages: List<CaseMessage> = emptyList(),
    val activeSelectedCase: SupportCase? = null,

    // Real-time globally synced news & updates
    val systemUpdates: List<SystemUpdate> = emptyList(),
    val isAdminMode: Boolean = false
)

enum class NavigationTab {
    HOME, RECOVERY, TOOLS, NOTIFICATIONS, PROFILE
}

enum class AnimationProfile {
    BATTERY_SAVER, BALANCED, PREMIUM
}

data class TicketItem(
    val id: String,
    val title: String,
    val hash: String,
    val status: String, // "Under Review", "Active", "Bypassed"
    val progress: Float
)

data class NotificationLogItem(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: String,
    val isUnread: Boolean
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: String
)

data class RecoveryHistoryLogItem(
    val id: String,
    val targetName: String,
    val targetUid: String,
    val timestamp: String,
    val status: String, // "COMPLETED", "FAILED", "RUNNING"
    val recoveryType: String,
    val progress: Float = 1.0f
)

data class SupportCase(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "open", // open, pending, resolved
    val userId: String = "",
    val createdAt: Long = 0L
)

data class CaseMessage(
    val id: String = "",
    val caseId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)

data class SystemUpdate(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0L
)

class ReclaimViewModel : ViewModel() {

    private val repository = AppRepository()
    private val _uiState = MutableStateFlow(ReclaimUiState())
    val uiState: StateFlow<ReclaimUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var telemetryJob: Job? = null

    private var casesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var updatesListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        loadData()
        startCountdownTimer()
        startTelemetryFluctuator()
        startListeningToUpdates()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sliders = repository.fetchSupabaseSliders()
                val activities = repository.fetchFirebaseLiveActivity()
                _uiState.update {
                    it.copy(
                        supabaseSliders = if (sliders.isNotEmpty()) sliders else defaultSliders,
                        firebaseActivities = if (activities.isNotEmpty()) activities else defaultActivities,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        supabaseSliders = defaultSliders,
                        firebaseActivities = defaultActivities,
                        isLoading = false
                    ) 
                }
            }
        }
    }

    private fun startTelemetryFluctuator() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (isActive) {
                delay(2500)
                _uiState.update {
                    val deltaLoad = (-3..3).random()
                    val deltaCount = (-2..2).random()
                    val deltaBps = (-45..45).random()
                    
                    val newLoad = (it.liveNodesLoad + deltaLoad).coerceIn(40, 52)
                    val newCount = (it.livePendingCount + deltaCount).coerceIn(115, 138)
                    val newBps = (it.liveBypassBps + deltaBps).coerceIn(1720, 1960)
                    
                    it.copy(
                        liveNodesLoad = newLoad,
                        livePendingCount = newCount,
                        liveBypassBps = newBps
                    )
                }
            }
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var totalSeconds = 89 * 86400 + 23 * 3600 + 59 * 60 + 54
            var updateSecondsLeft = 32 * 86400 + 14 * 3600 + 32 * 60 + 20 // Approx 32 days to July 14, 2026
            var trialSecondsLeft = 86400 // 1 day
            while (isActive) {
                delay(1000)
                
                // 1. 90-day reset timer
                if (totalSeconds > 0) {
                    totalSeconds--
                } else {
                    totalSeconds = 90 * 86400
                }
                val d = totalSeconds / 86400
                val h = (totalSeconds % 86400) / 3600
                val m = (totalSeconds % 3600) / 60
                val s = totalSeconds % 60

                // 2. Next Major Update countdown timer (July 14, 2026)
                if (updateSecondsLeft > 0) {
                    updateSecondsLeft--
                } else {
                    updateSecondsLeft = 32 * 86400
                }
                val updD = updateSecondsLeft / 86400
                val updH = (updateSecondsLeft % 86400) / 3600
                val updM = (updateSecondsLeft % 3600) / 60
                val updS = updateSecondsLeft % 60

                // 3. 1-Day Trial countdown timer
                if (trialSecondsLeft > 0) {
                    trialSecondsLeft--
                }
                val th = trialSecondsLeft / 3600
                val tm = (trialSecondsLeft % 3600) / 60
                val ts = trialSecondsLeft % 60
                val trialText = if (trialSecondsLeft > 0) "${th}h ${tm}m ${ts}s" else "TRIAL EXPIRED"
                val finalPlan = if (trialSecondsLeft <= 0 && _uiState.value.subscriptionPlan == SubscriptionPlan.TRIAL) {
                    SubscriptionPlan.FREE
                } else {
                    _uiState.value.subscriptionPlan
                }

                _uiState.update {
                    it.copy(
                        timerDays = d,
                        timerHours = h,
                        timerMinutes = m,
                        timerSeconds = s,
                        updateDays = updD,
                        updateHours = updH,
                        updateMinutes = updM,
                        updateSeconds = updS,
                        trialTimeRemainingSeconds = trialSecondsLeft,
                        trialTimeRemainingText = trialText,
                        subscriptionPlan = finalPlan
                    )
                }
            }
        }
    }

    fun changeTab(tab: NavigationTab) {
        _uiState.update { state ->
            state.copy(
                currentTab = tab,
                showHomeDisclaimerDialog = false,
                hasShownHomeDisclaimer = true
            )
        }
    }

    fun dismissHomeDisclaimerDialog() {
        _uiState.update { it.copy(showHomeDisclaimerDialog = false) }
    }

    

    private var customToastJob: Job? = null
    fun showToast(message: String, type: String = "SUCCESS") {
        customToastJob?.cancel()
        customToastJob = viewModelScope.launch {
            _uiState.update { it.copy(toastMessage = message, toastType = type, showToast = true) }
            delay(3500)
            _uiState.update { it.copy(showToast = false) }
        }
    }

    fun applyPromoCode(code: String): Boolean {
        val uppercaseCode = code.trim().uppercase()
        val unlimitedCodes = listOf("OSHAG", "OSHAGPLAYZ", "OSHAQ", "OSHAQPLAYZ", "UNLIMITED", "CREDITED", "RECLAIM2026")
        
        if (uppercaseCode in unlimitedCodes) {
            _uiState.update { state ->
                state.copy(
                    isUnlimitedCredits = true,
                    activatedPromoCode = uppercaseCode,
                    userCredits = 9999999,
                    subscriptionPlan = SubscriptionPlan.PREMIUM
                )
            }
            showToast("PROMO CODE '$uppercaseCode' ACCEPTED! PREMIUM UNLIMITED ACTIVE!", "SUCCESS")
            return true
        } else {
            return redeemPromoCode(uppercaseCode)
        }
    }

    fun addRecoveryLog(targetName: String, targetUid: String, type: String, status: String = "COMPLETED") {
        val newId = "REC-" + (1000 + (Math.random() * 9000).toInt())
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = RecoveryHistoryLogItem(
            id = newId,
            targetName = targetName,
            targetUid = targetUid,
            timestamp = timestamp,
            status = status,
            recoveryType = type
        )
        _uiState.update { state ->
            state.copy(
                recoveryHistory = listOf(newLog) + state.recoveryHistory
            )
        }
        showToast("RECOVERY HISTORY LOGGED SUCCESSFULLY", "SUCCESS")
    }

    fun startCheatsWarningTimer(targetName: String, targetUid: String) {
        viewModelScope.launch {
            // Emulate "some days" of recovery with a short interactive delayed timer (e.g. 15 seconds)
            delay(15000)
            _uiState.update { state ->
                state.copy(
                    showCheatsEmailWarningDialog = true,
                    activeCheatsWarningName = targetName,
                    activeCheatsWarningUid = targetUid
                )
            }
        }
    }

    fun dismissCheatsWarningDialog() {
        _uiState.update { it.copy(showCheatsEmailWarningDialog = false) }
    }

    fun changeGlassIntensity(intensity: GlassIntensity) {
        _uiState.update { it.copy(glassIntensity = intensity) }
    }

    fun changeAnimationProfile(profile: AnimationProfile) {
        _uiState.update { it.copy(animationProfile = profile) }
    }

    fun getActiveThemeMode(mode: AppThemeMode, isSystemDark: Boolean): AppThemeMode {
        return when (mode) {
            AppThemeMode.AUTO -> {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val isNight = hour < 6 || hour >= 18
                if (isNight || isSystemDark) AppThemeMode.TITANIUM else AppThemeMode.LIGHT
            }
            else -> mode
        }
    }

    fun setThemeMode(mode: AppThemeMode, isSystemDark: Boolean = true) {
        val resolved = getActiveThemeMode(mode, isSystemDark)
        val isDark = resolved == AppThemeMode.DARK
        _uiState.update {
            it.copy(
                themeMode = mode,
                isDarkTheme = isDark,
                themeStyle = if (resolved == AppThemeMode.LIGHT) AppThemeStyle.GRAY else AppThemeStyle.DARK
            )
        }
    }

    fun updateIsDarkTheme(isDark: Boolean) {
        if (_uiState.value.isDarkTheme != isDark) {
            _uiState.update { it.copy(isDarkTheme = isDark) }
        }
    }

    fun toggleTheme() {
        val nextMode = when (_uiState.value.themeMode) {
            AppThemeMode.LIGHT -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.TITANIUM
            AppThemeMode.TITANIUM -> AppThemeMode.AUTO
            AppThemeMode.AUTO -> AppThemeMode.LIGHT
        }
        setThemeMode(nextMode)
    }

    fun setThemeStyle(style: AppThemeStyle) {
        _uiState.update {
            it.copy(
                themeStyle = style,
                themeMode = if (style == AppThemeStyle.DARK) AppThemeMode.DARK else AppThemeMode.TITANIUM
            )
        }
    }

    fun setTheme(isDark: Boolean) {
        setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _uiState.update { it.copy(hasCompletedOnboarding = completed) }
    }

    fun setAsGuest(guest: Boolean) {
        _uiState.update { it.copy(isGuest = guest, isLoggedIn = true) }
    }

    fun setInternetAvailable(available: Boolean) {
        _uiState.update { it.copy(isInternetAvailable = available) }
    }

    fun initializeCreditsAndClaimInfo(credits: Int, streak: Int, lastClaimDate: String, adHistory: List<String>) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val claimedToday = (lastClaimDate == today)
        _uiState.update { 
            it.copy(
                userCredits = credits,
                streakDays = streak,
                lastClaimDate = lastClaimDate,
                hasClaimedToday = claimedToday,
                adHistory = adHistory
            )
        }
    }

    fun claimDailyBox() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        if (_uiState.value.lastClaimDate == today) return

        val possibleRewards = listOf(10, 25, 50, 100)
        val reward = possibleRewards.random()
        _uiState.update { state ->
            val newStreak = state.streakDays + 1
            val bonusStreakCredits = when {
                newStreak % 7 == 0 -> 50 // Weekly bonus credit
                newStreak % 3 == 0 -> 20
                else -> 0
            }
            val totalCreditsAdded = reward + bonusStreakCredits
            val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val logEntry = "Daily Claim Gained +$totalCreditsAdded Credits [$timeString]"
            state.copy(
                userCredits = state.userCredits + totalCreditsAdded,
                hasClaimedToday = true,
                lastClaimDate = today,
                streakDays = newStreak,
                adHistory = state.adHistory + logEntry,
                showAdRewardAnimation = true
            )
        }
        syncCreditsToFirebase()
    }

    fun watchSponsoredAd() {
        val rewardCredits = 25
        val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "Watched Sponsored Ad Gained +$rewardCredits Credits [$timeString]"
        _uiState.update { state ->
            state.copy(
                userCredits = state.userCredits + rewardCredits,
                adHistory = state.adHistory + logEntry,
                showAdRewardAnimation = true
            )
        }
        syncCreditsToFirebase()
    }

    fun grantDemoCredits(amount: Int) {
        val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "Demo Grant Added +$amount Credits [$timeString]"
        _uiState.update { state ->
            state.copy(
                userCredits = state.userCredits + amount,
                adHistory = state.adHistory + logEntry,
                showAdRewardAnimation = true
            )
        }
        syncCreditsToFirebase()
    }

    fun syncCreditsToFirebase() {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        if (state.isGuest) return

        val updates = hashMapOf<String, Any>(
            "userCredits" to state.userCredits,
            "hasClaimedOneTimeReward" to state.hasClaimedOneTimeReward,
            "redeemedPromoCodes" to state.redeemedPromoCodes
        )
        try {
            FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                .update(updates)
                .addOnFailureListener {
                    try {
                        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        Log.e("ReclaimViewModel", "Nested firestore credits sync failure: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore credits sync failure: ${e.message}")
        }

        viewModelScope.launch {
            try {
                val currentProfile = repository.rtdbGetProfile(uid)?.toMutableMap() ?: mutableMapOf()
                currentProfile["userCredits"] = state.userCredits
                currentProfile["hasClaimedOneTimeReward"] = state.hasClaimedOneTimeReward
                currentProfile["redeemedPromoCodes"] = state.redeemedPromoCodes
                repository.rtdbSaveProfile(uid, currentProfile)
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "RTDB sync error: ${e.message}")
            }
        }
    }

    fun claimOneTimeReward(): Boolean {
        val state = _uiState.value
        if (state.isGuest) {
            showToast("RESTRICTED: PLEASE REGISTER OR SIGN IN TO CLAIM REWARDS!", "WARNING")
            return false
        }
        if (state.hasClaimedOneTimeReward) {
            showToast("ERROR: REWARD ALREADY CLAIMED ON THIS ACCOUNT!", "ERROR")
            return false
        }
        _uiState.update {
            it.copy(
                userCredits = it.userCredits + 100,
                hasClaimedOneTimeReward = true
            )
        }
        syncCreditsToFirebase()
        showToast("SUCCESS: +100 SECURE COINS CLAIMED SUCCESSFULLY!", "SUCCESS")
        return true
    }

    fun redeemPromoCode(code: String): Boolean {
        val uppercaseCode = code.trim().uppercase()
        if (uppercaseCode.isBlank()) {
            showToast("PROMO CODE CANNOT BE EMPTY!", "ERROR")
            return false
        }
        val state = _uiState.value
        if (state.isGuest) {
            showToast("RESTRICTED: PLEASE SIGN IN TO REDEEM PROMO CODES!", "WARNING")
            return false
        }
        if (uppercaseCode in state.redeemedPromoCodes) {
            showToast("ERROR: CODE '$uppercaseCode' ALREADY REDEEMED BY YOU!", "ERROR")
            return false
        }
        val validCodes = listOf("RECLAIM70", "OSHAG70", "FREE70", "BONUS70", "RECLAIM", "OSHAG", "CHEATS")
        val isValid = uppercaseCode in validCodes || uppercaseCode.endsWith("70")
        if (isValid) {
            _uiState.update {
                it.copy(
                    userCredits = it.userCredits + 70,
                    redeemedPromoCodes = it.redeemedPromoCodes + uppercaseCode
                )
            }
            syncCreditsToFirebase()
            showToast("SUCCESS: PROMO CODE '$uppercaseCode' REDEEMED! +70 DETAILS CREDITS!", "SUCCESS")
            return true
        } else {
            showToast("INVALID PROMO CODE, PLEASE TRY AGAIN.", "ERROR")
            return false
        }
    }

    fun refreshProbability() {
        if (_uiState.value.isProbabilityRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbabilityRefreshing = true) }
            delay(1500)
            val newProb = (1..50).random()
            _uiState.update { state ->
                val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                state.copy(
                    currentProbability = newProb,
                    isProbabilityRefreshing = false,
                    adHistory = state.adHistory + "Bypass probability recalibration: $newProb% successful [$timeString]"
                )
            }
        }
    }

    fun toggleRecoveryBoostDialog(show: Boolean) {
        _uiState.update { it.copy(showRecoveryBoostDialog = show) }
    }

    fun boostRecoveryScore() {
        val state = _uiState.value
        if (state.isRecoveryScoreBoosting) return
        if (!spendCredits(25)) {
            togglePremiumModal(true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRecoveryScoreBoosting = true) }
            delay(2200)
            val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val logEntry = "Harden Node Sec. Boost: Current Score raised to 10.0/10 Perfect Rating [$timeString]"
            
            // Add a fresh notification item
            val newNotification = NotificationLogItem(
                id = "boost_" + System.currentTimeMillis(),
                title = "CORE SECURITY HARDENED",
                body = "Sandbox physical device certificate spoof unban layers fully armored. Security score boosted to 10/10 perfect rating.",
                timestamp = "Just Now",
                isUnread = true
            )
            
            _uiState.update {
                it.copy(
                    currentRecoveryScore = 10.0f,
                    isRecoveryScoreBoosting = false,
                    showRecoveryBoostDialog = false,
                    adHistory = it.adHistory + logEntry,
                    userNotificationLogs = listOf(newNotification) + it.userNotificationLogs
                )
            }
        }
    }

    fun togglePremiumModal(show: Boolean) {
        _uiState.update { it.copy(showPremiumModal = show) }
    }

    fun logout() {
        _uiState.update { 
            it.copy(
                isLoggedIn = false,
                googleAccountEmail = null,
                googleAccountName = null
            )
        }
    }

    fun addToAdHistory(entry: String) {
        _uiState.update { it.copy(adHistory = it.adHistory + entry) }
    }

    fun triggerAdRewardAnimation(show: Boolean) {
        _uiState.update { it.copy(showAdRewardAnimation = show) }
    }
    
    fun toggleLowCreditWarning(show: Boolean) {
        _uiState.update { it.copy(showLowCreditWarning = show) }
    }

    fun toggleAiChatDialog(show: Boolean) {
        _uiState.update { it.copy(showAiChatDialog = show) }
    }

    fun spendCredits(amount: Int): Boolean {
        var success = false
        _uiState.update { state ->
            if (state.isGuest) {
                success = false
                state
            } else if (state.isUnlimitedCredits) {
                success = true
                state
            } else if (state.subscriptionPlan == SubscriptionPlan.PREMIUM || state.subscriptionPlan == SubscriptionPlan.TRIAL) {
                if (state.userCredits >= amount) {
                    success = true
                    state.copy(userCredits = state.userCredits - amount)
                } else {
                    success = true
                    state
                }
            } else if (state.userCredits >= amount) {
                success = true
                state.copy(userCredits = state.userCredits - amount)
            } else {
                success = false
                state
            }
        }
        if (success) {
            syncCreditsToFirebase()
        }
        return success
    }

    fun purchasePremiumPlan() {
        _uiState.update { it.copy(subscriptionPlan = SubscriptionPlan.PREMIUM) }
    }

    fun cancelPremiumPlan() {
        _uiState.update { it.copy(subscriptionPlan = SubscriptionPlan.FREE) }
    }

    fun togglePreloadSandbox() {
        if (!_uiState.value.isPreloadSandboxActive) {
            if (_uiState.value.isGuest) {
                showToast("Guest Users cannot access this feature.", "ACCESS DENIED")
                return
            }
            if (!spendCredits(15)) {
                showToast("Insufficient credits. Need 15 credits.", "ERROR")
                return
            }
        }
        _uiState.update { it.copy(isPreloadSandboxActive = !it.isPreloadSandboxActive) }
    }

    fun togglePreloadMailDecoupler() {
        if (!_uiState.value.isPreloadMailDecouplerActive) {
            if (_uiState.value.isGuest) {
                showToast("Guest Users cannot access this feature.", "ACCESS DENIED")
                return
            }
            if (!spendCredits(20)) {
                showToast("Insufficient credits. Need 20 credits.", "ERROR")
                return
            }
        }
        _uiState.update { it.copy(isPreloadMailDecouplerActive = !it.isPreloadMailDecouplerActive) }
    }

    fun togglePreloadTokenSpoofer() {
        _uiState.update { it.copy(isPreloadTokenSpooferActive = !it.isPreloadTokenSpooferActive) }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQueries = query) }
    }

    fun dismissNotification(id: String) {
        _uiState.update { state ->
            state.copy(userNotificationLogs = state.userNotificationLogs.filter { it.id != id })
        }
    }

    fun markNotificationAsRead(id: String) {
        _uiState.update { state ->
            val updated = state.userNotificationLogs.map {
                if (it.id == id) it.copy(isUnread = false) else it
            }
            state.copy(userNotificationLogs = updated)
        }
    }

    fun createNewTicket(title: String) {
        if (!spendCredits(25)) {
            togglePremiumModal(true)
            return
        }
        val newId = "#" + (10000 + (Math.random() * 90000).toInt())
        val newHash = java.util.UUID.randomUUID().toString().take(12).uppercase()
        val newTicket = TicketItem(
            id = newId,
            title = title,
            hash = "REC-HASH-$newHash",
            status = "Under Review",
            progress = 0.15f
        )
        _uiState.update {
            it.copy(supportTickets = listOf(newTicket) + it.supportTickets)
        }
    }

    fun updateTargetProfile(name: String, uid: String, reason: String, profileType: String) {
        _uiState.update {
            it.copy(
                targetPlayerName = name,
                targetPlayerUid = uid,
                targetBanReason = reason,
                targetProfileType = profileType
            )
        }
    }

    fun accelerateTicket(ticketId: String) {
        _uiState.update { state ->
            val updated = state.supportTickets.map { ticket ->
                if (ticket.id == ticketId) {
                    val newProgress = (ticket.progress + 0.15f).coerceAtMost(1.0f)
                    val newStatus = if (newProgress >= 1.0f) "Bypassed" else "Active"
                    ticket.copy(progress = newProgress, status = newStatus)
                } else ticket
            }
            state.copy(supportTickets = updated)
        }
        
        val targetTicket = _uiState.value.supportTickets.firstOrNull { it.id == ticketId }
        targetTicket?.let {
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        userNotificationLogs = listOf(
                            NotificationLogItem(
                                id = java.util.UUID.randomUUID().toString(),
                                title = "Telemetry Restored ${it.id}",
                                body = "Successfully injected unban acceleration payload on ticket: ${it.title}.",
                                timestamp = "Just now",
                                isUnread = true
                            )
                        ) + state.userNotificationLogs
                    )
                }
            }
        }
    }

    fun updateProfileLogoUri(uriString: String) {
        _uiState.update { it.copy(profileLogoUri = uriString) }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    userNotificationLogs = listOf(
                        NotificationLogItem(
                            id = java.util.UUID.randomUUID().toString(),
                            title = "Profile Logo Synced",
                            body = "Successfully uploaded custom player profile logo.",
                            timestamp = "Just now",
                            isUnread = true
                        )
                    ) + state.userNotificationLogs
                )
            }
        }
    }

    fun sendManualEmailAppeal(recipient: String, subject: String, body: String) {
        if (!spendCredits(25)) {
            togglePremiumModal(true)
            return
        }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    userNotificationLogs = listOf(
                        NotificationLogItem(
                            id = java.util.UUID.randomUUID().toString(),
                            title = "Email Appeal Transmitted",
                            body = "Dispatched recovery details containing credentials payload to $recipient successfully.",
                            timestamp = "Just now",
                            isUnread = true
                        )
                    ) + state.userNotificationLogs
                )
            }
        }
    }

    fun triggerProgressRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshPercentage = 0) }
            for (p in listOf(15, 32, 54, 78, 100)) {
                delay(120)
                _uiState.update { it.copy(refreshPercentage = p) }
            }
            try {
                val sliders = repository.fetchSupabaseSliders()
                val activities = repository.fetchFirebaseLiveActivity()
                _uiState.update {
                    it.copy(
                        supabaseSliders = if (sliders.isNotEmpty()) sliders else defaultSliders,
                        firebaseActivities = if (activities.isNotEmpty()) activities else defaultActivities,
                        isRefreshing = false,
                        refreshPercentage = 100
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        supabaseSliders = defaultSliders,
                        firebaseActivities = defaultActivities,
                        isRefreshing = false,
                        refreshPercentage = 100
                    )
                }
            }
        }
    }

    fun googleLogin(email: String, name: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSplashLoading = true, 
                    splashProgress = 0.0f,
                    splashTaskName = "Initializing Firebase Authentication container..."
                )
            }
            
            val tasks = listOf(
                "Authenticating with Google Play Identity Services..." to 0.15f,
                "Retrieving Google ID Token for individual account sync..." to 0.35f,
                "Establishing secure connection with Cloud REST servers..." to 0.55f,
                "Verifying user profile mapping with secure database..." to 0.75f,
                "Syncing account balances and previous unban tickets..." to 0.90f,
                "Handshake valid. Welcome to your original Terminal!" to 1.0f
            )
            
            for ((task, progress) in tasks) {
                delay(650)
                _uiState.update { it.copy(splashProgress = progress, splashTaskName = task) }
            }
            
            // Log for actual Firebase implementation verification
            android.util.Log.d("FirebaseAuth", "signInWithCredential callback received successfully: CurrentUser(email=$email, name=$name)")
            
            delay(300)
            
            _uiState.update { 
                it.copy(
                    isLoggedIn = true,
                    isSplashLoading = false,
                    googleAccountEmail = email,
                    googleAccountName = name
                )
            }
        }
    }

    fun handleLogin(email: String, name: String, uid: String, isGuest: Boolean) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSplashLoading = true, 
                    splashProgress = 0.0f,
                    splashTaskName = "Authenticating..."
                )
            }
            
            // Simulation
            delay(1500)
            
            _uiState.update { 
                it.copy(
                    isLoggedIn = true,
                    isGuest = isGuest,
                    isSplashLoading = false,
                    googleAccountEmail = email,
                    googleAccountName = name,
                    targetPlayerUid = uid
                )
            }
        }
    }

    fun autoLoginWithSplash(email: String, name: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSplashLoading = true, 
                    splashProgress = 0.0f,
                    splashTaskName = "Initializing secure offline cache..."
                )
            }
            
            val tasks = listOf(
                "Locating previously saved login credentials..." to 0.20f,
                "Bypassing secure Google play-services session lock..." to 0.45f,
                "Validating previous Supabase endpoint sync state..." to 0.70f,
                "Preparing secure home database dashboard..." to 0.90f,
                "Handshake valid. Session recovery complete!" to 1.0f
            )
            
            for ((task, progress) in tasks) {
                delay(500)
                _uiState.update { it.copy(splashProgress = progress, splashTaskName = task) }
            }
            
            delay(250)
            
            _uiState.update { 
                it.copy(
                    isLoggedIn = true,
                    isSplashLoading = false,
                    googleAccountEmail = email,
                    googleAccountName = name
                )
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsgId = java.util.UUID.randomUUID().toString()
        val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val userMsg = ChatMessage(userMsgId, "You", text, true, currentTime)
        val stateSnapshot = _uiState.value
        
        _uiState.update { 
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isChatLoading = true
            )
        }
        
        viewModelScope.launch {
            val systemPrompt = """
                You are the Core AI unban specialist advisor for the Reclaim Security App.
                Respond with a highly skilled, direct, elite operational operative tone.
                Current Target Info: Name: ${stateSnapshot.targetPlayerName}, UID: ${stateSnapshot.targetPlayerUid}, Ban Reason: ${stateSnapshot.targetBanReason}.
                DO NOT USE any formatting like markdown stars (*), dashes (-), bullet points, or newlines/spacing. Keep it as pure text in a single paragraph or clean text formatting.
                Do NOT use generic fallback dummy data. Be proactive and suggest actions to unban or troubleshoot.
            """.trimIndent()
            
            val responseText = com.example.data.GeminiClient.callGemini(systemPrompt, text)
            
            val responseTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val botMsgId = java.util.UUID.randomUUID().toString()
            val botMsg = ChatMessage(botMsgId, "AI Specialist", responseText, false, responseTime)
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + botMsg,
                    isChatLoading = false
                )
            }
        }
    }

    fun updateAiQuery(query: String) {
        _uiState.update { it.copy(aiQuery = query) }
    }

    fun askAgentVance() {
        val query = _uiState.value.aiQuery
        val state = _uiState.value
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, vanceResponse = "Agent Vance is analyzing server sandbox files...") }
            
            val systemPrompt = """
                You are Agent Vance - PUBG Mobile High-Value Security Sandbox Bypass Specialist.
                Adopt a highly skilled, direct, elite operational operative tone. Use jargon like 'MIME headers', 'firewall sandbox signature', 'recovery payload', and 'SMTP node verification'.
                Answer the user's question regarding their current target: Name: ${state.targetPlayerName}, UID: ${state.targetPlayerUid}, Ban Reason: ${state.targetBanReason}.
                Provide specific visual, helpful, and highly detailed unban-manual unblocking steps.
            """.trimIndent()
            
            val response = com.example.data.GeminiClient.callGemini(systemPrompt, query)
            _uiState.update { it.copy(isAiLoading = false, vanceResponse = response) }
        }
    }

    fun askAgentMarcus() {
        val query = _uiState.value.aiQuery
        val state = _uiState.value
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, marcusResponse = "Agent Marcus is parsing Supabase schema logs...") }
            
            val systemPrompt = """
                You are Agent Marcus - Supabase REST API & Unified Database Synchronization Custodian.
                Adopt a highly technical backend coder, systemic, precise db developer tone. Use jargon like 'Supabase REST Endpoint', 'user_sync mapping key', 'JSON schema tables', 'Firebase Provider Tokens', and 'API dispatch latency'.
                Answer the user's question regarding their current target: Name: ${state.targetPlayerName}, UID: ${state.targetPlayerUid}, Ban Reason: ${state.targetBanReason}.
                Explain specifically how the data syncs, connects, and keeps status alive.
            """.trimIndent()
            
            val response = com.example.data.GeminiClient.callGemini(systemPrompt, query)
            _uiState.update { it.copy(isAiLoading = false, marcusResponse = response) }
        }
    }



    fun checkUserSession(context: android.content.Context) {
                val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: "bypass@reclaim.com"
            val name = currentUser.displayName ?: email.substringBefore("@")
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    isGuest = false,
                    googleAccountEmail = email,
                    googleAccountName = name
                )
            }
            val handleSessionFallback = {
                val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                val pName = prefs.getString("saved_playerName", "") ?: ""
                val pUid = prefs.getString("saved_playerUid", "") ?: ""
                _uiState.update {
                    it.copy(
                        targetPlayerName = pName,
                        targetPlayerUid = pUid,
                        hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                        hasCompletedOnboarding = pName.isNotEmpty() && pUid.isNotEmpty()
                    )
                }
            }

            // Parallel RTDB Fetch backup
            viewModelScope.launch {
                try {
                    val rtdbProf = repository.rtdbGetProfile(currentUser.uid)
                    if (rtdbProf != null) {
                        val pName = rtdbProf["playerName"] as? String ?: ""
                        val pUid = rtdbProf["playerUid"] as? String ?: ""
                        val pLogo = rtdbProf["profileLogoUri"] as? String ?: ""
                        val creditsVal = (rtdbProf["userCredits"] as? Number)?.toInt() ?: 50
                        val claimedOneTime = rtdbProf["hasClaimedOneTimeReward"] as? Boolean ?: false
                        val redeemedList = rtdbProf["redeemedPromoCodes"] as? List<Any> ?: emptyList()
                        val redeemed = redeemedList.map { it.toString() }

                        _uiState.update {
                            it.copy(
                                targetPlayerName = pName,
                                targetPlayerUid = pUid,
                                profileLogoUri = pLogo.ifBlank { null },
                                userCredits = creditsVal,
                                hasClaimedOneTimeReward = claimedOneTime,
                                redeemedPromoCodes = redeemed,
                                hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                                hasCompletedOnboarding = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "RTDB checkUserSession fetch error: ${e.message}")
                }
            }

            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("reclaim_profiles").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val pName = doc.getString("playerName") ?: ""
                            val pUid = doc.getString("playerUid") ?: ""
                            val pLogo = doc.getString("profileLogoUri") ?: ""
                            val creditsVal = doc.getLong("userCredits")?.toInt() ?: doc.getDouble("userCredits")?.toInt() ?: 50
                            val claimedOneTime = doc.getBoolean("hasClaimedOneTimeReward") ?: false
                            val redeemedList = doc.get("redeemedPromoCodes") as? List<Any> ?: emptyList()
                            val redeemed = redeemedList.map { it.toString() }
                            
                            _uiState.update {
                                it.copy(
                                    targetPlayerName = pName,
                                    targetPlayerUid = pUid,
                                    profileLogoUri = pLogo.ifBlank { null },
                                    userCredits = creditsVal,
                                    hasClaimedOneTimeReward = claimedOneTime,
                                    redeemedPromoCodes = redeemed,
                                    hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                                    hasCompletedOnboarding = true
                                )
                            }
                        } else {
                            _uiState.update { it.copy(hasCompletedProfileSetup = false) }
                        }
                    }
                    .addOnFailureListener {
                        handleSessionFallback()
                    }
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "Firestore failed to initialize in checkUserSession: ${e.message}")
                handleSessionFallback()
            }
        } else {
            val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
            val wasLoggedIn = prefs.getBoolean("is_logged_in", false)
            val isGuest = prefs.getBoolean("is_guest", false)
            if (wasLoggedIn) {
                if (isGuest) {
                    val pName = prefs.getString("saved_playerName", "Guest Player") ?: "Guest Player"
                    val pUid = prefs.getString("saved_playerUid", "882419") ?: "882419"
                    _uiState.update {
                        it.copy(
                            isLoggedIn = true,
                            isGuest = true,
                            googleAccountEmail = "guest@reclaim.com",
                            googleAccountName = "Guest User",
                            targetPlayerName = pName,
                            targetPlayerUid = pUid,
                            hasCompletedProfileSetup = true,
                            hasCompletedOnboarding = true
                        )
                    }
                } else {
                    val pEmail = prefs.getString("logged_in_email", "bypass@reclaim.com") ?: "bypass@reclaim.com"
                    val pName = prefs.getString("logged_in_name", "Bypass Guest Player") ?: "Bypass Guest Player"
                    val tName = prefs.getString("saved_playerName", "") ?: ""
                    val tUid = prefs.getString("saved_playerUid", "") ?: ""
                    _uiState.update {
                        it.copy(
                            isLoggedIn = true,
                            isGuest = false,
                            googleAccountEmail = pEmail,
                            googleAccountName = pName,
                            targetPlayerName = tName,
                            targetPlayerUid = tUid,
                            hasCompletedProfileSetup = tName.isNotEmpty() && tUid.isNotEmpty(),
                            hasCompletedOnboarding = tName.isNotEmpty() && tUid.isNotEmpty()
                        )
                    }
                }
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true, authError = null) }
        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid ?: ""
                _uiState.update {
                    it.copy(
                        isAuthOperationLoading = false,
                        isLoggedIn = true,
                        isGuest = false,
                        googleAccountEmail = email,
                        googleAccountName = email.substringBefore("@")
                    )
                }
                
                val profile = hashMapOf(
                    "userId" to uid,
                    "email" to email,
                    "playerName" to "",
                    "playerUid" to "",
                    "loginMethod" to "email",
                    "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                )
                FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid).set(profile)
                
                // Write to RTDB in parallel
                viewModelScope.launch {
                    repository.rtdbSaveProfile(uid, profile)
                }
                
                val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("is_logged_in", true)
                    .putBoolean("is_guest", false)
                    .putString("logged_in_email", email)
                    .putString("logged_in_name", email.substringBefore("@"))
                    .apply()
                
                onSuccess()
            }
            .addOnFailureListener { exc ->
                _uiState.update {
                    it.copy(
                        isAuthOperationLoading = false,
                        authError = exc.localizedMessage ?: "Registration failed."
                    )
                }
            }
    }

    fun signInWithEmail(email: String, password: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true, authError = null) }
        val auth = FirebaseAuth.getInstance()
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid ?: ""
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        isGuest = false,
                        googleAccountEmail = email,
                        googleAccountName = user?.displayName ?: email.substringBefore("@")
                    )
                }
                
                FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        _uiState.update { it.copy(isAuthOperationLoading = false) }
                        if (doc.exists()) {
                            val pName = doc.getString("playerName") ?: ""
                            val pUid = doc.getString("playerUid") ?: ""
                            val pLogo = doc.getString("profileLogoUri") ?: ""
                            val creditsVal = doc.getLong("userCredits")?.toInt() ?: doc.getDouble("userCredits")?.toInt() ?: 50
                            val claimedOneTime = doc.getBoolean("hasClaimedOneTimeReward") ?: false
                            val redeemedList = doc.get("redeemedPromoCodes") as? List<Any> ?: emptyList()
                            val redeemed = redeemedList.map { it.toString() }

                            _uiState.update {
                                it.copy(
                                    targetPlayerName = pName,
                                    targetPlayerUid = pUid,
                                    profileLogoUri = pLogo.ifBlank { null },
                                    userCredits = creditsVal,
                                    hasClaimedOneTimeReward = claimedOneTime,
                                    redeemedPromoCodes = redeemed,
                                    hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                                    hasCompletedOnboarding = true
                                )
                            }
                            
                            val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("is_logged_in", true)
                                .putBoolean("is_guest", false)
                                .putString("saved_playerName", pName)
                                .putString("saved_playerUid", pUid)
                                .apply()
                        } else {
                            // Fallback to RTDB
                            viewModelScope.launch {
                                val rtdbProf = repository.rtdbGetProfile(uid)
                                if (rtdbProf != null) {
                                    val pName = rtdbProf["playerName"] as? String ?: ""
                                    val pUid = rtdbProf["playerUid"] as? String ?: ""
                                    val pLogo = rtdbProf["profileLogoUri"] as? String ?: ""
                                    _uiState.update {
                                        it.copy(
                                            targetPlayerName = pName,
                                            targetPlayerUid = pUid,
                                            profileLogoUri = pLogo.ifBlank { null },
                                            hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                                            hasCompletedOnboarding = true
                                        )
                                    }
                                    val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putBoolean("is_guest", false)
                                        .putString("saved_playerName", pName)
                                        .putString("saved_playerUid", pUid)
                                        .apply()
                                }
                            }
                        }
                        onSuccess()
                    }
                    .addOnFailureListener {
                        _uiState.update { it.copy(isAuthOperationLoading = false) }
                        val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                        val pName = prefs.getString("saved_playerName", "") ?: ""
                        val pUid = prefs.getString("saved_playerUid", "") ?: ""
                        _uiState.update {
                            it.copy(
                                targetPlayerName = pName,
                                targetPlayerUid = pUid,
                                hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                                hasCompletedOnboarding = pName.isNotEmpty() && pUid.isNotEmpty()
                            )
                        }
                        onSuccess()
                    }
            }
            .addOnFailureListener { exc ->
                _uiState.update {
                    it.copy(
                        isAuthOperationLoading = false,
                        authError = exc.localizedMessage ?: "Invalid login credentials."
                    )
                }
            }
    }

    fun signInAsGuest(context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isGuest = true,
                googleAccountEmail = "guest@reclaim.com",
                googleAccountName = "Guest User",
                targetPlayerName = "Guest Player",
                targetPlayerUid = "882419",
                hasCompletedProfileSetup = true,
                hasCompletedOnboarding = true
            )
        }
        val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("is_guest", true)
            .putString("logged_in_email", "guest@reclaim.com")
            .putString("logged_in_name", "Guest User")
            .putString("saved_playerName", "Guest Player")
            .putString("saved_playerUid", "882419")
            .putBoolean("has_completed_onboarding_v4", true)
            .apply()
        onSuccess()
    }

    fun sendPasswordReset(email: String, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Please enter a valid email address.")
            return
        }
        _uiState.update { it.copy(isAuthOperationLoading = true, authError = null) }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                onResult(true, "Password reset link sent to $email.")
            }
            .addOnFailureListener { exc ->
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                onResult(false, exc.localizedMessage ?: "Failed to send reset link.")
            }
    }

    fun nativeGoogleSignIn(context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true, authError = null) }
        val credentialManager = androidx.credentials.CredentialManager.create(context)
        
        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("98245081693-n6k9d9k4gqgna2hv1r95t37esbcopu22.apps.googleusercontent.com")
            .setAutoSelectEnabled(true)
            .build()
            
        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
            
        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                
                if (credential is androidx.credentials.CustomCredential && credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    
                    val firebaseAuth = FirebaseAuth.getInstance()
                    val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    
                    firebaseAuth.signInWithCredential(authCredential)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user != null) {
                                val userEmail = user.email ?: "googleuser@reclaim.com"
                                val userName = user.displayName ?: userEmail.substringBefore("@")
                                finalizeGoogleAuth(userEmail, userName, context, onSuccess)
                            } else {
                                _uiState.update { it.copy(isAuthOperationLoading = false, authError = "User is null after Google Auth") }
                            }
                        }
                        .addOnFailureListener {
                            _uiState.update { state -> state.copy(isAuthOperationLoading = false, authError = it.localizedMessage) }
                        }
                } else {
                    _uiState.update { it.copy(isAuthOperationLoading = false, authError = "Unknown credential type") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAuthOperationLoading = false, authError = e.localizedMessage) }
            }
        }
    }

    fun finalizeGoogleAuth(email: String, name: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true, authError = null) }
        
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "google_${email.hashCode()}"
        
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isGuest = false,
                googleAccountEmail = email,
                googleAccountName = name
            )
        }
        
        val profile = hashMapOf(
            "userId" to uid,
            "email" to email,
            "playerName" to "",
            "playerUid" to "",
            "loginMethod" to "google",
            "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
            "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
        
        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                if (doc.exists()) {
                    val pName = doc.getString("playerName") ?: ""
                    val pUid = doc.getString("playerUid") ?: ""
                    val pLogo = doc.getString("profileLogoUri") ?: ""
                    _uiState.update {
                        it.copy(
                            targetPlayerName = pName,
                            targetPlayerUid = pUid,
                            profileLogoUri = pLogo.ifBlank { null },
                            hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                            hasCompletedOnboarding = true
                        )
                    }
                    
                    val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putBoolean("is_guest", false)
                        .putString("logged_in_email", email)
                        .putString("logged_in_name", name)
                        .putString("saved_playerName", pName)
                        .putString("saved_playerUid", pUid)
                        .apply()
                } else {
                    _uiState.update { it.copy(hasCompletedProfileSetup = false) }
                    FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid).set(profile)
                    
                    val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putBoolean("is_guest", false)
                        .putString("logged_in_email", email)
                        .putString("logged_in_name", name)
                        .apply()
                }
                onSuccess()
            }
            .addOnFailureListener {
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                val pName = prefs.getString("saved_playerName", "") ?: ""
                val pUid = prefs.getString("saved_playerUid", "") ?: ""
                
                _uiState.update {
                    it.copy(
                        targetPlayerName = pName,
                        targetPlayerUid = pUid,
                        hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                        hasCompletedOnboarding = pName.isNotEmpty() && pUid.isNotEmpty()
                    )
                }
                onSuccess()
            }
    }

    fun storePlayerProfileInFirestore(playerName: String, playerUid: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true) }
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        val email = currentUser?.email ?: _uiState.value.googleAccountEmail ?: "bypass@reclaim.com"
        val loginMethod = if (_uiState.value.isGuest) "guest" else (if (currentUser?.email != null) "email" else "google")
        
        val fallbackBlock = {
            viewModelScope.launch {
                try {
                    val profile = hashMapOf(
                        "userId" to uid,
                        "email" to email,
                        "playerName" to playerName,
                        "playerUid" to playerUid,
                        "loginMethod" to loginMethod,
                        "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                        "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    )
                    repository.rtdbSaveProfile(uid, profile)
                    _uiState.update {
                        it.copy(
                            isAuthOperationLoading = false,
                            targetPlayerName = playerName,
                            targetPlayerUid = playerUid,
                            hasCompletedProfileSetup = true,
                            hasCompletedOnboarding = true
                        )
                    }
                    val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("saved_playerName", playerName)
                        .putString("saved_playerUid", playerUid)
                        .putBoolean("is_logged_in", true)
                        .putBoolean("has_completed_onboarding_v4", true)
                        .apply()
                    android.widget.Toast.makeText(context, "Profile setup synced wirelessly & backed up successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                } catch (ex: Exception) {
                    _uiState.update { it.copy(isAuthOperationLoading = false) }
                    android.widget.Toast.makeText(context, "Failed to initialize backup: ${ex.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("linkedPubgAccounts").document(playerUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("userUid") != uid) {
                        _uiState.update { it.copy(isAuthOperationLoading = false) }
                        android.widget.Toast.makeText(context, "This UID is already linked to another account", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val mapping = hashMapOf(
                            "pubgUid" to playerUid,
                            "userUid" to uid,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("linkedPubgAccounts").document(playerUid).set(mapping)
                            .addOnSuccessListener {
                                val profile = hashMapOf(
                                    "userId" to uid,
                                    "email" to email,
                                    "playerName" to playerName,
                                    "playerUid" to playerUid,
                                    "loginMethod" to loginMethod,
                                    "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                                    "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                )
                                db.collection("reclaim_profiles").document(uid).set(profile)
                                    .addOnSuccessListener {
                                        _uiState.update {
                                            it.copy(
                                                isAuthOperationLoading = false,
                                                targetPlayerName = playerName,
                                                targetPlayerUid = playerUid,
                                                hasCompletedProfileSetup = true,
                                                hasCompletedOnboarding = true
                                            )
                                        }
                                        
                                        val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("saved_playerName", playerName)
                                            .putString("saved_playerUid", playerUid)
                                            .putBoolean("is_logged_in", true)
                                            .putBoolean("has_completed_onboarding_v4", true)
                                            .apply()
                                        
                                        onSuccess()
                                    }
                                    .addOnFailureListener {
                                        fallbackBlock()
                                    }
                            }
                            .addOnFailureListener {
                                fallbackBlock()
                            }
                    }
                }
                .addOnFailureListener {
                    fallbackBlock()
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore initialization error: ${e.message}")
            fallbackBlock()
        }
    }

    fun updatePlayerProfileInFirestore(playerName: String, playerUid: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true) }
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        
        val fallbackBlock = {
            viewModelScope.launch {
                try {
                    val rtdbProfile = mapOf(
                        "userId" to uid,
                        "email" to (currentUser?.email ?: _uiState.value.googleAccountEmail ?: "bypass@reclaim.com"),
                        "playerName" to playerName,
                        "playerUid" to playerUid,
                        "loginMethod" to "email"
                    )
                    repository.rtdbSaveProfile(uid, rtdbProfile)
                    _uiState.update {
                        it.copy(
                            isAuthOperationLoading = false,
                            targetPlayerName = playerName,
                            targetPlayerUid = playerUid,
                            hasCompletedProfileSetup = true
                        )
                    }
                    val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("saved_playerName", playerName)
                        .putString("saved_playerUid", playerUid)
                        .apply()
                    android.widget.Toast.makeText(context, "Changes synchronized locally & backed up to Realtime Database successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                } catch (ex: Exception) {
                    _uiState.update { it.copy(isAuthOperationLoading = false) }
                    android.widget.Toast.makeText(context, "Failed to update offline backup: ${ex.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("linkedPubgAccounts").document(playerUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("userUid") != uid) {
                        _uiState.update { it.copy(isAuthOperationLoading = false) }
                        android.widget.Toast.makeText(context, "This UID is already linked to another account", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val mapping = hashMapOf(
                            "pubgUid" to playerUid,
                            "userUid" to uid,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("linkedPubgAccounts").document(playerUid).set(mapping)
                            .addOnSuccessListener {
                                val updates = hashMapOf<String, Any>(
                                    "playerName" to playerName,
                                    "playerUid" to playerUid
                                )
                                // Parallel RTDB update
                                viewModelScope.launch {
                                    val rtdbProfile = mapOf(
                                        "userId" to uid,
                                        "email" to (currentUser?.email ?: _uiState.value.googleAccountEmail ?: "bypass@reclaim.com"),
                                        "playerName" to playerName,
                                        "playerUid" to playerUid,
                                        "loginMethod" to "email"
                                    )
                                    repository.rtdbSaveProfile(uid, rtdbProfile)
                                }
                                db.collection("reclaim_profiles").document(uid).update(updates)
                                    .addOnSuccessListener {
                                        _uiState.update {
                                            it.copy(
                                                isAuthOperationLoading = false,
                                                targetPlayerName = playerName,
                                                targetPlayerUid = playerUid
                                            )
                                        }
                                        val prefs = context.getSharedPreferences("reclaim_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("saved_playerName", playerName)
                                            .putString("saved_playerUid", playerUid)
                                            .apply()
                                        onSuccess()
                                    }
                                    .addOnFailureListener {
                                        fallbackBlock()
                                    }
                            }
                            .addOnFailureListener {
                                fallbackBlock()
                            }
                    }
                }
                .addOnFailureListener {
                    fallbackBlock()
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore initialization error during update: ${e.message}")
            fallbackBlock()
        }
    }

    fun startListeningToCases() {
        try {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
            
            // Parallel RTDB Fetch Fallback
            viewModelScope.launch {
                try {
                    val allCasesMap = repository.rtdbGetAllCases()
                    if (allCasesMap != null) {
                        val list = allCasesMap.values.mapNotNull { map ->
                            val id = map["id"] as? String ?: ""
                            val title = map["title"] as? String ?: ""
                            val description = map["description"] as? String ?: ""
                            val status = map["status"] as? String ?: "open"
                            val userId = map["userId"] as? String ?: ""
                            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
                            if (userId == uid) {
                                SupportCase(id, title, description, status, userId, createdAt)
                            } else null
                        }.sortedByDescending { it.createdAt }
                        _uiState.update { it.copy(supportCases = list) }
                    }
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "RTDB cases fetch error: ${e.message}")
                }
            }

            casesListener?.remove()
            casesListener = FirebaseFirestore.getInstance().collection("cases")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val id = doc.id
                            val title = doc.getString("title") ?: ""
                            val description = doc.getString("description") ?: ""
                            val status = doc.getString("status") ?: "open"
                            val userId = doc.getString("userId") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: 0L
                            SupportCase(id, title, description, status, userId, createdAt)
                        }.sortedByDescending { it.createdAt }
                        _uiState.update { it.copy(supportCases = list) }
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firebase error in startListeningToCases: ${e.message}")
        }
    }

    fun startListeningToAllCasesForAdmin() {
        try {
            // Parallel RTDB Fetch Fallback for Admin
            viewModelScope.launch {
                try {
                    val allCasesMap = repository.rtdbGetAllCases()
                    if (allCasesMap != null) {
                        val list = allCasesMap.values.mapNotNull { map ->
                            val id = map["id"] as? String ?: ""
                            val title = map["title"] as? String ?: ""
                            val description = map["description"] as? String ?: ""
                            val status = map["status"] as? String ?: "open"
                            val userId = map["userId"] as? String ?: ""
                            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
                            SupportCase(id, title, description, status, userId, createdAt)
                        }.sortedByDescending { it.createdAt }
                        _uiState.update { it.copy(supportCases = list) }
                    }
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "RTDB admin cases fetch error: ${e.message}")
                }
            }

            casesListener?.remove()
            casesListener = FirebaseFirestore.getInstance().collection("cases")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val id = doc.id
                            val title = doc.getString("title") ?: ""
                            val description = doc.getString("description") ?: ""
                            val status = doc.getString("status") ?: "open"
                            val userId = doc.getString("userId") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: 0L
                            SupportCase(id, title, description, status, userId, createdAt)
                        }.sortedByDescending { it.createdAt }
                        _uiState.update { it.copy(supportCases = list) }
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firebase error in startListeningToAllCasesForAdmin: ${e.message}")
        }
    }

    fun selectCase(case: SupportCase?) {
        _uiState.update { it.copy(activeSelectedCase = case, activeCaseMessages = emptyList()) }
        messagesListener?.remove()
        messagesListener = null
        
        if (case != null) {
            // Parallel RTDB Messages Fetch Fallback
            viewModelScope.launch {
                try {
                    val messagesMap = repository.rtdbGetCaseMessages(case.id)
                    if (messagesMap != null) {
                        val msgs = messagesMap.values.mapNotNull { map ->
                            val id = map["id"] as? String ?: ""
                            val caseId = map["caseId"] as? String ?: case.id
                            val senderId = map["senderId"] as? String ?: ""
                            val senderName = map["senderName"] as? String ?: ""
                            val msgText = map["message"] as? String ?: ""
                            val ts = (map["timestamp"] as? Number)?.toLong() ?: 0L
                            CaseMessage(id, caseId, senderId, senderName, msgText, ts)
                        }.sortedBy { it.timestamp }
                        _uiState.update { it.copy(activeCaseMessages = msgs) }
                    }
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "RTDB messages fetch error: ${e.message}")
                }
            }

            messagesListener = FirebaseFirestore.getInstance().collection("cases").document(case.id).collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                     if (error != null) {
                         return@addSnapshotListener
                     }
                     if (snapshot != null) {
                         val msgs = snapshot.documents.mapNotNull { doc ->
                             val id = doc.id
                             val caseId = doc.getString("caseId") ?: case.id
                             val senderId = doc.getString("senderId") ?: ""
                             val senderName = doc.getString("senderName") ?: ""
                             val msgText = doc.getString("message") ?: ""
                             val ts = doc.getLong("timestamp") ?: 0L
                             CaseMessage(id, caseId, senderId, senderName, msgText, ts)
                         }
                         _uiState.update { it.copy(activeCaseMessages = msgs) }
                     }
                }
        }
    }

    fun sendCaseMessage(messageText: String) {
        val activeCase = _uiState.value.activeSelectedCase ?: return
        if (messageText.isBlank()) return
        
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        val userName = auth.currentUser?.displayName ?: _uiState.value.googleAccountName ?: _uiState.value.targetPlayerName.ifBlank { "User" }
        
        val msgId = java.util.UUID.randomUUID().toString()
        val msgData = hashMapOf(
            "id" to msgId,
            "caseId" to activeCase.id,
            "senderId" to uid,
            "senderName" to userName,
            "message" to messageText,
            "timestamp" to System.currentTimeMillis()
        )
        
        try {
            FirebaseFirestore.getInstance().collection("cases").document(activeCase.id).collection("messages").document(msgId)
                .set(msgData)
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore sendCaseMessage error: ${e.message}")
        }

        // Write to RTDB in parallel
        viewModelScope.launch {
            repository.rtdbSaveCaseMessage(activeCase.id, msgId, msgData)
            val currentMsgs = _uiState.value.activeCaseMessages.toMutableList()
            currentMsgs.add(CaseMessage(msgId, activeCase.id, uid, userName, messageText, msgData["timestamp"] as Long))
            _uiState.update { it.copy(activeCaseMessages = currentMsgs) }
        }
    }

    fun sendAdminCaseMessage(caseId: String, messageText: String) {
        if (messageText.isBlank()) return
        val msgId = java.util.UUID.randomUUID().toString()
        val msgData = hashMapOf(
            "id" to msgId,
            "caseId" to caseId,
            "senderId" to "admin_reclaim",
            "senderName" to "Compliance Specialist (Admin)",
            "message" to messageText,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            FirebaseFirestore.getInstance().collection("cases").document(caseId).collection("messages").document(msgId)
                .set(msgData)
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore sendAdminCaseMessage error: ${e.message}")
        }

        // Write to RTDB in parallel
        viewModelScope.launch {
            repository.rtdbSaveCaseMessage(caseId, msgId, msgData)
        }
    }

    fun createSupportCase(title: String, description: String, context: android.content.Context, onSuccess: () -> Unit) {
        if (title.isBlank() || description.isBlank()) {
            android.widget.Toast.makeText(context, "Please enter both title and description.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        
        val caseId = java.util.UUID.randomUUID().toString()
        val caseData = hashMapOf(
            "id" to caseId,
            "title" to title,
            "description" to description,
            "status" to "open",
            "userId" to uid,
            "createdAt" to System.currentTimeMillis()
        )
        
        _uiState.update { it.copy(isLoading = true) }
        
        // Save to RTDB in parallel
        viewModelScope.launch {
            repository.rtdbSaveCase(caseId, caseData)
            val welcomeMsgId = java.util.UUID.randomUUID().toString()
            val welcomeMsg = hashMapOf(
                "id" to welcomeMsgId,
                "caseId" to caseId,
                "senderId" to "system_compliance",
                "senderName" to "Compliance Specialists",
                "message" to "Thank you for enrolling Case '${title}'. Our system architecture has parsed your digital sandbox signatures. A unban specialist will audit your case payload instructions shortly.",
                "timestamp" to System.currentTimeMillis() + 500
            )
            repository.rtdbSaveCaseMessage(caseId, welcomeMsgId, welcomeMsg)
            
            val newList = _uiState.value.supportCases.toMutableList()
            newList.add(0, SupportCase(caseId, title, description, "open", uid, caseData["createdAt"] as Long))
            _uiState.update { it.copy(supportCases = newList) }
        }

        try {
            FirebaseFirestore.getInstance().collection("cases").document(caseId)
                .set(caseData)
                .addOnSuccessListener {
                    _uiState.update { it.copy(isLoading = false) }
                    val welcomeMsgId = java.util.UUID.randomUUID().toString()
                    val welcomeMsg = hashMapOf(
                        "id" to welcomeMsgId,
                        "caseId" to caseId,
                        "senderId" to "system_compliance",
                        "senderName" to "Compliance Specialists",
                        "message" to "Thank you for enrolling Case '${title}'. Our system architecture has parsed your digital sandbox signatures. A live unban specialist will audit your case payload instructions shortly.",
                        "timestamp" to System.currentTimeMillis() + 500
                    )
                    FirebaseFirestore.getInstance().collection("cases").document(caseId).collection("messages").document(welcomeMsgId)
                        .set(welcomeMsg)
                        
                    android.widget.Toast.makeText(context, "Case Enrolled successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .addOnFailureListener {
                    _uiState.update { it.copy(isLoading = false) }
                    android.widget.Toast.makeText(context, "Case Enrolled wirelessly (Secure Sandbox backup)!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore addSupportCase error: ${e.message}")
            _uiState.update { it.copy(isLoading = false) }
            android.widget.Toast.makeText(context, "Case Enrolled wirelessly (Secure Sandbox backup)!", android.widget.Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }

    fun updateCaseStatus(caseId: String, newStatus: String, context: android.content.Context) {
        FirebaseFirestore.getInstance().collection("cases").document(caseId)
            .update("status", newStatus)
            .addOnSuccessListener {
                _uiState.update {
                    if (it.activeSelectedCase?.id == caseId) {
                        it.copy(activeSelectedCase = it.activeSelectedCase.copy(status = newStatus))
                    } else {
                        it
                    }
                }
                android.widget.Toast.makeText(context, "Status updated to $newStatus", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    fun startListeningToUpdates() {
        try {
            // Parallel RTDB Fetch Fallback for Updates
            viewModelScope.launch {
                try {
                    val updatesMap = repository.rtdbGetUpdates()
                    if (updatesMap != null) {
                        val list = updatesMap.values.mapNotNull { map ->
                            val id = map["id"] as? String ?: ""
                            val title = map["title"] as? String ?: ""
                            val description = map["description"] as? String ?: ""
                            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
                            SystemUpdate(id, title, description, timestamp)
                        }.sortedByDescending { it.timestamp }
                        _uiState.update { it.copy(systemUpdates = list) }
                    }
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "RTDB updates fetch error: ${e.message}")
                }
            }

            updatesListener?.remove()
            updatesListener = FirebaseFirestore.getInstance().collection("updates")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val id = doc.id
                            val title = doc.getString("title") ?: ""
                            val description = doc.getString("description") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            SystemUpdate(id, title, description, timestamp)
                        }
                        _uiState.update { it.copy(systemUpdates = list) }
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firebase error in startListeningToUpdates: ${e.message}")
        }
    }

    fun addSystemUpdate(title: String, description: String, context: android.content.Context) {
        val id = java.util.UUID.randomUUID().toString()
        val data = hashMapOf(
            "id" to id,
            "title" to title,
            "description" to description,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("updates").document(id)
            .set(data)

        viewModelScope.launch {
            repository.rtdbSaveUpdate(id, data)
            val newList = _uiState.value.systemUpdates.toMutableList()
            newList.add(0, SystemUpdate(id, title, description, data["timestamp"] as Long))
            _uiState.update { it.copy(systemUpdates = newList) }
            android.widget.Toast.makeText(context, "System Update added as Admin!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSystemUpdate(id: String, context: android.content.Context) {
        FirebaseFirestore.getInstance().collection("updates").document(id)
            .delete()

        viewModelScope.launch {
            repository.rtdbDeleteUpdate(id)
            _uiState.update { it.copy(systemUpdates = it.systemUpdates.filter { !it.id.equals(id) }) }
            android.widget.Toast.makeText(context, "System Update deleted as Admin!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleAdminMode(enabled: Boolean) {
        _uiState.update { it.copy(isAdminMode = enabled) }
        if (enabled) {
            startListeningToAllCasesForAdmin()
        } else {
            startListeningToCases()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        telemetryJob?.cancel()
        casesListener?.remove()
        messagesListener?.remove()
        updatesListener?.remove()
    }
}

private val defaultTickets = listOf(
    TicketItem("#99824", "Email Recovery Ticket", "REC-HASH-88A92B", "Bypassed", 1.0f),
    TicketItem("#99541", "In-Game Recovery Token Sync", "REC-HASH-477F12", "Active", 0.65f),
    TicketItem("#91102", "Advanced Shield Protection", "REC-HASH-29AC8C", "Under Review", 0.05f)
)

private val defaultNotificationLogs = listOf(
    NotificationLogItem(
        "1",
        "Support Agent Response",
        "Your unban case parameters have been updated. View visual log hashes inside the case detail panel.",
        "Just now",
        true
    ),
    NotificationLogItem(
        "2",
        "Firebase Security Warning",
        "Multiple browser authentication instances detected globally. Secondary protection loops successfully initialized.",
        "15 mins ago",
        true
    ),
    NotificationLogItem(
        "3",
        "Supabase Table Synced",
        "Table 'slider' synced 5 dynamic visual guides and guides profiles successfully.",
        "1 hour ago",
        false
    ),
    NotificationLogItem(
        "4",
        "Monthly Unban Stats",
        "June 2026 report is ready. 94.2% global unban success rate recorded.",
        "Today, 10:42 AM",
        false
    )
)

private val defaultRecoveryHistory = listOf(
    RecoveryHistoryLogItem("REC-5082", "Oshaq Playz", "5482910772", "2026-06-11 14:22", "COMPLETED", "Direct Email Layer Recovery"),
    RecoveryHistoryLogItem("REC-4903", "YouTube", "3904817290", "2026-06-12 09:15", "FAILED", "In-game Token Authentication")
)
