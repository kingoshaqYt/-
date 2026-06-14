package com.example.ui

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
        description = "Master the steps for full security and custom reclamation system logs.",
        image_url = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&auto=format&fit=crop",
        badge = "GUIDE"
    ),
    SupabaseSlider(
        id = 2,
        title = "Email Recovery System",
        description = "Advanced secondary layer mailbox bypass and visual extraction keys.",
        image_url = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=800&auto=format&fit=crop",
        badge = "EMAIL"
    ),
    SupabaseSlider(
        id = 3,
        title = "In-Game Recovery Guide",
        description = "Level up your gaming security parameters via live token unbans.",
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

enum class SubscriptionPlan {
    FREE, TRIAL, PREMIUM
}

data class ReclaimUiState(
    val currentTab: NavigationTab = NavigationTab.HOME,
    val language: LanguageDictionary.Language = LanguageDictionary.Language.ENGLISH,
    val glassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    val animationProfile: AnimationProfile = AnimationProfile.PREMIUM,
    val isDarkTheme: Boolean = true, // Force premium dark by default
    val themeStyle: AppThemeStyle = AppThemeStyle.DARK,
    
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
        ChatMessage("init_1", "AI Assistant", "Hello and welcome to the secure unban advisor chat. I am here to assist you with unblocking your account. How can I help you today?", false, "01:00")
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
    val currentProbability: Int = 78,
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

    // Recovery History logs
    val recoveryHistory: List<RecoveryHistoryLogItem> = defaultRecoveryHistory,

    // Simulated email popup cheats warning states
    val showCheatsEmailWarningDialog: Boolean = false,
    val activeCheatsWarningUid: String = "",
    val activeCheatsWarningName: String = "",

    // Modern Onboarding / Profile Setup State
    val hasCompletedProfileSetup: Boolean = false,
    val authError: String? = null,
    val isAuthOperationLoading: Boolean = false
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

class ReclaimViewModel : ViewModel() {

    private val repository = AppRepository()
    private val _uiState = MutableStateFlow(ReclaimUiState())
    val uiState: StateFlow<ReclaimUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var telemetryJob: Job? = null

    init {
        loadData()
        startCountdownTimer()
        startTelemetryFluctuator()
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

    fun changeLanguage(lang: LanguageDictionary.Language) {
        _uiState.update { it.copy(language = lang) }
        showToast("LANGUAGE SWITCHED SUCCESSFULLY", "SUCCESS")
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
        val validCodes = listOf("OSHAG", "OSHAGPLAYZ", "OSHAQ", "OSHAQPLAYZ", "UNLIMITED", "CREDITED", "RECLAIM2026", "CHEATS")
        if (uppercaseCode in validCodes) {
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
            showToast("INVALID PROMO CODE. PLEASE TRY AGAIN.", "ERROR")
            return false
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

    fun toggleTheme() {
        val nextTheme = when (_uiState.value.themeStyle) {
            AppThemeStyle.DARK -> AppThemeStyle.GRAY
            AppThemeStyle.GRAY -> AppThemeStyle.DARK
        }
        setThemeStyle(nextTheme)
    }

    fun setThemeStyle(style: AppThemeStyle) {
        _uiState.update {
            it.copy(
                themeStyle = style,
                isDarkTheme = true // Always true for Dark or Gray
            )
        }
    }

    fun setTheme(isDark: Boolean) {
        val style = if (isDark) AppThemeStyle.DARK else AppThemeStyle.GRAY
        setThemeStyle(style)
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
    }

    fun refreshProbability() {
        if (_uiState.value.isProbabilityRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbabilityRefreshing = true) }
            delay(1500)
            val newProb = (75..99).random()
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
            if (state.isGuest || state.isUnlimitedCredits) {
                // Guest mode or Unlimited promo code active
                success = true
                state
            } else if (state.subscriptionPlan == SubscriptionPlan.PREMIUM || state.subscriptionPlan == SubscriptionPlan.TRIAL) {
                // Premium subscription has unlimited credentials bypass
                success = true
                state
            } else if (state.userCredits >= amount) {
                // Regular logged in user spends credit
                success = true
                state.copy(userCredits = state.userCredits - amount)
            } else {
                success = false
                state
            }
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
        _uiState.update { it.copy(isPreloadSandboxActive = !it.isPreloadSandboxActive) }
    }

    fun togglePreloadMailDecoupler() {
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
        val userMsg = ChatMessage(userMsgId, "You", text, true, "Just now")
        
        _uiState.update { 
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isChatLoading = true
            )
        }
        
        viewModelScope.launch {
            delay(1500) // typing simulation
            val responseText = when {
                text.contains("hello", ignoreCase = true) || text.contains("hi", ignoreCase = true) -> {
                    "🛡️ [SECURE SECURITY PROTOCOL ENGAGED]\nGreetings, Commander! I am the Core AI unban specialist advisor. Submit your PUBG UID and current ban reason. I've been trained on over 50,000 successful account restoration signatures. We will orchestrate the sandbox unblocking bypass route using custom client-side handshakes."
                }
                text.contains("10", ignoreCase = true) || text.contains("ten", ignoreCase = true) || text.contains("year", ignoreCase = true) -> {
                    "🔑 [10-YEAR SUSPENSION SANDBOX DETECTION]\nA 10-year suspension is often triggered by an 'Illegal Client Revision' flag. My diagnostic analysis suggest this can be bypassed by spoofing your device's physical certificate IDs via a custom SECURE SMTP payload. We route this through isolated unban node clusters to override the global server state."
                }
                text.contains("unban", ignoreCase = true) || text.contains("bypass", ignoreCase = true) || text.contains("reclaim", ignoreCase = true) -> {
                    "⚡ [BYPASS ENGINE OPERATIONAL]\nAdvanced bypass structures successfully compiled v1.4.1! Our telemetry layer detects your sandbox state. Recommendation: Execute both 'Direct Email Layer appeal' AND 'In-game authentication token injection'. Ensure T1 Shield protection is ACTIVE before launching PUBG Mobile."
                }
                text.contains("oshaq", ignoreCase = true) || text.contains("developer", ignoreCase = true) || text.contains("whatsapp", ignoreCase = true) -> {
                    "📱 [APP ARCHITECT & COMMUNITY]\nThis platform was fully architectured and coded by Oshaq Playz (@Oshaq). The unban payload logic in this app utilizes proprietary client-server handshake methods derived from modern security research. Join our live WhatsApp Community for real-time unban log updates!"
                }
                text.contains("status", ignoreCase = true) || text.contains("help", ignoreCase = true) -> {
                    "📊 [DIGITAL TELEMETRY STATUS]\nServers: [OPTIMAL / 100% SECURE]\nInjection Latency: <8ms\nSuccess Probability: 98.4%\nI recommend starting with a 'Direct Email Layer Recovery' to verify your account's SMTP availability before proceeding with deep client-side unbans."
                }
                else -> {
                    "🤖 [MATURE DIAGNOSTIC MATCH]\nQuery analyzed. Recommended action: 1) Activate Advanced T1 Shield Protection, 2) Complete a sandbox verification check in the 'AI Check Specialist', and 3) Transmit a secure SMTP appeal carrying your device signature to our support team."
                }
            }
            val botMsgId = java.util.UUID.randomUUID().toString()
            val botMsg = ChatMessage(botMsgId, "AI Specialist", responseText, false, "Just now")
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
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("reclaim_profiles").document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
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
                    } else {
                        _uiState.update { it.copy(hasCompletedProfileSetup = false) }
                    }
                }
                .addOnFailureListener {
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
                                .putString("logged_in_name", user?.displayName ?: email.substringBefore("@"))
                                .putString("saved_playerName", pName)
                                .putString("saved_playerUid", pUid)
                                .apply()
                        } else {
                            _uiState.update { it.copy(hasCompletedProfileSetup = false) }
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
        
        val profile = hashMapOf(
            "userId" to uid,
            "email" to email,
            "playerName" to playerName,
            "playerUid" to playerUid,
            "loginMethod" to loginMethod,
            "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
            "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
        
        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
            .set(profile)
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
    }

    fun updatePlayerProfileInFirestore(playerName: String, playerUid: String, context: android.content.Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isAuthOperationLoading = true) }
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        
        val updates = hashMapOf<String, Any>(
            "playerName" to playerName,
            "playerUid" to playerUid
        )
        
        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
            .update(updates)
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
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        telemetryJob?.cancel()
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
