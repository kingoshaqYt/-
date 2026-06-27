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
    val isDarkTheme: Boolean = true, // Forced premium dark luxury theme
    val themeStyle: AppThemeStyle = AppThemeStyle.DARK,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    
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
    val adsWatchedToday: Int = 0,
    val trialTimeRemainingSeconds: Int = 86400, // 24 hours
    val trialTimeRemainingText: String = "23h 59m 54s",
    val hasSeenAutoClaimPopup: Boolean = false,
    
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
    val userBio: String = "",
    
    // Timer state for 90 days protection window
    val timerDays: Int = 89,
    val timerHours: Int = 23,
    val timerMinutes: Int = 59,
    val timerSeconds: Int = 54,
    
    // Live Service states
    val isSupabaseOnline: Boolean = true,
    val isFirebaseOnline: Boolean = true,

    // Coin Transfer & Transactions
    val coinTransferHistory: List<com.example.data.CoinTransfer> = emptyList(),
    val isPremiumUser: Boolean = false,
    val reclaimEngineDailyCalls: Int = 0,
    val lastReclaimEngineDate: String = "",

    // Referral System
    val userReferralCode: String = "",
    val totalReferrals: Int = 0,
    val referredUsers: List<String> = emptyList(),
    val hasUsedReferral: Boolean = false,
    val referredByName: String? = null,
    val isDayOne: Boolean = false,
    
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
    val userFeedbackList: List<FeedbackItem> = emptyList(),
    val recoveryTips: List<RecoveryTip> = emptyList(),
    val isAdminMode: Boolean = false,
    
    // Admin list of users and active promo codes
    val adminUsers: List<AdminUserItem> = emptyList(),
    val adminPromoCodes: List<PromoCode> = emptyList(),
    val userRank: String = "Silver Recruit",
    val userRole: String = "User",
    
    // Account Verification status
    val accountStatus: String = "Pending",
    val isVerified: Boolean = false,
    
    // Custom Popups of Reclaim status and screens
    val showRejectedPopup: Boolean = false,
    val showCompletedPopup: Boolean = false,
    val showMyRewardsScreen: Boolean = false,
    
    // Community Reports
    val communityReports: List<CommunityReport> = emptyList(),
    val searchedBanStatus: String? = null,
    val searchedBanReason: String? = null,
    val searchedAppealAvailable: Boolean = false,
    
    // Chat System
    val isChatSuspended: Boolean = false,
    val isDeviceBanned: Boolean = false,
    val chatConnections: List<ChatConnection> = emptyList(),
    val chatConnectionRequests: List<ChatConnectionRequest> = emptyList(),
    val activeDirectChatMessages: List<DirectChatMessage> = emptyList(),
    val adminPendingChatRequests: List<ChatConnectionRequest> = emptyList(),
    val searchedChatUser: AdminUserItem? = null,
    val chatDirectory: List<AdminUserItem> = emptyList(),
    val activeChatConnectionId: String? = null,

    // Live Popups
    val liveAdminPopup: LiveAdminPopup? = null,

    // Custom Admin Settings & Features
    val forceUpdateApp: Boolean = false,
    val optionalUpdateApp: Boolean = false,
    val updateApkUrl: String = "https://example.com/latest.apk",
    val updateVerName: String = "1.1.0",
    val updateVerCode: Int = 11,
    val updateChangelog: String = "Glassmorphism UI redesign, improved speed, and security handshakes.",
    val lastReceivedNotification: Pair<String, String>? = null,
    val costRecoveryScoreBoost: Int = 2500,
    val costPreloadSandbox: Int = 1500,
    val costPreloadMailDecoupler: Int = 2000,
    val costCreateNewTicket: Int = 2500,
    val costManualEmailAppeal: Int = 2500,
    val adminTimerSeconds: Int = 120,
    val isAdminTimerRunning: Boolean = false
)

data class LiveAdminPopup(
    val title: String,
    val message: String,
    val targetAudience: String,
    val timestamp: Long
)

data class CommunityReport(
    val reportId: String = "",
    val uid: String = "",
    val banReason: String = "",
    val status: String = "Pending Review", // "Pending Review", "Verified", "Rejected"
    val isPremiumVerified: Boolean = false,
    val submittedBy: String = "",
    val timestamp: Long = 0L
)

data class ChatConnectionRequest(
    val id: String = "",
    val senderUid: String = "",
    val senderPlayerId: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val receiverPlayerId: String = "",
    val receiverName: String = "",
    val status: String = "pending_receiver", // "pending_receiver", "pending_admin", "approved", "rejected"
    val timestamp: Long = 0L
)

data class DirectChatMessage(
    val id: String = "",
    val connectionId: String = "",
    val senderUid: String = "",
    val content: String = "",
    val containsLink: Boolean = false,
    val timestamp: Long = 0L
)

data class ChatConnection(
    val id: String = "",
    val user1Uid: String = "",
    val user2Uid: String = "",
    val user1Name: String = "",
    val user2Name: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L
)

enum class NavigationTab {
    HOME, RECOVERY, TOOLS, NOTIFICATIONS, PROFILE, CHAT
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

data class FeedbackItem(
    val id: String = "",
    val type: String = "Comment",
    val email: String = "",
    val text: String = "",
    val rating: Int = 5,
    val timestamp: Long = 0L,
    val isApproved: Boolean = true
)

data class RecoveryTip(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "General Check",
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
    private var profilesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var promoCodesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var userProfileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var feedbackListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var tipsListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        loadData()
        startCountdownTimer()
        startTelemetryFluctuator()
        startListeningToUpdates()
        startListeningToPopups()
        startListeningToReports()
        startListeningToFeedback()
        startListeningToTips()
        seedAdminsCollection()
        setupFCM()
        startListeningToUserNotificationLogs()
    }

    fun updateUserBio(bio: String) {
        _uiState.update { it.copy(userBio = bio) }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("reclaim_profiles")
            .document(uid).update("bio", bio)
    }

