const fs = require('fs');

const path = 'app/src/main/java/com/example/MainActivity.kt';
let text = fs.readFileSync(path, 'utf8');

// Replace LanguageDictionary calls with actual English text directly!
// e.g. LanguageDictionary.get(lang, "recoveryStatus") -> "Active Bypass Logs"
const engMap = {
    "recoveryStatus": "Active Bypass Logs",
    "probability": "Bypass Probability",
    "recoveryPotential": "Recovery success probability",
    "aiRecoveryScore": "Core Security AI Score",
    "riskAnalysis": "Risk factor is fully clear.",
    "highSuccessRate": "HIGH PROBABILITY UNBAN",
    "firebaseAuth": "Real-time Google Auth Hub",
    "firebaseDb": "Real-time Recovery Cluster",
    "firebaseStorage": "Restoration CDN Nodes",
    "firebaseMsg": "Sync Payload Telemetry",
    "supabaseInt": "Supabase REST API Gateway",
    "supabaseDb": "Secondary Bypass Table",
    "supabaseStorage": "Blob Restoration Bucket",
    "systemStatus": "OPERATIONAL CORES",
    "lastSync": "last database heartbeat sync",
    "timerTitle": "NEXT AUTOMATED HEARTBEAT",
    "days": "Days",
    "hours": "Hours",
    "minutes": "Mins",
    "seconds": "Secs",
    "emailRecovery": "Secure Email Layer Bypass",
    "emailRecoveryDesc": "Redirect target verification links to virtual encrypted sandboxes.",
    "statusActive": "READY FOR INJECTION",
    "gameRecovery": "Device Hardware Unban",
    "gameRecoveryDesc": "Spoof HWID metadata directly via secure hypervisor firmware injection.",
    "limitedMode": "Restoration Sandboxing API",
    "comingSoon": "COMING SOON",
    "advancedRecovery": "Kernel Suspensions Appeal",
    "searchPlaceholder": "Filter active recovery payloads...",
    "email": "Email Address",
    "settingsGlass": "Liquid Glassmorphism Intensity",
    "settingsLanguage": "INTERFACE LANGUAGE SELECTION",
    "sessionTermination": "ACTIVE SESSION TERMINATION",
    "logoutButton": "LOGOUT DECRYPTION KEY",
    "devOptionHub": "DEVELOPER OPTIONS PROFILE HUB",
    "verifiedCore": "VERIFIED SYSTEM ARCHITECT",
    "devBio": "This application uses secure client-side database handshakes and ML Kit dynamic localization algorithms. Developed by Oshaq Playz.",
    "devResources": "DEVELOPER RESOURCES AND CHANNELS",
    "dailyUsage": "Daily Activity Logs",
    "creditUsage": "Security Credits Pool",
    "accountActivity": "Realtime Appeal Feed",
    "uploadProgress": "Payload Uplink Path",
    "downloadProgress": "Decryption CDN Downlink",
    "claimBox": "CLAIM DAILY REWARD"
};

for (const [k, v] of Object.entries(engMap)) {
    text = text.replace(new RegExp(`LanguageDictionary\\.get\\(lang,\\s*"${k}"\\)`, 'g'), `"${v}"`);
}

// remove unused val lang = state.language
text = text.replace(/val lang = state\.language\n/g, '');

// Clean up SettingsPanel for languages
text = text.replace(/SettingsPanel\(title = "INTERFACE LANGUAGE SELECTION"\) \{[\s\S]*?\}\s*\}\s*Spacer\(modifier = Modifier\.height\(24\.dp\)\)/, '');

// Remove LanguageDictionary.Language references
text = text.replace(/LanguageDictionary\.Language\.values\(\)/g, "emptyList<Any>()");

// Remove Neon Colors:
// 0xFF00E5FF (Neon Cyan) -> 0xFF2196F3 (Material Blue) or dynamic
// 0xFF1FFFC5 (Neon Mint) -> 0xFF4CAF50 (Material Green)
// 0xFFFFD600 (Neon Yellow) -> 0xFFFF9800 (Material Orange)
text = text.replace(/0xFF00E5FF/g, '0xFF0A84FF');
text = text.replace(/0xFF1FFFC5/g, '0xFF34C759');
text = text.replace(/0xFFFFD600/g, '0xFFFF9500');

// "Eddie shadows and highlights ": adding shadow to cards.
// We can find Modifier.background(...) which has shadow? No, we can add a shadow modifier to cards.
// But doing string manipulation is risky if not precise. Let's make it simpler.
text = text.replace(/modifier = Modifier\.fillMaxWidth\(\)/g, 'modifier = Modifier.fillMaxWidth().shadow(if (state.isDarkTheme) 4.dp else 8.dp, RoundedCornerShape(16.dp), ambientColor = if(state.isDarkTheme) Color.Black else Color.Gray, spotColor = if(state.isDarkTheme) Color.Black else Color.Gray).background(if (state.isDarkTheme) Color(0xFF1E1E1E) else Color.White, RoundedCornerShape(16.dp))');

fs.writeFileSync(path, text, 'utf8');
console.log("Done");
