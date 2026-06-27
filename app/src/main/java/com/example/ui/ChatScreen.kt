package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

fun formatTimeShort(timeMillis: Long): String {
    if (timeMillis == 0L) return ""
    val format = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    return format.format(java.util.Date(timeMillis))
}

fun formatDateSeparator(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val smsTime = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = java.util.Calendar.getInstance()
    return if (now.get(java.util.Calendar.YEAR) == smsTime.get(java.util.Calendar.YEAR)) {
        if (now.get(java.util.Calendar.DAY_OF_YEAR) == smsTime.get(java.util.Calendar.DAY_OF_YEAR)) {
            "Today"
        } else if (now.get(java.util.Calendar.DAY_OF_YEAR) - smsTime.get(java.util.Calendar.DAY_OF_YEAR) == 1) {
            "Yesterday"
        } else {
            java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    } else {
        java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

@Composable
fun ChatScreen(vModel: ReclaimViewModel, state: ReclaimUiState) {
    if (state.activeChatConnectionId != null) {
        ActiveChatArea(vModel, state)
    } else {
        ChatDashboard(vModel, state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDashboard(vModel: ReclaimViewModel, state: ReclaimUiState) {
    var searchQuery by remember { mutableStateOf("") }
    var userToConnectWith by remember { mutableStateOf<AdminUserItem?>(null) }
    
    LaunchedEffect(Unit) {
        vModel.fetchChatDirectory()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA))
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Connections",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isDarkTheme) Color.White else Color.Black
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by Player UID...") },
            trailingIcon = {
                IconButton(onClick = { vModel.searchChatUser(searchQuery) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(4286331629),
                unfocusedBorderColor = Color.Gray
            ),
            singleLine = true
        )
        
        // Search Result
        state.searchedChatUser?.let { user ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = if (state.isDarkTheme) Color(0xFF1E1E1E) else Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(user.playerName, fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black)
                        Text(user.playerUid, fontSize = 12.sp, color = Color.Gray)
                    }
                    ConnectionButton(
                        user = user,
                        state = state,
                        vModel = vModel,
                        onShowDisclaimer = { userToConnectWith = it }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            // Requests
            if (state.chatConnectionRequests.isNotEmpty() || state.adminPendingChatRequests.isNotEmpty()) {
                item {
                    Text("Pending Requests", fontWeight = FontWeight.Bold, color = Color(4286331629))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.chatConnectionRequests) { req ->
                    RequestItem(req = req, isReceiver = true, state = state, onAccept = { vModel.acceptChatConnectionRequest(req.id) })
                }
                if (state.isAdminMode) {
                    items(state.adminPendingChatRequests) { req ->
                        RequestItem(req = req, isReceiver = false, isAdmin = true, state = state, onAdminApprove = { 
                            vModel.adminApproveChatRequest(req.id, req.senderUid, req.senderName, req.receiverUid, req.receiverName) 
                        })
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            
            // Developer Contact Banner
            item {
                val infiniteTransition = rememberInfiniteTransition(label = "banner_color")
                val color1 by infiniteTransition.animateColor(
                    initialValue = Color(4286331629),
                    targetValue = Color(0xFF9C27B0),
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "color1"
                )
                val color2 by infiniteTransition.animateColor(
                    initialValue = Color(0xFFFF5252),
                    targetValue = Color(0xFFFFD54F),
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "color2"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = 2.dp,
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(color1, color2)),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = if (state.isDarkTheme) Color(0xFF1E1E2A) else Color(0xFFE0F7FA)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = Color(0xFF1DA1F2), // Twitter Blue Tick
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Developer Oshaqplayz", fontWeight = FontWeight.ExtraBold, color = if (state.isDarkTheme) Color.White else Color.Black, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Some features are not working correctly in this beta version. Sorry for that, please wait for updates.",
                            fontSize = 11.sp,
                            color = if (state.isDarkTheme) Color(0xFFFFCC00) else Color(0xFFCC7A00),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Button(
                            onClick = { vModel.sendChatConnectionRequest("oshaq_dev_uid", "@oshaq", "Developer (@oshaq)") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send Direct Message", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            // Active Connections
            if (state.chatConnections.isNotEmpty()) {
                item {
                    Text("Active Chats", fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.chatConnections) { conn ->
                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val otherName = if (conn.user1Uid == currentUid) conn.user2Name else conn.user1Name
                    val otherUid = if (conn.user1Uid == currentUid) conn.user2Uid else conn.user1Uid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vModel.openChatConnection(conn.id) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar (Fetch firebase profile photo)
                        val otherUser = state.chatDirectory.find { it.uid == otherUid }
                        val avatarUrl = otherUser?.profileLogoUri
                        if (!avatarUrl.isNullOrEmpty()) {
                            SubcomposeAsyncImage(
                                model = avatarUrl,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .border(1.dp, Color(4286331629), androidx.compose.foundation.shape.CircleShape),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp, color = Color(4286331629))
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF0A84FF).copy(0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(otherName.take(1).uppercase(), color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(otherName, fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black, fontSize = 16.sp)
                                Text(formatTimeShort(conn.lastMessageTime), fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(conn.lastMessage, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                val elapsedMs = System.currentTimeMillis() - conn.lastMessageTime
                                val elapsedMonths = elapsedMs / (1000L * 60 * 60 * 24 * 30)
                                val deadTimeText = if (elapsedMonths < 1) "Dead Time: < 1 Month" else "Dead Time: $elapsedMonths Month(s)"
                                Text(
                                    text = deadTimeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (elapsedMonths >= 1) Color(0xFFFF5252) else Color.Gray.copy(0.7f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(0.2f), thickness = 0.5.dp, modifier = Modifier.padding(start = 66.dp))
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            
            // Directory (Horizontal recycler view)
            if (state.chatDirectory.isNotEmpty() && state.searchedChatUser == null) {
                item {
                    Text("Discover Users", fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.chatDirectory) { user ->
                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(180.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (state.isDarkTheme) Color(0xFF161616) else Color(0xFFF0F4F8)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val avatarUrl = user.profileLogoUri
                                    if (!avatarUrl.isNullOrEmpty()) {
                                        SubcomposeAsyncImage(
                                            model = avatarUrl,
                                            contentDescription = "User Avatar",
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .border(1.5.dp, Color(4286331629), androidx.compose.foundation.shape.CircleShape),
                                            contentScale = ContentScale.Crop,
                                            loading = {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp, color = Color(4286331629))
                                            }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color(0xFF0A84FF).copy(0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.playerName.take(1).uppercase(),
                                                color = Color(0xFF0A84FF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = user.playerName,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isDarkTheme) Color.White else Color(0xFF1E293B),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = user.playerUid,
                                        fontSize = 10.sp,
                                        color = if (state.isDarkTheme) Color.Gray else Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    ConnectionButton(
                                        user = user,
                                        state = state,
                                        vModel = vModel,
                                        onShowDisclaimer = { userToConnectWith = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (userToConnectWith != null) {
        AlertDialog(
            onDismissRequest = { userToConnectWith = null },
            title = {
                Text(
                    text = "Start Connection",
                    fontWeight = FontWeight.Bold,
                    color = if (state.isDarkTheme) Color.White else Color.Black
                )
            },
            text = {
                Text(
                    text = "You are about to send a connection request to ${userToConnectWith?.playerName}. " +
                           "By continuing, you agree to use polite language, respect other users, and proceed cordially to continue to chat.",
                    color = if (state.isDarkTheme) Color.LightGray else Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userToConnectWith?.let { u ->
                            vModel.sendChatConnectionRequest(u.uid, u.playerUid, u.playerName)
                        }
                        userToConnectWith = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(4286331629))
                ) {
                    Text("Agree & Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToConnectWith = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = if (state.isDarkTheme) Color(0xFF1E1E1E) else Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun RequestItem(req: ChatConnectionRequest, isReceiver: Boolean, isAdmin: Boolean = false, state: ReclaimUiState, onAccept: () -> Unit = {}, onAdminApprove: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val otherUid = if (isReceiver) req.senderUid else req.receiverUid
                val otherUser = state.chatDirectory.find { it.uid == otherUid }
                val avatarUrl = otherUser?.profileLogoUri
                if (!avatarUrl.isNullOrEmpty()) {
                    SubcomposeAsyncImage(
                        model = avatarUrl,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, Color(4286331629), androidx.compose.foundation.shape.CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF0A84FF).copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isReceiver) req.senderName.take(1).uppercase() else req.receiverName.take(1).uppercase(), color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isAdmin) "Admin: ${req.senderName} -> ${req.receiverName}" else "From: ${req.senderName}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (isAdmin) {
                Button(onClick = onAdminApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(4286331629))) {
                    Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else if (isReceiver) {
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ActiveChatArea(vModel: ReclaimViewModel, state: ReclaimUiState) {
    var messageText by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageText by remember { mutableStateOf("") }
    val translatedMessages = remember { mutableStateMapOf<String, String>() }
    val tts = remember {
        var ttsObj: android.speech.tts.TextToSpeech? = null
        ttsObj = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                ttsObj?.language = java.util.Locale.US
            }
        }
        ttsObj
    }
    
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA))
    ) {
        // Top Bar
        val conn = state.chatConnections.find { it.id == state.activeChatConnectionId }
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val otherName = if (conn != null) {
            if (conn.user1Uid == currentUid) conn.user2Name else conn.user1Name
        } else "Chat"
        val otherUid = if (conn != null) {
            if (conn.user1Uid == currentUid) conn.user2Uid else conn.user1Uid
        } else ""
        val otherUser = state.chatDirectory.find { it.uid == otherUid }
        val avatarUrl = otherUser?.profileLogoUri

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vModel.closeChatConnection() }) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            // Avatar in Active Chat top bar
            if (!avatarUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = avatarUrl,
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, Color(4286331629), androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF0A84FF).copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(otherName.take(1).uppercase(), color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(otherName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (conn != null) {
                    val elapsedMs = System.currentTimeMillis() - conn.lastMessageTime
                    val elapsedMonths = elapsedMs / (1000L * 60 * 60 * 24 * 30)
                    val deadTimeText = if (elapsedMonths < 1) "Dead Time: < 1 Month" else "Dead Time: $elapsedMonths Month(s)"
                    Text(deadTimeText, fontSize = 11.sp, color = if (elapsedMonths >= 1) Color(0xFFFF5252) else Color.LightGray.copy(0.7f))
                }
            }
        }
        
        // Messages
        val messages = state.activeDirectChatMessages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            reverseLayout = true
        ) {
            items(messages.size) { index ->
                val msg = messages.reversed()[index]
                val isMe = msg.senderUid == currentUid
                var showMenu by remember { mutableStateOf(false) }
                
                val showDateSeparator = if (index == messages.lastIndex) {
                    true
                } else {
                    val olderMsg = messages.reversed()[index + 1]
                    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = msg.timestamp }
                    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = olderMsg.timestamp }
                    cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR) ||
                            cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showDateSeparator) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(0.12f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formatDateSeparator(msg.timestamp),
                                    color = Color.White.copy(0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 4.dp,
                                        bottomEnd = if (isMe) 4.dp else 16.dp
                                    )
                                )
                                .background(if (isMe) Color(4286331629) else Color(4280362541))
                                .combinedClickable(
                                    onLongClick = { showMenu = true },
                                    onClick = {}
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = msg.content,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                
                                val translation = translatedMessages[msg.id]
                                if (translation != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(0.15f), thickness = 0.5.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = translation,
                                        color = Color.White.copy(0.85f),
                                        fontSize = 14.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatTimeShort(msg.timestamp),
                                        color = Color.White.copy(0.6f),
                                        fontSize = 11.sp
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                contentDescription = "Sent",
                                                tint = Color(0xFF53BDEB), // WhatsApp blue ticks
                                                modifier = Modifier.size(14.dp)
                                            )
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                contentDescription = "Sent",
                                                tint = Color(0xFF53BDEB), // WhatsApp blue ticks
                                                modifier = Modifier.size(14.dp).offset(x = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF2E2E2E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy Text", color = Color.White) },
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.content))
                                        showMenu = false
                                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Message", color = Color.White) },
                                    onClick = {
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, msg.content)
                                            type = "text/plain"
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Read Aloud (TTS)", color = Color.White) },
                                    onClick = {
                                        tts?.speak(msg.content, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Translate to Spanish", color = Color.White) },
                                    onClick = {
                                        val spanish = when {
                                            msg.content.equals("Hello", true) -> "Hola"
                                            msg.content.equals("How are you?", true) -> "¿Cómo estás?"
                                            msg.content.contains("good", true) -> "bueno"
                                            msg.content.contains("yes", true) -> "sí"
                                            msg.content.contains("no", true) -> "no"
                                            msg.content.contains("bye", true) -> "adiós"
                                            else -> "Traducido: ${msg.content}"
                                        }
                                        translatedMessages[msg.id] = spanish
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Translate to French", color = Color.White) },
                                    onClick = {
                                        val french = when {
                                            msg.content.equals("Hello", true) -> "Bonjour"
                                            msg.content.equals("How are you?", true) -> "Comment ça va?"
                                            msg.content.contains("good", true) -> "bien"
                                            msg.content.contains("yes", true) -> "oui"
                                            msg.content.contains("no", true) -> "non"
                                            msg.content.contains("bye", true) -> "au revoir"
                                            else -> "Traduit: ${msg.content}"
                                        }
                                        translatedMessages[msg.id] = french
                                        showMenu = false
                                    }
                                )
                                if (isMe) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Message", color = Color(0xFF53BDEB)) },
                                        onClick = {
                                            editingMessageId = msg.id
                                            editingMessageText = msg.content
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Un-send Message", color = Color(0xFFFF5252)) },
                                        onClick = {
                                            vModel.deleteDirectMessage(msg.id)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(4286331629),
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        vModel.sendDirectMessage(state.activeChatConnectionId!!, messageText)
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(4286331629))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }

        if (editingMessageId != null) {
            AlertDialog(
                onDismissRequest = { editingMessageId = null },
                title = { Text("Edit Message", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = editingMessageText,
                        onValueChange = { editingMessageText = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(4286331629),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vModel.editDirectMessage(editingMessageId!!, editingMessageText)
                            editingMessageId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("Save", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingMessageId = null }) {
                        Text("Cancel", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}

@Composable
fun ConnectionButton(
    user: AdminUserItem,
    state: ReclaimUiState,
    vModel: ReclaimViewModel,
    onShowDisclaimer: (AdminUserItem) -> Unit
) {
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "google_${(state.googleAccountEmail ?: "guest").hashCode()}"
    
    // Check if already connected
    val activeConnection = state.chatConnections.find { 
        (it.user1Uid == myUid && it.user2Uid == user.uid) || (it.user2Uid == myUid && it.user1Uid == user.uid)
    }
    
    // Check if we sent a request
    val sentRequest = state.chatConnectionRequests.find {
        it.senderUid == myUid && it.receiverUid == user.uid && it.status == "pending_receiver"
    }

    if (activeConnection != null) {
        Button(
            onClick = { vModel.openChatConnection(activeConnection.id) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    } else if (sentRequest != null) {
        Button(
            onClick = { vModel.cancelChatConnectionRequest(user.uid) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("Request Sent", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    } else {
        Button(
            onClick = { onShowDisclaimer(user) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(4286331629)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