    fun startListeningToUserNotificationLogs() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("user_notification_logs")
            .whereEqualTo("uid", uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val title = doc.getString("title") ?: ""
                        val body = doc.getString("body") ?: ""
                        val timestamp = doc.getLong("timestamp")?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: ""
                        val isUnread = doc.getBoolean("isUnread") ?: false
                        NotificationLogItem(id, title, body, timestamp, isUnread)
                    }
                    _uiState.update { state -> 
                        val merged = (logs + state.userNotificationLogs).distinctBy { it.id }
                        state.copy(userNotificationLogs = merged)
                    }
                }
            }
    }
    
    private fun setupFCM() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all")
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM", "FCM Token: ${task.result}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReclaimViewModel", "FCM Setup failed: ${e.message}")
        }
    }

    fun isUserAdminFallback(email: String): Boolean {
        // Fallback or quick check used before Firebase Role System fully loads
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentAuthEmail = auth.currentUser?.email ?: email
        // Real-time permission is validated in Firebase Admin Collection, this is an initial UI unlock check
        return currentAuthEmail.endsWith("@reclaimaccounts.com") ||
               currentAuthEmail.equals("oshaqplayz@gmail.com", ignoreCase = true) ||
               currentAuthEmail.equals("oshaqyt2@gmail.com", ignoreCase = true) ||
               currentAuthEmail.equals("oshaqali722@gmail.com", ignoreCase = true)
    }

    private fun startListeningToPopups() {
        FirebaseFirestore.getInstance().collection("admin_popups").document("global_live_popup")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val title = doc.getString("title") ?: return@addSnapshotListener
                    val message = doc.getString("message") ?: return@addSnapshotListener
                    val targetAudience = doc.getString("targetAudience") ?: "All Users"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    
                    val currentState = _uiState.value
                    
                    // Filter logic
                    val currentRole = currentState.userRole
                    val isMatch = targetAudience == "All Users" || targetAudience.equals(currentRole, ignoreCase = true)
                    
                    if (isMatch) {
                        // Check if popup is recent (less than 5 minutes old) to prevent stale popups on launch
                        if (System.currentTimeMillis() - timestamp < 300000) {
                            _uiState.update { 
                                it.copy(liveAdminPopup = LiveAdminPopup(title, message, targetAudience, timestamp)) 
                            }
                        }
                    }
                }
            }
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
                if (isSystemDark) AppThemeMode.DARK else AppThemeMode.LIGHT
            }
            else -> mode
        }
    }

    fun setThemeMode(mode: AppThemeMode, isSystemDark: Boolean = true) {
        _uiState.update {
            it.copy(
                themeMode = AppThemeMode.DARK,
                isDarkTheme = true,
                themeStyle = AppThemeStyle.DARK
            )
        }
    }

    fun updateIsDarkTheme(isDark: Boolean) {
        if (!_uiState.value.isDarkTheme) {
            _uiState.update { it.copy(isDarkTheme = true) }
        }
    }

    fun toggleTheme(isSystemDark: Boolean = false) {
        setThemeMode(AppThemeMode.DARK, isSystemDark)
    }

    fun setThemeStyle(style: AppThemeStyle) {
        _uiState.update {
            it.copy(
                themeStyle = AppThemeStyle.DARK,
                themeMode = AppThemeMode.DARK
            )
        }
    }

    fun setTheme(isDark: Boolean) {
        setThemeMode(AppThemeMode.DARK)
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
        if (_uiState.value.accountStatus == "Pending Verification") {
            showToast("Rewards are restricted until your account is verified.", "WARNING")
            return
        }
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

        // Trigger both in-app notification & system local push notification
        try {
            val act = com.example.MainActivity.instance
            if (act != null) {
                val totalAdded = reward + (if (_uiState.value.streakDays % 7 == 0) 50 else if (_uiState.value.streakDays % 3 == 0) 20 else 0)
                triggerLocalNotification(
                    act, 
                    "Daily Reward Claimed! 🎉", 
                    "Logged streak: ${_uiState.value.streakDays} days! Added +$totalAdded credits securely to your account."
                )
            }
        } catch (e: Exception) {}
    }

    fun watchSponsoredAd(rewardCredits: Int = 25) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val limit = if (_uiState.value.isDayOne) 5 else 100
        if (_uiState.value.lastClaimDate == todayStr && _uiState.value.adsWatchedToday >= limit) {
            showToast("You have reached the daily limit of $limit ads on ${if (_uiState.value.isDayOne) "Day 1 of your security layout activation" else "normal usage"}.", "LIMIT EXCEEDED")
            return
        }
        val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "Watched Sponsored Ad Gained +$rewardCredits Credits [$timeString]"
        _uiState.update { state ->
            val newAdsWatched = if (state.lastClaimDate == todayStr) state.adsWatchedToday + 1 else 1
            state.copy(
                userCredits = state.userCredits + rewardCredits,
                adsWatchedToday = newAdsWatched,
                lastClaimDate = todayStr,
                adHistory = state.adHistory + logEntry,
                showAdRewardAnimation = true
            )
        }
        syncCreditsToFirebase()
        
        // Trigger both in-app notification & system local push notification
        try {
            val act = com.example.MainActivity.instance
            if (act != null) {
                val currentLimit = if (_uiState.value.isDayOne) 5 else 100
                triggerLocalNotification(
                    act, 
                    "Ad Reward Received! 🎉", 
                    "Claimed +$rewardCredits credits (Ad limit: ${_uiState.value.adsWatchedToday}/$currentLimit today)."
                )
            }
        } catch (e: Exception) {}
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
            "redeemedPromoCodes" to state.redeemedPromoCodes,
            "adsWatchedToday" to state.adsWatchedToday,
            "lastClaimDate" to state.lastClaimDate
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
        if (state.accountStatus == "Pending Verification") {
            showToast("Rewards are restricted until your account is verified.", "WARNING")
            return false
        }
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

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return false

        FirebaseFirestore.getInstance().collection("promo_codes").document(uppercaseCode)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val reward = doc.getLong("reward")?.toInt() ?: doc.getDouble("reward")?.toInt() ?: 0
                    val expiryDateStr = doc.getString("expiryDate") ?: ""
                    val usageLimit = doc.getLong("usageLimit")?.toInt() ?: doc.getDouble("usageLimit")?.toInt() ?: 100
                    val timesRedeemed = doc.getLong("timesRedeemed")?.toInt() ?: doc.getDouble("timesRedeemed")?.toInt() ?: 0
                    val isSingleUse = doc.getBoolean("singleUse") ?: doc.getBoolean("isSingleUse") ?: false
                    val redeemedByUsers = doc.get("redeemedByUsers") as? List<Any> ?: emptyList()
                    val redeemedByUids = redeemedByUsers.map { it.toString() }

                    // Validate expiry (format YYYY-MM-DD)
                    var isExpired = false
                    if (expiryDateStr.isNotBlank()) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val expiryDate = sdf.parse(expiryDateStr)
                            if (expiryDate != null && expiryDate.before(java.util.Date())) {
                                isExpired = true
                            }
                        } catch (e: Exception) {
                            Log.e("ReclaimViewModel", "Expiry parse error: ${e.message}")
                        }
                    }

                    if (isExpired) {
                        showToast("ERROR: PROMO CODE '$uppercaseCode' HAS EXPIRED!", "ERROR")
                        return@addOnSuccessListener
                    }

                    if (timesRedeemed >= usageLimit) {
                        showToast("ERROR: PROMO CODE USAGE LIMIT REACHED!", "ERROR")
                        return@addOnSuccessListener
                    }

                    if (isSingleUse && uid in redeemedByUids) {
                        showToast("ERROR: CODE '$uppercaseCode' ALREADY REDEEMED BY YOU!", "ERROR")
                        return@addOnSuccessListener
                    }

                    if (uppercaseCode in _uiState.value.redeemedPromoCodes) {
                        showToast("ERROR: CODE '$uppercaseCode' ALREADY REDEEMED BY YOU!", "ERROR")
                        return@addOnSuccessListener
                    }

                    val updatedRedeemedList = redeemedByUids + uid
                    val promoUpdates = hashMapOf<String, Any>(
                        "timesRedeemed" to (timesRedeemed + 1),
                        "redeemedByUsers" to updatedRedeemedList
                    )

                    FirebaseFirestore.getInstance().collection("promo_codes").document(uppercaseCode)
                        .update(promoUpdates)
                        .addOnSuccessListener {
                            _uiState.update { s ->
                                s.copy(
                                    userCredits = s.userCredits + reward,
                                    redeemedPromoCodes = s.redeemedPromoCodes + uppercaseCode
                                )
                            }
                            syncCreditsToFirebase()
                            showToast("PROMO CODE '$uppercaseCode' ACCEPTED! +$reward CREDITS!", "SUCCESS")
                        }
                        .addOnFailureListener { e ->
                            showToast("Failed to redeem: ${e.message}", "ERROR")
                        }
                } else {
                    // Seed defaults if one of standard promo codes is entered
                    val defaults = mapOf(
                        "RECLAIM70" to 70,
                        "OSHAG70" to 70,
                        "FREE70" to 70,
                        "BONUS70" to 70,
                        "RECLAIM" to 50,
                        "OSHAG" to 50,
                        "CHEATS" to 150
                    )
                    if (uppercaseCode in defaults.keys) {
                        val rewardVal = defaults[uppercaseCode] ?: 70
                        val data = hashMapOf<String, Any>(
                            "code" to uppercaseCode,
                            "reward" to rewardVal,
                            "expiryDate" to "2028-12-31",
                            "usageLimit" to 1000,
                            "timesRedeemed" to 1,
                            "singleUse" to true,
                            "redeemedByUsers" to listOf(uid)
                        )
                        FirebaseFirestore.getInstance().collection("promo_codes").document(uppercaseCode)
                            .set(data)
                            .addOnSuccessListener {
                                _uiState.update { s ->
                                    s.copy(
                                        userCredits = s.userCredits + rewardVal,
                                        redeemedPromoCodes = s.redeemedPromoCodes + uppercaseCode
                                    )
                                }
                                syncCreditsToFirebase()
                                showToast("PROMO CODE '$uppercaseCode' ACCEPTED! +$rewardVal CREDITS!", "SUCCESS")
                            }
                    } else {
                        showToast("INVALID PROMO CODE, PLEASE TRY AGAIN.", "ERROR")
                    }
                }
            }
            .addOnFailureListener { e ->
                showToast("Verification failed: ${e.message}", "ERROR")
            }
        return true
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
        if (!spendCredits(state.costRecoveryScoreBoost)) {
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
        if (show && _uiState.value.accountStatus == "Pending Verification") {
            showToast("Premium features are locked until account is verified.", "WARNING")
            return
        }
        _uiState.update { it.copy(showPremiumModal = show) }
    }

    fun logout() {
        userProfileListener?.remove()
        userProfileListener = null
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
        if (_uiState.value.accountStatus == "Pending Verification") {
            showToast("Action restricted to Verified Users.", "ERROR")
            return false
        }
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
            try {
                val act = com.example.MainActivity.instance
                if (act != null) {
                    triggerLocalNotification(
                        act, 
                        "Credits Spent Securely 💎", 
                        "Deducted $amount credits. Remaining balance: ${_uiState.value.userCredits} credits."
                    )
                }
            } catch (e: Exception) {}
        }
        return success
    }

    fun canUseReclaimEngine(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return _uiState.value.let { state ->
            val isPremium = state.isPremiumUser || state.subscriptionPlan == SubscriptionPlan.PREMIUM
            val limit = if (isPremium) 5 else 2
            
            if (state.lastReclaimEngineDate != today) {
                true // limit resets today
            } else {
                state.reclaimEngineDailyCalls < limit
            }
        }
    }

    fun recordReclaimEngineUse() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.update { state ->
            if (state.lastReclaimEngineDate != today) {
                state.copy(lastReclaimEngineDate = today, reclaimEngineDailyCalls = 1)
            } else {
                state.copy(reclaimEngineDailyCalls = state.reclaimEngineDailyCalls + 1)
            }
        }
    }

    fun transferCoinsToUser(toUid: String, amount: Int, note: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("You must be logged in to transfer coins.")
            return
        }
        if (amount < 200) {
            onError("Minimum limit of 200 credits per transaction required.")
            return
        }
        val fromUid = currentUser.uid
        if (fromUid == toUid || toUid == _uiState.value.userReferralCode) {
            onError("Cannot transfer coins to yourself.")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // Find receiver first
        db.collection("reclaim_profiles").document(toUid).get().addOnSuccessListener { receiverDoc ->
            if (receiverDoc.exists()) {
                executeTransfer(fromUid, toUid, toUid, amount, note, onSuccess, onError, db)
            } else {
                // Try looking up by playerUid
                db.collection("reclaim_profiles").whereEqualTo("playerUid", toUid).get().addOnSuccessListener { queryByPlayerUid ->
                    if (!queryByPlayerUid.isEmpty) {
                        val doc = queryByPlayerUid.documents[0]
                        executeTransfer(fromUid, toUid, doc.id, amount, note, onSuccess, onError, db)
                    } else {
                        // Try looking up by referralCode
                        db.collection("reclaim_profiles").whereEqualTo("referralCode", toUid).get().addOnSuccessListener { queryByRef ->
                            if (!queryByRef.isEmpty) {
                                val doc = queryByRef.documents[0]
                                executeTransfer(fromUid, toUid, doc.id, amount, note, onSuccess, onError, db)
                            } else {
                                onError("Target User ID/Player UID not found.")
                            }
                        }.addOnFailureListener {
                            onError("Failed to lookup referral code.")
                        }
                    }
                }.addOnFailureListener {
                    onError("Failed to lookup player UID.")
                }
            }
        }.addOnFailureListener {
            onError("Failed to query target user.")
        }
    }

    private fun executeTransfer(fromUid: String, originalToUid: String, targetDocId: String, amount: Int, note: String, onSuccess: () -> Unit, onError: (String) -> Unit, db: com.google.firebase.firestore.FirebaseFirestore) {
        val senderRef = db.collection("reclaim_profiles").document(fromUid)
        val receiverRef = db.collection("reclaim_profiles").document(targetDocId)

        db.runTransaction { transaction ->
            val senderSnapshot = transaction.get(senderRef)
            if (!senderSnapshot.exists()) {
                throw Exception("Your profile does not exist.")
            }
            
            val currentCreditsObj = senderSnapshot.get("credits") ?: senderSnapshot.get("userCredits")
            val currentSenderCredits = (currentCreditsObj as? Number)?.toLong() ?: 0L
            if (currentSenderCredits < amount) {
                throw Exception("Insufficient balance. You only have $currentSenderCredits credits.")
            }

            val receiverSnapshot = transaction.get(receiverRef)
            val receiverCreditsObj = receiverSnapshot.get("credits") ?: receiverSnapshot.get("userCredits")
            val currentReceiverCredits = (receiverCreditsObj as? Number)?.toLong() ?: 0L

            // Update both credits and userCredits fields
            transaction.update(senderRef, mapOf(
                "credits" to currentSenderCredits - amount,
                "userCredits" to currentSenderCredits - amount
            ))
            transaction.update(receiverRef, mapOf(
                "credits" to currentReceiverCredits + amount,
                "userCredits" to currentReceiverCredits + amount
            ))
            
            val transferId = java.util.UUID.randomUUID().toString()
            val transferRec = hashMapOf(
                "id" to transferId,
                "fromUid" to fromUid,
                "toUid" to targetDocId,
                "amount" to amount,
                "note" to note,
                "timestamp" to System.currentTimeMillis()
            )
            transaction.set(db.collection("coin_transfers").document(transferId), transferRec)
            null
        }.addOnSuccessListener {
            // Also update RTDB
            viewModelScope.launch {
                try {
                    val receiverRtdb = repository.rtdbGetProfile(targetDocId)?.toMutableMap() ?: mutableMapOf()
                    val receiverCredits = (receiverRtdb["userCredits"] as? Number)?.toInt() ?: 0
                    receiverRtdb["userCredits"] = receiverCredits + amount
                    repository.rtdbSaveProfile(targetDocId, receiverRtdb)
                } catch (e: Exception) {
                    Log.e("ReclaimViewModel", "Failed to update RTDB for coin transfer receiver: ${e.message}")
                }
            }

            _uiState.update { state ->
                val newTx = com.example.data.CoinTransfer(
                    id = java.util.UUID.randomUUID().toString(),
                    fromUid = fromUid,
                    fromName = state.googleAccountName ?: "You",
                    toUid = targetDocId,
                    toName = "User $targetDocId",
                    amount = amount,
                    note = note,
                    timestamp = System.currentTimeMillis()
                )
                val newNotif = NotificationLogItem(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Coin Transfer",
                    body = "You sent $amount coins to User $targetDocId.",
                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    isUnread = true
                )
                state.copy(
                    userCredits = kotlin.math.max(0, state.userCredits - amount),
                    coinTransferHistory = listOf(newTx) + state.coinTransferHistory,
                    userNotificationLogs = listOf(newNotif) + state.userNotificationLogs
                )
            }
            syncCreditsToFirebase()
            try {
                val act = com.example.MainActivity.instance
                if (act != null) {
                    triggerLocalNotification(
                        act, 
                        "Transfer Complete", 
                        "Successfully sent $amount credits to $originalToUid."
                    )
                }
            } catch (e: Exception) {}
            onSuccess()
        }.addOnFailureListener {
            onError(it.message ?: "Transaction failed due to network error.")
        }
    }

    fun setFallbackReferralCode(code: String) {
        _uiState.update { it.copy(userReferralCode = code) }
        viewModelScope.launch {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid
                if (uid != null && !_uiState.value.isGuest) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                        .update("referralCode", code)
                }
            } catch (e: Exception) {}
        }
    }

    fun applyReferralCode(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("Must be logged in to apply a referral code.")
            return
        }
        val uid = currentUser.uid
        if (code == _uiState.value.userReferralCode) {
            onError("Cannot use your own referral code.")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val usersRef = db.collection("reclaim_profiles")

        usersRef.whereEqualTo("referralCode", code).get().addOnSuccessListener { qs ->
            if (qs.isEmpty) {
                onError("Invalid referral code.")
                return@addOnSuccessListener
            }
            val referrerDoc = qs.documents.first()
            val referrerUid = referrerDoc.id

            db.runTransaction { transaction ->
                val myDoc = transaction.get(usersRef.document(uid))
                if (myDoc.getBoolean("hasUsedReferral") == true) {
                    throw Exception("You have already used a referral code.")
                }

                val currentReferrerCredits = (referrerDoc.get("credits") as? Number)?.toLong() ?: 0L
                val currentMyCredits = (myDoc.get("credits") as? Number)?.toLong() ?: 0L
                val refCount = (referrerDoc.get("totalReferrals") as? Number)?.toInt() ?: 0
                val refUsersList = (referrerDoc.get("referredUsers") as? List<String>) ?: emptyList()
                val referrerName = referrerDoc.getString("googleAccountName") ?: referrerDoc.getString("playerName") ?: "Unknown Referrer"

                transaction.set(usersRef.document(referrerUid), mapOf(
                    "credits" to currentReferrerCredits + 500L,
                    "totalReferrals" to refCount + 1,
                    "referredUsers" to (refUsersList + (myDoc.getString("googleAccountName") ?: "Unknown User"))
                ), com.google.firebase.firestore.SetOptions.merge())
                transaction.set(usersRef.document(uid), mapOf(
                    "credits" to currentMyCredits + 500L,
                    "hasUsedReferral" to true,
                    "referredByName" to referrerName
                ), com.google.firebase.firestore.SetOptions.merge())
                null
            }.addOnSuccessListener {
                _uiState.update { it.copy(userCredits = it.userCredits + 500) }
                onSuccess()
            }.addOnFailureListener {
                onError(it.message ?: "Failed to process referral code.")
            }
        }.addOnFailureListener {
            onError("Failed to query referral code.")
        }
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
            val cost = _uiState.value.costPreloadSandbox
            if (!spendCredits(cost)) {
                showToast("Insufficient credits. Need $cost credits.", "ERROR")
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
            val cost = _uiState.value.costPreloadMailDecoupler
            if (!spendCredits(cost)) {
                showToast("Insufficient credits. Need $cost credits.", "ERROR")
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

    fun dismissRejectedPopup() {
        _uiState.update { it.copy(showRejectedPopup = false) }
    }

    fun dismissCompletedPopup() {
        _uiState.update { it.copy(showCompletedPopup = false) }
    }

    fun dismissAutoClaimPopup() {
        _uiState.update { it.copy(hasSeenAutoClaimPopup = true) }
    }

    fun dismissLivePopup() {
        _uiState.update { it.copy(liveAdminPopup = null) }
    }

    fun toggleMyRewardsScreen() {
        _uiState.update { it.copy(showMyRewardsScreen = !it.showMyRewardsScreen) }
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
        if (!spendCredits(_uiState.value.costCreateNewTicket)) {
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
        if (!spendCredits(_uiState.value.costManualEmailAppeal)) {
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
            startListeningToUserProfile(currentUser.uid, context)
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
                            val creditsVal = doc.getLong("credits")?.toInt() ?: doc.getDouble("credits")?.toInt() ?: doc.getLong("userCredits")?.toInt() ?: doc.getDouble("userCredits")?.toInt() ?: 50
                            val claimedOneTime = doc.getBoolean("hasClaimedOneTimeReward") ?: false
                            val statusVal = doc.getString("status") ?: "Pending Verification"
                            val verifiedVal = doc.getBoolean("verified") ?: false
                            val rankVal = doc.getString("userRank") ?: "Silver Recruit"
                            val redeemedList = doc.get("redeemedPromoCodes") as? List<Any> ?: emptyList()
                            val redeemed = redeemedList.map { it.toString() }
                            
                            _uiState.update {
                                it.copy(
                                    targetPlayerName = pName,
                                    targetPlayerUid = pUid,
                                    profileLogoUri = pLogo.ifBlank { null },
                                    userCredits = creditsVal,
                                    accountStatus = statusVal,
                                    isVerified = verifiedVal,
                                    userRank = rankVal,
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
                            val creditsVal = doc.getLong("credits")?.toInt() ?: doc.getDouble("credits")?.toInt() ?: doc.getLong("userCredits")?.toInt() ?: doc.getDouble("userCredits")?.toInt() ?: 50
                            val claimedOneTime = doc.getBoolean("hasClaimedOneTimeReward") ?: false
                            val statusVal = doc.getString("status") ?: "Pending Verification"
                            val verifiedVal = doc.getBoolean("verified") ?: false
                            val rankVal = doc.getString("userRank") ?: "Silver Recruit"
                            val redeemedList = doc.get("redeemedPromoCodes") as? List<Any> ?: emptyList()
                            val redeemed = redeemedList.map { it.toString() }

                            _uiState.update {
                                it.copy(
                                    targetPlayerName = pName,
                                    targetPlayerUid = pUid,
                                    profileLogoUri = pLogo.ifBlank { null },
                                    userCredits = creditsVal,
                                    accountStatus = statusVal,
                                    isVerified = verifiedVal,
                                    userRank = rankVal,
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
        startListeningToUserProfile(uid, context)
        
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
                        "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        "status" to "Pending",
                        "verified" to false,
                        "credits" to 50
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
                                val myReferralCode = java.util.UUID.randomUUID().toString().take(6).uppercase()
                                val profile = hashMapOf<String, Any>(
                                    "userId" to uid,
                                    "email" to email,
                                    "playerName" to playerName,
                                    "playerUid" to playerUid,
                                    "loginMethod" to loginMethod,
                                    "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                                    "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                    "status" to "Pending",
                                    "verified" to false,
                                    "credits" to 50,
                                    "referralCode" to myReferralCode,
                                    "totalReferrals" to 0,
                                    "referredUsers" to emptyList<String>(),
                                    "hasUsedReferral" to false
                                )
                                val pPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
                                if (pPhoto.isNotEmpty()) {
                                    profile["firebasePhotoUrl"] = pPhoto
                                }
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
                                val pPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
                                if (pPhoto.isNotEmpty()) {
                                    updates["firebasePhotoUrl"] = pPhoto
                                }
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

    fun triggerLocalNotification(context: android.content.Context, title: String, body: String) {
        try {
            val channelId = "reclaim_alerts_channel"
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Reclaim System Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts regarding account restoration status changes"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to show local notification: ${e.message}")
        }
    }

    fun startListeningToUserProfile(uid: String, context: android.content.Context) {
        listenToChatConnections()
        userProfileListener?.remove()
        userProfileListener = FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val pName = snapshot.getString("playerName") ?: snapshot.getString("googleAccountName") ?: ""
                    val pUid = snapshot.getString("playerUid") ?: ""
                    val pLogo = snapshot.getString("profileLogoUri") ?: snapshot.getString("firebasePhotoUrl") ?: snapshot.getString("photoUrl") ?: ""
                    val fbPhoto = snapshot.getString("firebasePhotoUrl") ?: ""
                    if (fbPhoto.isEmpty()) {
                        val authPhoto = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: ""
                        if (authPhoto.isNotEmpty()) {
                            try {
                                FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                                    .update("firebasePhotoUrl", authPhoto)
                            } catch (e: Exception) {}
                        }
                    }
                    val creditsVal = snapshot.getLong("credits")?.toInt() ?: snapshot.getDouble("credits")?.toInt() ?: snapshot.getLong("userCredits")?.toInt() ?: snapshot.getDouble("userCredits")?.toInt() ?: 50
                    val claimedOneTime = snapshot.getBoolean("hasClaimedOneTimeReward") ?: false
                    val statusVal = snapshot.getString("status") ?: "Pending"
                    val verifiedVal = snapshot.getBoolean("verified") ?: false
                    val rankVal = snapshot.getString("userRank") ?: "Silver Recruit"
                    val roleVal = snapshot.getString("userRole") ?: "User"
                    val redeemedList = snapshot.get("redeemedPromoCodes") as? List<Any> ?: emptyList()
                    val redeemed = redeemedList.map { it.toString() }
                    
                    var refCode = snapshot.getString("referralCode") ?: ""
                    if (refCode.isBlank()) {
                        refCode = "REC" + (1000..9999).random().toString()
                        try {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reclaim_profiles").document(uid)
                                .update("referralCode", refCode)
                        } catch (e: Exception) {}
                    }
                    val refTotal = (snapshot.get("totalReferrals") as? Number)?.toInt() ?: 0
                    val refUsers = snapshot.get("referredUsers") as? List<String> ?: emptyList()
                    val usedRef = snapshot.getBoolean("hasUsedReferral") ?: false
                    val refByName = snapshot.getString("referredByName")
                    val creationDateStr = snapshot.getString("creationDate") ?: ""

                    val lastClaim = snapshot.getString("lastClaimDate") ?: ""
                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    val claimedToday = (lastClaim == todayStr)
                    val streak = snapshot.getLong("streakDays")?.toInt() ?: snapshot.getDouble("streakDays")?.toInt() ?: _uiState.value.streakDays
                    val adsWatched = snapshot.getLong("adsWatchedToday")?.toInt() ?: snapshot.getDouble("adsWatchedToday")?.toInt() ?: 0
                    val isDayOne = creationDateStr.startsWith(todayStr)
                    
                    val oldStatus = _uiState.value.accountStatus
                    val statusChanged = (oldStatus.isNotEmpty() && oldStatus != statusVal && oldStatus != "Pending Verification")
                    
                    val shouldShowRejected = (statusVal == "Rejected" && oldStatus != "Rejected")
                    val shouldShowCompleted = (statusVal == "Completed" && oldStatus != "Completed")

                    val googleAccName = snapshot.getString("googleAccountName") ?: pName
                    val googleAccEmail = snapshot.getString("googleAccountEmail") ?: _uiState.value.googleAccountEmail
                    
                    val isChatSuspended = snapshot.getBoolean("isChatSuspended") ?: false
                    val isDeviceBanned = snapshot.getBoolean("isDeviceBanned") ?: false
                    
                    if (isDeviceBanned) {
                        logout()
                        showToast("YOUR DEVICE HAS BEEN BANNED FROM USING THIS APP.", "ERROR")
                        return@addSnapshotListener
                    }

                    _uiState.update {
                        it.copy(
                            targetPlayerName = pName,
                            targetPlayerUid = pUid,
                            profileLogoUri = pLogo.ifBlank { null },
                            userCredits = creditsVal,
                            accountStatus = statusVal,
                            isVerified = verifiedVal,
                            userRank = rankVal,
                            userRole = roleVal,
                            hasClaimedOneTimeReward = claimedOneTime,
                            lastClaimDate = lastClaim,
                            hasClaimedToday = claimedToday,
                            streakDays = streak,
                            adsWatchedToday = if (claimedToday) adsWatched else 0,
                            redeemedPromoCodes = redeemed,
                            userReferralCode = refCode,
                            totalReferrals = refTotal,
                            referredUsers = refUsers,
                            hasUsedReferral = usedRef,
                            referredByName = refByName,
                            isDayOne = isDayOne,
                            hasCompletedProfileSetup = pName.isNotEmpty() && pUid.isNotEmpty(),
                            hasCompletedOnboarding = true,
                            showRejectedPopup = if (shouldShowRejected) true else it.showRejectedPopup,
                            showCompletedPopup = if (shouldShowCompleted) true else it.showCompletedPopup,
                            googleAccountName = googleAccName.ifBlank { it.googleAccountName },
                            googleAccountEmail = googleAccEmail,
                            isChatSuspended = isChatSuspended,
                            isDeviceBanned = isDeviceBanned
                        )
                    }

                    if (statusChanged) {
                        val notificationTitle = when (statusVal) {
                            "Verified" -> "Account Verified! 🎉"
                            "Completed" -> "Restoration Completed! 🏆"
                            "Rejected" -> "Verification Rejected ⚠️"
                            else -> "Appeal Status Updated"
                        }
                        val notificationBody = when (statusVal) {
                            "Verified" -> "Your PUBG character appeal is now verified. +100 Credits added."
                            "Completed" -> "Congratulations! Your account has been successfully unbanned."
                            "Rejected" -> "Your authentication appeal was rejected by security auditors."
                            else -> "Your status changed to $statusVal."
                        }
                        triggerLocalNotification(context.applicationContext, notificationTitle, notificationBody)
                    }
                }
            }
    }

    fun seedAdminsCollection() {
        val firestore = FirebaseFirestore.getInstance()
        val adminEmails = listOf("admin@gmail.com", "admin@reclaimaccounts.com", "oshaqplayz@gmail.com", "oshaqyt2@gmail.com", "oshaqali722@gmail.com")
        for (email in adminEmails) {
            val adminDoc = hashMapOf(
                "email" to email,
                "role" to "Super Admin",
                "creationDate" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            )
            firestore.collection("admins").document(email.lowercase())
                .set(adminDoc, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FirestoreSeeding", "Admin entry $email seeded successfully.")
                }
        }
    }

    fun claimDailyReward(context: android.content.Context) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val userUid = currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        val newCredits = _uiState.value.userCredits + 25
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val newStreak = _uiState.value.streakDays + 1
        
        val updates = hashMapOf<String, Any>(
            "credits" to newCredits,
            "lastClaimDate" to todayStr,
            "hasClaimedToday" to true,
            "streakDays" to newStreak
        )
        
        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(userUid)
            .update(updates)
            .addOnSuccessListener {
                _uiState.update { 
                    it.copy(
                        userCredits = newCredits,
                        hasClaimedToday = true,
                        lastClaimDate = todayStr,
                        streakDays = newStreak
                    )
                }
                showToast("Claimed Daily Check-in: +25 Secure Coins!", "SUCCESS")
                triggerLocalNotification(context.applicationContext, "Restoration Daily Reward 🎁", "You claimed +25 secure coins! Keep your streak active.")
            }
            .addOnFailureListener {
                _uiState.update { 
                    it.copy(
                        userCredits = newCredits,
                        hasClaimedToday = true,
                        lastClaimDate = todayStr,
                        streakDays = newStreak
                    )
                }
                showToast("Check-in saved locally: +25 Secure Coins!", "SUCCESS")
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
        if (!enabled) {
            _uiState.update { it.copy(isAdminMode = false) }
            profilesListener?.remove()
            profilesListener = null
            promoCodesListener?.remove()
            promoCodesListener = null
            startListeningToCases()
            return
        }
        
        val userEmail = _uiState.value.googleAccountEmail
        if (userEmail.isNullOrBlank()) {
            showToast("Authorize first to access administrator systems.", "WARNING")
            return
        }
        
        _uiState.update { it.copy(isAuthOperationLoading = true) }
        FirebaseFirestore.getInstance().collection("admins").document(userEmail.lowercase())
            .get()
            .addOnSuccessListener { doc ->
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                if (doc.exists()) {
                    _uiState.update { it.copy(isAdminMode = true) }
                    startListeningToAllCasesForAdmin()
                    startListeningToAllUserProfiles()
                    startListeningToAllPromoCodes()
                    showToast("Super Admin Mode Verified!", "SUCCESS")
                } else {
                    showToast("ACCESS DENIED: User is not configured as Super Admin in Firestore.", "ERROR")
                }
            }
            .addOnFailureListener {
                _uiState.update { it.copy(isAuthOperationLoading = false) }
                showToast("Admin verification failed: ${it.localizedMessage}", "ERROR")
            }
    }

    fun startListeningToAllUserProfiles() {
        if (!_uiState.value.isAdminMode) return
        try {
            profilesListener?.remove()
            profilesListener = FirebaseFirestore.getInstance().collection("reclaim_profiles")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val uid = doc.id
                            val playerName = doc.getString("playerName") ?: doc.getString("targetPlayerName") ?: ""
                            val playerUid = doc.getString("playerUid") ?: doc.getString("targetPlayerUid") ?: ""
                            val userCredits = doc.getLong("credits")?.toInt() ?: doc.getDouble("credits")?.toInt() ?: doc.getLong("userCredits")?.toInt() ?: doc.getDouble("userCredits")?.toInt() ?: 50
                            val status = doc.getString("status") ?: doc.getString("accountStatus") ?: "Pending Verification"
                            val verified = doc.getBoolean("verified") ?: doc.getBoolean("isVerified") ?: false
                            val userRank = doc.getString("userRank") ?: "Silver Recruit"
                            val userRole = doc.getString("userRole") ?: "User"
                            val googleAccountName = doc.getString("googleAccountName") ?: doc.getString("playerName")
                            val googleAccountEmail = doc.getString("googleAccountEmail")
                            AdminUserItem(
                                uid = uid,
                                playerName = playerName,
                                playerUid = playerUid,
                                userCredits = userCredits,
                                status = status,
                                verified = verified,
                                userRank = userRank,
                                userRole = userRole,
                                googleAccountName = googleAccountName,
                                googleAccountEmail = googleAccountEmail
                            )
                        }.distinctBy { it.uid }
                        _uiState.update { it.copy(adminUsers = list) }
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firebase error in startListeningToAllUserProfiles: ${e.message}")
        }
    }

    fun startListeningToAllPromoCodes() {
        if (!_uiState.value.isAdminMode) return
        try {
            promoCodesListener?.remove()
            promoCodesListener = FirebaseFirestore.getInstance().collection("promo_codes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val code = doc.id
                            val reward = doc.getLong("reward")?.toInt() ?: doc.getDouble("reward")?.toInt() ?: 0
                            val expiryDate = doc.getString("expiryDate") ?: ""
                            val usageLimit = doc.getLong("usageLimit")?.toInt() ?: doc.getDouble("usageLimit")?.toInt() ?: 100
                            val timesRedeemed = doc.getLong("timesRedeemed")?.toInt() ?: doc.getDouble("timesRedeemed")?.toInt() ?: 0
                            val isSingleUse = doc.getBoolean("singleUse") ?: doc.getBoolean("isSingleUse") ?: false
                            val redeemedByUsers = doc.get("redeemedByUsers") as? List<Any> ?: emptyList()
                            PromoCode(
                                code = code,
                                reward = reward,
                                expiryDate = expiryDate,
                                usageLimit = usageLimit,
                                timesRedeemed = timesRedeemed,
                                isSingleUse = isSingleUse,
                                redeemedByUsers = redeemedByUsers.map { it.toString() }
                            )
                        }
                        _uiState.update { it.copy(adminPromoCodes = list) }
                    }
                }
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firebase error in startListeningToAllPromoCodes: ${e.message}")
        }
    }

    fun adminUpdateUserFields(
        userUid: String,
        updatedCredits: Int?,
        updatedUsername: String?,
        updatedPlayerUid: String?,
        updatedPlayerName: String?,
        updatedStatus: String?,
        updatedVerified: Boolean?,
        updatedRank: String?,
        updatedRole: String? = null
    ) {
        if (!_uiState.value.isAdminMode) return
        val updates = hashMapOf<String, Any>()
        
        var finalCredits = updatedCredits
        var isVerifying = false
        
        if (updatedStatus != null) {
            updates["status"] = updatedStatus
            if (updatedStatus == "Verified" || updatedStatus == "Completed" || updatedStatus == "Active") {
                isVerifying = true
                updates["verified"] = true
                updates["verificationTime"] = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                // Automatic Reward: +100 credits upon verification
                finalCredits = (updatedCredits ?: 50) + 100
            } else if (updatedStatus == "Rejected" || updatedStatus == "Suspended") {
                updates["verified"] = false
            }
        }
        
        if (finalCredits != null) updates["credits"] = finalCredits
        if (updatedUsername != null) updates["googleAccountName"] = updatedUsername
        if (updatedPlayerUid != null) updates["playerUid"] = updatedPlayerUid
        if (updatedPlayerName != null) updates["playerName"] = updatedPlayerName
        if (updatedVerified != null && !isVerifying) updates["verified"] = updatedVerified
        if (updatedRank != null) updates["userRank"] = updatedRank
        if (updatedRole != null) updates["userRole"] = updatedRole

        FirebaseFirestore.getInstance().collection("reclaim_profiles").document(userUid)
            .update(updates)
            .addOnSuccessListener {
                showToast("Admin: User account updated!", "SUCCESS")
                
                viewModelScope.launch {
                    val id = java.util.UUID.randomUUID().toString()
                    val activityMap = hashMapOf<String, Any>(
                        "id" to id,
                        "user" to "Admin Dispatcher",
                        "action" to "Status updated user ID ${updatedPlayerName ?: userUid} to '$updatedStatus'",
                        "time" to "Just Now",
                        "type" to "info"
                    )
                    FirebaseFirestore.getInstance().collection("live_activities").document(id)
                        .set(activityMap)
                }

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null && currentUser.uid == userUid) {
                    _uiState.update { state ->
                        state.copy(
                            userCredits = finalCredits ?: state.userCredits,
                            googleAccountName = updatedUsername ?: state.googleAccountName,
                            targetPlayerUid = updatedPlayerUid ?: state.targetPlayerUid,
                            targetPlayerName = updatedPlayerName ?: state.targetPlayerName,
                            accountStatus = updatedStatus ?: state.accountStatus,
                            isVerified = if (isVerifying) true else (updatedVerified ?: state.isVerified),
                            userRank = updatedRank ?: state.userRank,
                            userRole = updatedRole ?: state.userRole
                        )
                    }
                }
            }
            .addOnFailureListener {
                FirebaseFirestore.getInstance().collection("reclaim_profiles").document(userUid)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        showToast("Admin: User created/merged successfully!", "SUCCESS")
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser != null && currentUser.uid == userUid) {
                            _uiState.update { state ->
                                state.copy(
                                    userCredits = finalCredits ?: state.userCredits,
                                    googleAccountName = updatedUsername ?: state.googleAccountName,
                                    targetPlayerUid = updatedPlayerUid ?: state.targetPlayerUid,
                                    targetPlayerName = updatedPlayerName ?: state.targetPlayerName,
                                    accountStatus = updatedStatus ?: state.accountStatus,
                                    isVerified = if (isVerifying) true else (updatedVerified ?: state.isVerified),
                                    userRank = updatedRank ?: state.userRank,
                                    userRole = updatedRole ?: state.userRole
                                )
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        showToast("Admin: Failed to update user, ${e.message}", "ERROR")
                    }
            }
        
        // Parallel sync to RTDB
        viewModelScope.launch {
            try {
                val currentProfile = repository.rtdbGetProfile(userUid)?.toMutableMap() ?: mutableMapOf()
                if (finalCredits != null) currentProfile["userCredits"] = finalCredits
                if (updatedUsername != null) currentProfile["googleAccountName"] = updatedUsername
                if (updatedPlayerUid != null) currentProfile["playerUid"] = updatedPlayerUid
                if (updatedPlayerName != null) currentProfile["playerName"] = updatedPlayerName
                if (updatedStatus != null) currentProfile["status"] = updatedStatus
                if (updatedVerified != null) currentProfile["verified"] = updatedVerified
                if (updatedRank != null) currentProfile["userRank"] = updatedRank
                if (updatedRole != null) currentProfile["userRole"] = updatedRole
                repository.rtdbSaveProfile(userUid, currentProfile)
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "Admin RTDB sync error: ${e.message}")
            }
        }
    }

    fun adminDeleteUser(userUid: String) {
        if (!_uiState.value.isAdminMode) return
        val PROTECTED_UIDS = setOf("555", "000")
        if (userUid in PROTECTED_UIDS) { 
            showToast("Protected account.")
            return 
        }
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance().collection("reclaim_profiles").document(userUid).delete().addOnSuccessListener {
                    showToast("User account deleted securely.", "SUCCESS")
                }.addOnFailureListener { e ->
                    showToast("Failed to delete user: ${e.message}", "ERROR")
                }
            } catch (e: Exception) {
                showToast("Failed to delete user: ${e.message}", "ERROR")
            }
        }
    }

    fun updateCommunityReportStatus(reportId: String, newStatus: String) {
        if (!_uiState.value.isAdminMode) return
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance().collection("community_reports").document(reportId)
                    .update("status", newStatus)
                    .addOnSuccessListener {
                        showToast("Report status updated to $newStatus.", "SUCCESS")
                    }
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "Failed to update report: ${e.message}")
            }
        }
    }

    fun deleteCommunityReport(reportId: String) {
        if (!_uiState.value.isAdminMode) return
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance().collection("community_reports").document(reportId)
                    .delete()
                    .addOnSuccessListener {
                        showToast("Report deleted securely.", "SUCCESS")
                    }
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "Failed to delete report: ${e.message}")
            }
        }
    }

    fun adminSendFCMNotification(title: String, message: String, targetAudience: String) {
        if (!_uiState.value.isAdminMode) return
        val data = hashMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "targetAudience" to targetAudience,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            FirebaseFirestore.getInstance().collection("fcm_requests").add(data)
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore error: ${e.message}")
        }

        // Real local notification fallback trigger & logging
        val newNotification = NotificationLogItem(
            id = java.util.UUID.randomUUID().toString(),
            title = "📡 $title",
            body = message,
            timestamp = "Just now",
            isUnread = true
        )
        _uiState.update { state ->
            state.copy(
                userNotificationLogs = listOf(newNotification) + state.userNotificationLogs,
                lastReceivedNotification = Pair(title, message)
            )
        }
    }

    fun adminSendCustomPopup(title: String, message: String, targetAudience: String) {
        if (!_uiState.value.isAdminMode) return
        val data = hashMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "targetAudience" to targetAudience,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            FirebaseFirestore.getInstance().collection("admin_popups").document("global_live_popup").set(data)
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore error: ${e.message}")
        }
    }

    fun clearLastNotification() {
        _uiState.update { it.copy(lastReceivedNotification = null) }
    }

    fun sendFCMNotification(title: String, message: String, targetAudience: String) {
        val data = hashMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "targetAudience" to targetAudience,
            "targetUid" to targetAudience,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("fcm_requests").add(data)
        } catch (e: Exception) {
            Log.e("ReclaimViewModel", "Firestore error in sendFCMNotification: ${e.message}")
        }
    }

    fun adminConfigurePrices(boost: Int, sandbox: Int, decoupler: Int, ticket: Int, appeal: Int) {
        _uiState.update {
            it.copy(
                costRecoveryScoreBoost = boost,
                costPreloadSandbox = sandbox,
                costPreloadMailDecoupler = decoupler,
                costCreateNewTicket = ticket,
                costManualEmailAppeal = appeal
            )
        }
    }

    fun adminUpdateAppUpdate(force: Boolean, optional: Boolean, url: String, verName: String, verCode: Int, changelog: String) {
        _uiState.update {
            it.copy(
                forceUpdateApp = force,
                optionalUpdateApp = optional,
                updateApkUrl = url,
                updateVerName = verName,
                updateVerCode = verCode,
                updateChangelog = changelog
            )
        }
    }

    private var adminTimerJob: kotlinx.coroutines.Job? = null

    fun adminSetTimer(seconds: Int, isRunning: Boolean) {
        adminTimerJob?.cancel()
        _uiState.update { it.copy(adminTimerSeconds = seconds, isAdminTimerRunning = isRunning) }
        if (isRunning) {
            adminTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val currentSecs = _uiState.value.adminTimerSeconds
                    if (currentSecs <= 1) {
                        _uiState.update { it.copy(adminTimerSeconds = 0, isAdminTimerRunning = false) }
                        break
                    } else {
                        _uiState.update { it.copy(adminTimerSeconds = currentSecs - 1) }
                    }
                }
            }
        }
    }

    fun adminCreatePromoCode(
        code: String,
        reward: Int,
        expiryDate: String, // YYYY-MM-DD
        usageLimit: Int,
        isSingleUse: Boolean
    ) {
        if (!_uiState.value.isAdminMode) return
        val upperCode = code.trim().uppercase()
        if (upperCode.isBlank()) {
            showToast("Promo Code cannot be empty!", "ERROR")
            return
        }
        val data = hashMapOf<String, Any>(
            "code" to upperCode,
            "reward" to reward,
            "expiryDate" to expiryDate,
            "usageLimit" to usageLimit,
            "timesRedeemed" to 0,
            "singleUse" to isSingleUse,
            "redeemedByUsers" to emptyList<String>()
        )
        FirebaseFirestore.getInstance().collection("promo_codes").document(upperCode)
            .set(data)
            .addOnSuccessListener {
                showToast("Promo Code '$upperCode' registered in Firestore!", "SUCCESS")
            }
            .addOnFailureListener { e ->
                showToast("Failed to register Promo Code: ${e.message}", "ERROR")
            }
    }

    fun adminDeletePromoCode(code: String) {
        if (!_uiState.value.isAdminMode) return
        val upperCode = code.trim().uppercase()
        FirebaseFirestore.getInstance().collection("promo_codes").document(upperCode)
            .delete()
            .addOnSuccessListener {
                showToast("Promo Code '$upperCode' deleted!", "SUCCESS")
            }
            .addOnFailureListener { e ->
                showToast("Failed to delete code: ${e.message}", "ERROR")
            }
    }

    fun adminAddSupabaseSlider(imageUrl: String, linkUrl: String) {
        if (!_uiState.value.isAdminMode) return
        viewModelScope.launch {
            try {
                repository.addSupabaseSlider(imageUrl, linkUrl)
                showToast("Admin: Slider Added Successfully", "SUCCESS")
                triggerProgressRefresh() // Refresh sliders
            } catch (e: Exception) {
                showToast("Admin: Failed to add slider", "ERROR")
            }
        }
    }

    fun adminDeleteSupabaseSlider(imageUrl: String) {
        if (!_uiState.value.isAdminMode) return
        viewModelScope.launch {
            try {
                repository.deleteSupabaseSlider(imageUrl)
                showToast("Admin: Slider Deleted Successfully", "SUCCESS")
                triggerProgressRefresh() // Refresh sliders
            } catch (e: Exception) {
                showToast("Admin: Failed to delete slider", "ERROR")
            }
        }
    }

    private var reportsListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun startListeningToReports() {
        reportsListener?.remove()
        reportsListener = FirebaseFirestore.getInstance().collection("reclaim_reports")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val reports = snapshot.documents.mapNotNull { doc ->
                        val obj = doc.data ?: return@mapNotNull null
                        CommunityReport(
                            reportId = doc.id,
                            uid = obj["uid"]?.toString() ?: "",
                            banReason = obj["banReason"]?.toString() ?: "",
                            status = obj["status"]?.toString() ?: "Pending Review",
                            submittedBy = obj["submittedBy"]?.toString() ?: "",
                            timestamp = (obj["timestamp"] as? Long) ?: 0L,
                            isPremiumVerified = obj["isPremiumVerified"] as? Boolean ?: false
                        )
                    }
                    _uiState.update { it.copy(communityReports = reports) }
                }
            }
    }

    fun submitBanReport(uid: String, reason: String, status: String = "Pending Review") {
        val id = java.util.UUID.randomUUID().toString()
        val author = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
        val reportMap = hashMapOf(
            "reportId" to id,
            "uid" to uid,
            "banReason" to reason,
            "status" to status,
            "submittedBy" to author,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("reclaim_reports").document(id).set(reportMap)
            .addOnSuccessListener {
                showToast("Report submitted ($status) successfully.", "SUCCESS")
            }
            .addOnFailureListener {
                showToast("Failed to submit report", "ERROR")
            }
    }

    fun searchBanStatus(uid: String) {
        val report = _uiState.value.communityReports.find { it.uid == uid && (it.status == "Verified" || it.status == "Premium Verified") }
        if (report != null) {
            _uiState.update {
                it.copy(
                    searchedBanStatus = "BANNED (${report.status})",
                    searchedBanReason = report.banReason,
                    searchedAppealAvailable = true
                )
            }
        } else {
            FirebaseFirestore.getInstance().collection("reclaim_reports")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener {
                    val firestoreReport = it.documents.find { doc -> 
                        doc.getString("status") == "Verified" || doc.getString("status") == "Premium Verified"
                    }
                    if (firestoreReport != null) {
                        _uiState.update { state -> state.copy(
                            searchedBanStatus = "BANNED (${firestoreReport.getString("status")})",
                            searchedBanReason = firestoreReport.getString("banReason") ?: "N/A",
                            searchedAppealAvailable = true
                        )}
                    } else {
                        _uiState.update { state -> state.copy(
                            searchedBanStatus = "NO VERIFIED BAN FOUND",
                            searchedBanReason = "N/A",
                            searchedAppealAvailable = false
                        )}
                    }
                }
        }
    }

    fun adminVerifyReport(reportId: String, accept: Boolean, isPremium: Boolean = false) {
        if (!_uiState.value.isAdminMode) return
        val newStatus = if (accept) (if (isPremium) "Premium Verified" else "Verified") else "Rejected"
        
        FirebaseFirestore.getInstance().collection("reclaim_reports").document(reportId)
            .update("status", newStatus, "isPremiumVerified", isPremium)
            .addOnSuccessListener {
                if (accept) {
                    val reward = if (isPremium) 20 else 10
                    _uiState.update { it.copy(userCredits = it.userCredits + reward) }
                    showToast("Report verified. Awarded $reward credits to author.", "SUCCESS")
                } else {
                    showToast("Report rejected as Fake. No credits awarded.", "ERROR")
                }
            }
    }

    fun submitFeedback(type: String, email: String, text: String, rating: Int) {
        val id = java.util.UUID.randomUUID().toString()
        val fb = hashMapOf(
            "id" to id,
            "type" to type,
            "email" to email,
            "text" to text,
            "rating" to rating,
            "isApproved" to true,
            "timestamp" to System.currentTimeMillis()
        )
        
        FirebaseFirestore.getInstance().collection("user_feedback").document(id).set(fb)
            .addOnSuccessListener {
                showToast("Thank you! Feedback submitted.", "SUCCESS")
            }
            .addOnFailureListener {
                showToast("Failed to post feedback.", "ERROR")
            }
            
        viewModelScope.launch {
            try {
                repository.addSupabaseFeedback(fb)
                Log.d("ReclaimViewModel", "Feedback successfully backed up to Supabase.")
            } catch (e: Exception) {
                Log.e("ReclaimViewModel", "Supabase feedback backup skipped (standard fallback enabled): ${e.message}")
            }
        }
    }

    fun deleteFeedback(id: String) {
        FirebaseFirestore.getInstance().collection("user_feedback").document(id).delete()
            .addOnSuccessListener {
                showToast("Feedback comment removed successfully.", "SUCCESS")
            }
    }

    private fun startListeningToFeedback() {
        feedbackListener = FirebaseFirestore.getInstance().collection("user_feedback")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.isEmpty) {
                        seedDefaultFeedback()
                    } else {
                        val list = snapshot.map { doc ->
                            FeedbackItem(
                                id = doc.id,
                                type = doc.getString("type") ?: "Comment",
                                email = doc.getString("email") ?: "",
                                text = doc.getString("text") ?: "",
                                rating = doc.getLong("rating")?.toInt() ?: 5,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                isApproved = doc.getBoolean("isApproved") ?: true
                            )
                        }
                        _uiState.update { it.copy(userFeedbackList = list) }
                    }
                }
            }
    }

    private fun seedDefaultFeedback() {
        val db = FirebaseFirestore.getInstance()
        val list = listOf(
            FeedbackItem("", "Rating", "m***@gmail.com", "My ticket was verified in under 12 hours. Best recovery assistance ever, support agent guided me step-by-step!", 5, System.currentTimeMillis() - 86400000),
            FeedbackItem("", "Comment", "x***x@yahoo.com", "Make sure you have your linked Google email active when submitting the case. Got my account back safely!", 5, System.currentTimeMillis() - 172800000),
            FeedbackItem("", "Bug", "l***9@reclaim.com", "The feedback submission didn't load first time on mobile data, but worked flawlessly on WiFi.", 4, System.currentTimeMillis() - 259200000),
            FeedbackItem("", "Suggestion", "g***p@outlook.com", "Please add more detailed visual guides for restoring Facebook links. App is super clean though!", 5, System.currentTimeMillis() - 345600000)
        )
        for (item in list) {
            val map = hashMapOf(
                "type" to item.type,
                "email" to item.email,
                "text" to item.text,
                "rating" to item.rating,
                "isApproved" to item.isApproved,
                "timestamp" to item.timestamp
            )
            db.collection("user_feedback").add(map)
        }
    }

    private fun startListeningToTips() {
        tipsListener = FirebaseFirestore.getInstance().collection("recovery_tips")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.isEmpty) {
                        seedDefaultTips()
                    } else {
                        val list = snapshot.map { doc ->
                            RecoveryTip(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                content = doc.getString("content") ?: "",
                                category = doc.getString("category") ?: "General Check",
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }
                        _uiState.update { it.copy(recoveryTips = list) }
                    }
                }
            }
    }

    private fun seedDefaultTips() {
        val db = FirebaseFirestore.getInstance()
        val defaultTips = listOf(
            RecoveryTip("", "Two-Factor Authentication (2FA)", "Enable App-based 2FA (Google/Microsoft Authenticator) across all linked identity centers to prevent intrusion.", "Security Audit", System.currentTimeMillis() - 100000),
            RecoveryTip("", "Valid Recovery Outlets", "Ensure your secondary mailbox or authorization linked devices remain safe, verified, and accessible.", "Prerequisites", System.currentTimeMillis() - 200000),
            RecoveryTip("", "Prevent Credential Leakage", "Real support assistants will never request your account password, validation tokens, or bank lock headers.", "Critical Safety", System.currentTimeMillis() - 300000),
            RecoveryTip("", "Linked Subsystems Sync", "Regularly review and log out unfamiliar clients from Google, Play Games, Game Center or Steam settings under linked apps.", "Management", System.currentTimeMillis() - 400000)
        )
        for (tip in defaultTips) {
            val map = hashMapOf(
                "title" to tip.title,
                "content" to tip.content,
                "category" to tip.category,
                "timestamp" to tip.timestamp
            )
            db.collection("recovery_tips").add(map)
        }
    }

    fun addRecoveryTip(title: String, content: String, category: String) {
        val map = hashMapOf(
            "title" to title,
            "content" to content,
            "category" to category,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("recovery_tips").add(map)
            .addOnSuccessListener {
                showToast("Support Tip published successfully.", "SUCCESS")
            }
            .addOnFailureListener {
                showToast("Failed to publish tip.", "ERROR")
            }
    }

    fun deleteRecoveryTip(id: String) {
        FirebaseFirestore.getInstance().collection("recovery_tips").document(id).delete()
            .addOnSuccessListener {
                showToast("Support Tip deleted successfully.", "SUCCESS")
            }
    }

// ==========================================
// CHAT SYSTEM LOGIC
// ==========================================

fun fetchChatDirectory() {
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("reclaim_profiles").limit(50).get().addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    val logo = doc.getString("profileLogoUri") ?: doc.getString("firebasePhotoUrl") ?: doc.getString("photoUrl")
                    AdminUserItem(
                        uid = doc.id,
                        playerName = doc.getString("playerName") ?: doc.getString("googleAccountName") ?: "Unknown",
                        playerUid = doc.getString("playerUid") ?: "",
                        status = doc.getString("status") ?: "Unknown",
                        profileLogoUri = logo
                    )
                }.filter { it.uid != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.distinctBy { it.uid }
                _uiState.update { it.copy(chatDirectory = list) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReclaimViewModel", "Failed to fetch chat directory: ${e.message}")
        }
    }
}

fun searchChatUser(query: String) {
    if (query.isBlank()) {
        _uiState.update { it.copy(searchedChatUser = null) }
        return
    }
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("reclaim_profiles").document(query).get().addOnSuccessListener { resultByUid ->
                if (resultByUid.exists()) {
                    val logo = resultByUid.getString("profileLogoUri") ?: resultByUid.getString("firebasePhotoUrl") ?: resultByUid.getString("photoUrl")
                    val item = AdminUserItem(
                        uid = resultByUid.id,
                        playerName = resultByUid.getString("playerName") ?: resultByUid.getString("googleAccountName") ?: "Unknown",
                        playerUid = resultByUid.getString("playerUid") ?: "",
                        status = resultByUid.getString("status") ?: "Unknown",
                        profileLogoUri = logo
                    )
                    _uiState.update { it.copy(searchedChatUser = item) }
                } else {
                    db.collection("reclaim_profiles").whereEqualTo("playerUid", query).get().addOnSuccessListener { resultByPlayerId ->
                        if (!resultByPlayerId.isEmpty) {
                            val doc = resultByPlayerId.documents[0]
                            val logo = doc.getString("profileLogoUri") ?: doc.getString("firebasePhotoUrl") ?: doc.getString("photoUrl")
                            val item = AdminUserItem(
                                uid = doc.id,
                                playerName = doc.getString("playerName") ?: doc.getString("googleAccountName") ?: "Unknown",
                                playerUid = doc.getString("playerUid") ?: "",
                                status = doc.getString("status") ?: "Unknown",
                                profileLogoUri = logo
                            )
                            _uiState.update { it.copy(searchedChatUser = item) }
                        } else {
                            showToast("User not found.", "ERROR")
                            _uiState.update { it.copy(searchedChatUser = null) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            showToast("Search failed: ${e.message}", "ERROR")
        }
    }
}

fun sendChatConnectionRequest(receiverUid: String, receiverPlayerId: String, receiverName: String) {
    val state = _uiState.value
    if (state.isChatSuspended || state.isDeviceBanned) {
        showToast("You are suspended from chat.", "ERROR")
        return
    }
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val reqId = java.util.UUID.randomUUID().toString()
            val senderUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(state.googleAccountEmail ?: "guest").hashCode()}"
            val isDev = receiverUid == "oshaq_dev_uid"
            
            val req = ChatConnectionRequest(
                id = reqId,
                senderUid = senderUid,
                senderPlayerId = state.targetPlayerUid,
                senderName = state.googleAccountName ?: "Unknown",
                receiverUid = receiverUid,
                receiverPlayerId = receiverPlayerId,
                receiverName = receiverName,
                status = if (isDev) "approved" else "pending_receiver",
                timestamp = System.currentTimeMillis()
            )
            
            val batch = db.batch()
            batch.set(db.collection("chat_requests").document(reqId), req)
            if (isDev) {
                val connId = java.util.UUID.randomUUID().toString()
                val conn = ChatConnection(
                    id = connId,
                    user1Uid = senderUid,
                    user2Uid = receiverUid,
                    user1Name = state.googleAccountName ?: "Unknown",
                    user2Name = receiverName,
                    lastMessage = "System: Connected with Developer.",
                    lastMessageTime = System.currentTimeMillis()
                )
                batch.set(db.collection("chat_connections").document(connId), conn)
            }
            batch.commit().addOnSuccessListener {
                showToast(if (isDev) "Connected with Developer!" else "Connection Request Sent", "SUCCESS")
                _uiState.update { it.copy(searchedChatUser = null) }
                if (!isDev) {
                    sendFCMNotification(
                        title = "New Connection Request",
                        message = "${state.googleAccountName ?: "Someone"} wants to connect with you.",
                        targetAudience = receiverUid
                    )
                }
            }.addOnFailureListener { e ->
                showToast("Failed to send request: ${e.message}", "ERROR")
            }
        } catch (e: Exception) {
            showToast("Failed to send request: ${e.message}", "ERROR")
        }
    }
}

fun cancelChatConnectionRequest(receiverUid: String) {
    val state = _uiState.value
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(state.googleAccountEmail ?: "guest").hashCode()}"
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("chat_requests")
                .whereEqualTo("senderUid", myUid)
                .whereEqualTo("receiverUid", receiverUid)
                .whereEqualTo("status", "pending_receiver")
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().addOnSuccessListener {
                        showToast("Connection Request Cancelled", "SUCCESS")
                    }
                }
        } catch (e: Exception) {
            showToast("Failed to cancel: ${e.message}", "ERROR")
        }
    }
}

fun acceptChatConnectionRequest(reqId: String) {
    val state = _uiState.value
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("chat_requests").document(reqId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val senderUid = doc.getString("senderUid") ?: ""
                    val senderName = doc.getString("senderName") ?: "Someone"
                    val receiverUid = doc.getString("receiverUid") ?: ""
                    val receiverName = doc.getString("receiverName") ?: state.googleAccountName ?: "Someone"
                    
                    db.runTransaction { tx ->
                        val reqRef = db.collection("chat_requests").document(reqId)
                        tx.update(reqRef, "status", "approved")
                        
                        val connId = java.util.UUID.randomUUID().toString()
                        val conn = ChatConnection(
                            id = connId,
                            user1Uid = senderUid,
                            user2Uid = receiverUid,
                            user1Name = senderName,
                            user2Name = receiverName,
                            lastMessage = "Connected!",
                            lastMessageTime = System.currentTimeMillis()
                        )
                        tx.set(db.collection("chat_connections").document(connId), conn)
                        null
                    }.addOnSuccessListener {
                        showToast("Connection Approved!", "SUCCESS")
                        sendFCMNotification(
                            title = "Connection Approved",
                            message = "You are now connected with $receiverName!",
                            targetAudience = senderUid
                        )
                    }
                }
            }
        } catch (e: Exception) {
            showToast("Failed: ${e.message}", "ERROR")
        }
    }
}

fun adminApproveChatRequest(reqId: String, user1Uid: String, user1Name: String, user2Uid: String, user2Name: String) {
    if (!_uiState.value.isAdminMode) return
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.runTransaction { tx ->
                val reqRef = db.collection("chat_requests").document(reqId)
                tx.update(reqRef, "status", "approved")
                
                val connId = java.util.UUID.randomUUID().toString()
                val conn = ChatConnection(
                    id = connId,
                    user1Uid = user1Uid,
                    user2Uid = user2Uid,
                    user1Name = user1Name,
                    user2Name = user2Name,
                    lastMessage = "Connected!",
                    lastMessageTime = System.currentTimeMillis()
                )
                tx.set(db.collection("chat_connections").document(connId), conn)
                null
            }.addOnSuccessListener {
                showToast("Connection Approved.", "SUCCESS")
                sendFCMNotification(
                    title = "Connection Approved",
                    message = "Admin approved your connection with $user2Name!",
                    targetAudience = user1Uid
                )
                sendFCMNotification(
                    title = "Connection Approved",
                    message = "Admin approved your connection with $user1Name!",
                    targetAudience = user2Uid
                )
            }
        } catch (e: Exception) {
            showToast("Failed: ${e.message}", "ERROR")
        }
    }
}

fun sendDirectMessage(connId: String, content: String) {
    val state = _uiState.value
    if (state.isChatSuspended || state.isDeviceBanned) {
        showToast("You are suspended from chat.", "ERROR")
        return
    }
    viewModelScope.launch {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("chat_connections").document(connId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user1Uid = doc.getString("user1Uid") ?: ""
                    val user2Uid = doc.getString("user2Uid") ?: ""
                    val senderName = state.googleAccountName ?: "Someone"
                    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(state.googleAccountEmail ?: "guest").hashCode()}"
                    val targetUid = if (myUid == user1Uid) user2Uid else user1Uid
                    
                    val msgId = java.util.UUID.randomUUID().toString()
                    val hasLink = content.contains("http://") || content.contains("https://")
                    val msg = DirectChatMessage(
                        id = msgId,
                        connectionId = connId,
                        senderUid = myUid,
                        content = content,
                        containsLink = hasLink,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    db.runTransaction { tx ->
                        tx.set(db.collection("direct_messages").document(msgId), msg)
                        tx.update(db.collection("chat_connections").document(connId), mapOf(
                            "lastMessage" to content,
                            "lastMessageTime" to System.currentTimeMillis()
                        ))
                        null
                    }.addOnSuccessListener {
                        sendFCMNotification(
                            title = senderName,
                            message = content,
                            targetAudience = targetUid
                        )
                    }
                }
            }
        } catch (e: Exception) {
            showToast("Failed to send message: ${e.message}", "ERROR")
        }
    }
}

fun deleteDirectMessage(msgId: String) {
    viewModelScope.launch {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("direct_messages").document(msgId).delete()
                .addOnSuccessListener {
                    showToast("Message un-sent", "SUCCESS")
                }
        } catch (e: Exception) {
            showToast("Failed to delete message: ${e.message}", "ERROR")
        }
    }
}

fun editDirectMessage(msgId: String, newContent: String) {
    viewModelScope.launch {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("direct_messages").document(msgId).update("content", newContent)
                .addOnSuccessListener {
                    showToast("Message edited", "SUCCESS")
                }
        } catch (e: Exception) {
            showToast("Failed to edit message: ${e.message}", "ERROR")
        }
    }
}

fun adminSuspendUserChat(uid: String, suspend: Boolean) {
    if (!_uiState.value.isAdminMode) return
    viewModelScope.launch {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("reclaim_profiles").document(uid).update("isChatSuspended", suspend)
            showToast(if (suspend) "User Chat Suspended" else "Chat Suspension Lifted", "SUCCESS")
        } catch (e: Exception) {
            showToast("Failed: ${e.message}", "ERROR")
        }
    }
}

fun adminBanUserDevice(uid: String, ban: Boolean) {
    if (!_uiState.value.isAdminMode) return
    viewModelScope.launch {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("reclaim_profiles").document(uid).update("isDeviceBanned", ban)
            showToast(if (ban) "Device Banned" else "Device Ban Lifted", "SUCCESS")
        } catch (e: Exception) {
            showToast("Failed: ${e.message}", "ERROR")
        }
    }
}

fun listenToChatConnections() {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(_uiState.value.googleAccountEmail ?: "guest").hashCode()}"
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    
    // Connections where user is user1
    db.collection("chat_connections").whereEqualTo("user1Uid", uid)
        .addSnapshotListener { snap1, _ ->
            val conns1 = snap1?.documents?.mapNotNull { it.toObject(ChatConnection::class.java) } ?: emptyList()
            db.collection("chat_connections").whereEqualTo("user2Uid", uid)
                .addSnapshotListener { snap2, _ ->
                    val conns2 = snap2?.documents?.mapNotNull { it.toObject(ChatConnection::class.java) } ?: emptyList()
                    val all = (conns1 + conns2).sortedByDescending { it.lastMessageTime }
                    _uiState.update { it.copy(chatConnections = all) }
                }
        }
        
    // Requests received or sent
    db.collection("chat_requests").whereEqualTo("receiverUid", uid).whereEqualTo("status", "pending_receiver")
        .addSnapshotListener { snapReceived, _ ->
            val receivedReqs = snapReceived?.documents?.mapNotNull { it.toObject(ChatConnectionRequest::class.java) } ?: emptyList()
            db.collection("chat_requests").whereEqualTo("senderUid", uid).whereEqualTo("status", "pending_receiver")
                .addSnapshotListener { snapSent, _ ->
                    val sentReqs = snapSent?.documents?.mapNotNull { it.toObject(ChatConnectionRequest::class.java) } ?: emptyList()
                    val allReqs = (receivedReqs + sentReqs).distinctBy { it.id }
                    _uiState.update { it.copy(chatConnectionRequests = allReqs) }
                }
        }
        
    // Admin pending requests
    if (_uiState.value.isAdminMode) {
        db.collection("chat_requests").whereEqualTo("status", "pending_admin")
            .addSnapshotListener { snap, _ ->
                val reqs = snap?.documents?.mapNotNull { it.toObject(ChatConnectionRequest::class.java) } ?: emptyList()
                _uiState.update { it.copy(adminPendingChatRequests = reqs) }
            }
    }
}

fun openChatConnection(connId: String) {
    _uiState.update { it.copy(activeChatConnectionId = connId) }
    com.google.firebase.firestore.FirebaseFirestore.getInstance()
        .collection("direct_messages").whereEqualTo("connectionId", connId)
        .addSnapshotListener { snap, _ ->
            val msgs = snap?.documents?.mapNotNull { it.toObject(DirectChatMessage::class.java) }?.sortedBy { it.timestamp }?.distinctBy { it.id } ?: emptyList()
            _uiState.update { it.copy(activeDirectChatMessages = msgs) }
        }
}

fun closeChatConnection() {
    _uiState.update { it.copy(activeChatConnectionId = null, activeDirectChatMessages = emptyList()) }
}

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        telemetryJob?.cancel()
        casesListener?.remove()
        messagesListener?.remove()
        updatesListener?.remove()
        profilesListener?.remove()
        promoCodesListener?.remove()
        userProfileListener?.remove()
        feedbackListener?.remove()
        tipsListener?.remove()
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

// END OF CHAT LOGIC

private val defaultRecoveryHistory = emptyList<RecoveryHistoryLogItem>()

data class AdminUserItem(
    val uid: String = "",
    val playerName: String = "",
    val playerUid: String = "",
    val userCredits: Int = 50,
    val status: String = "Pending Verification",
    val verified: Boolean = false,
    val userRank: String = "Silver Recruit",
    val userRole: String = "User",
    val googleAccountName: String? = null,
    val googleAccountEmail: String? = null,
    val profileLogoUri: String? = null
)

data class PromoCode(
    val code: String = "",
    val reward: Int = 0,
    val expiryDate: String = "", // "YYYY-MM-DD" e.g., "2026-12-31"
    val usageLimit: Int = 100,
    val timesRedeemed: Int = 0,
    val isSingleUse: Boolean = false,
    val redeemedByUsers: List<String> = emptyList()
)
