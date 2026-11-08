package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.BuildConfig
import com.example.ZoyaForegroundService
import com.example.accessibility.ZoyaAccessibilityService
import com.example.live.ZoyaState
import kotlinx.coroutines.launch

@Composable
fun ZoyaScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    fun checkConnected(): Boolean {
        if (connectivityManager == null) return true
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    var isOnline by remember { mutableStateOf(checkConnected()) }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            return@DisposableEffect onDispose {}
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = checkConnected()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                isOnline = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            isOnline = checkConnected()
        }

        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Safely ignored
            }
        }
    }

    return isOnline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    
    // Real-time network connectivity state
    val isOnline = rememberIsOnline()

    // Determine effective API key (either saved in prefs or provided via BuildConfig secrets)
    val savedKey = prefs.getString("api_key", "")?.trim() ?: ""
    val hasBuildConfigKey = BuildConfig.GEMINI_API_KEY.isNotEmpty() &&
            BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" &&
            BuildConfig.GEMINI_API_KEY != "YOUR_API_KEY"
    val effectiveKey = if (savedKey.isNotEmpty()) savedKey else if (hasBuildConfigKey) BuildConfig.GEMINI_API_KEY else ""

    var apiKey by remember { mutableStateOf(savedKey) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Direct reactive state flows from ZoyaForegroundService
    val isRunning by ZoyaForegroundService.isRunning.collectAsState()
    val zoyaState by ZoyaForegroundService.currentState.collectAsState()
    val statusText by ZoyaForegroundService.statusText.collectAsState()
    val errorMessage by ZoyaForegroundService.errorMessage.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            try {
                val intent = Intent(context, ZoyaForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to start Zoya: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required for voice uplink.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isOnline -> Color(0xFFEF5350) // Red when offline
                                        !isRunning -> Color(0xFF00E676) // Bright green when online and standby
                                        zoyaState == ZoyaState.LISTENING -> Color(0xFFB388FF)
                                        zoyaState == ZoyaState.THINKING -> Color(0xFFFFD180)
                                        zoyaState == ZoyaState.SPEAKING -> Color(0xFF69F0AE)
                                        else -> Color(0xFF80D8FF)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Z.O.Y.A.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 3.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(
                        onClick = onNavigateToChat,
                        modifier = Modifier
                            .testTag("chat_terminal_button")
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat Terminal",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier
                            .testTag("settings_menu_button")
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF131726))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chat & Interaction Logs", color = Color.White) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFF00E5FF)) },
                            onClick = {
                                showMenu = false
                                onNavigateToChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gemini API Key", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFFFD54F)) },
                            onClick = {
                                showMenu = false
                                showApiKeyDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Accessibility (Auto-Actions)", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF69F0AE)) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF080B14)
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF141933),
                            Color(0xFF0B0E1E),
                            Color(0xFF05070E)
                        ),
                        center = Offset(500f, 600f),
                        radius = 1600f
                    )
                )
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top status pill
                StatusBadge(state = zoyaState, isRunning = isRunning, isOnline = isOnline)

                Spacer(modifier = Modifier.height(12.dp))

                // Center Sci-Fi Holographic Orb
                Box(
                    modifier = Modifier
                        .clickable {
                            if (isRunning) {
                                val service = ZoyaForegroundService.activeService
                                service?.reconnectSession()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ZoyaOrb(state = zoyaState)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // State feedback label
                Text(
                    text = when {
                        !isOnline -> "No Internet Connection • System Offline"
                        !isRunning -> {
                            if (errorMessage != null) "Online • $errorMessage" else "System Online • Ready to Initialize"
                        }
                        else -> statusText
                    },
                    color = when {
                        !isOnline -> Color(0xFFFF8A80)
                        errorMessage != null && !isRunning -> Color(0xFFFFD54F)
                        else -> Color.White.copy(alpha = 0.9f)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Command Action Chips
                QuickActionChips(
                    onActionSelected = { command ->
                        val service = ZoyaForegroundService.activeService
                        if (service != null && isRunning) {
                            service.sendTextMessage(command)
                        } else {
                            android.widget.Toast.makeText(context, "Initialize Zoya first to run: \"$command\"", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Start / Disconnect Action Button
                if (!isRunning) {
                    Button(
                        modifier = Modifier
                            .testTag("start_zoya_button")
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF00E5FF), spotColor = Color(0xFF00E5FF)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF050B14)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        onClick = {
                            if (!isOnline) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Please turn on Internet (Wi-Fi or Mobile Data) to connect to Zoya.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasMic && hasContacts && hasPhone) {
                                    try {
                                        val intent = Intent(context, ZoyaForegroundService::class.java)
                                        ContextCompat.startForegroundService(context, intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error starting Zoya: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    val neededPerms = mutableListOf(
                                        android.Manifest.permission.RECORD_AUDIO,
                                        android.Manifest.permission.READ_CONTACTS,
                                        android.Manifest.permission.CALL_PHONE,
                                        android.Manifest.permission.READ_PHONE_STATE
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        neededPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(neededPerms.toTypedArray())
                                }
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Initialize Z.O.Y.A.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("reconnect_button"),
                            onClick = {
                                val service = ZoyaForegroundService.activeService
                                service?.reconnectSession()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reconnect", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("disconnect_button"),
                            onClick = {
                                val intent = Intent(context, ZoyaForegroundService::class.java).apply {
                                    action = "STOP"
                                }
                                try {
                                    context.startService(intent)
                                } catch (e: Exception) {
                                    context.stopService(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF1744).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFF8A80)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Text("Disconnect", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Single instance Floating Assistant Button: Draggable, docks to edge, never overlaps orb/buttons
            FloatingAssistantButton(
                isOnline = isOnline,
                isRunning = isRunning,
                zoyaState = zoyaState,
                onTap = {
                    if (isRunning) {
                        onNavigateToChat()
                    } else if (!isOnline) {
                        android.widget.Toast.makeText(context, "Please turn on Internet to connect to Zoya.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasMic && hasContacts && hasPhone) {
                            try {
                                val intent = Intent(context, ZoyaForegroundService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Error starting Zoya: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val needed = mutableListOf(
                                android.Manifest.permission.RECORD_AUDIO,
                                android.Manifest.permission.READ_CONTACTS,
                                android.Manifest.permission.CALL_PHONE,
                                android.Manifest.permission.READ_PHONE_STATE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(needed.toTypedArray())
                        }
                    }
                },
                maxWidth = screenWidth,
                maxHeight = screenHeight
            )
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(if (apiKey.isNotEmpty()) apiKey else if (hasBuildConfigKey) BuildConfig.GEMINI_API_KEY else "") }
        var passwordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Gemini API Key Configuration", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Zoya connects directly to Google's Gemini Live native audio endpoint.",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (hasBuildConfigKey) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "✓ AI Studio Secret Key is active",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Get an API key from Google AI Studio",
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = tempKey.trim()
                        prefs.edit().putString("api_key", trimmed).apply()
                        apiKey = trimmed
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun StatusBadge(state: ZoyaState, isRunning: Boolean, isOnline: Boolean = true) {
    val badgeColor by animateColorAsState(
        targetValue = when {
            !isOnline -> Color(0xFFEF5350)
            !isRunning -> Color(0xFF00E676)
            state == ZoyaState.LISTENING -> Color(0xFFB388FF)
            state == ZoyaState.THINKING -> Color(0xFFFFD180)
            state == ZoyaState.SPEAKING -> Color(0xFF69F0AE)
            else -> Color(0xFF00E5FF)
        },
        label = "BadgeColor"
    )

    Surface(
        color = Color(0xFF101426).copy(alpha = 0.8f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isOnline -> "OFFLINE"
                    !isRunning -> "ONLINE"
                    state == ZoyaState.LISTENING -> "LIVE • LISTENING"
                    state == ZoyaState.THINKING -> "PROCESSING"
                    state == ZoyaState.SPEAKING -> "TRANSMITTING"
                    else -> "ONLINE • STANDBY"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun QuickActionChips(onActionSelected: (String) -> Unit) {
    val actions = listOf(
        Pair(Icons.Default.Call, "Call Mom"),
        Pair(Icons.AutoMirrored.Filled.Chat, "Send WhatsApp to Rahul"),
        Pair(Icons.Default.FlashlightOn, "Toggle Torch"),
        Pair(Icons.AutoMirrored.Filled.VolumeUp, "Set volume to 70%"),
        Pair(Icons.Default.MusicNote, "Play Synthwave Music"),
        Pair(Icons.Default.Settings, "Open Quick Settings")
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(actions) { (icon, command) ->
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.clickable { onActionSelected(command) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = command,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingAssistantButton(
    isOnline: Boolean,
    isRunning: Boolean,
    zoyaState: ZoyaState,
    onTap: () -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }

    val maxWidthPx = with(density) { maxWidth.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }

    val buttonSizeDp = 52.dp
    val buttonSizePx = with(density) { buttonSizeDp.toPx() }

    // Keep floating button within vertical safe boundaries (never overlap top bar or bottom action buttons)
    val minYPx = with(density) { 90.dp.toPx() }
    val maxYPx = with(density) { (maxHeight - 210.dp).toPx() }.coerceAtLeast(minYPx + 100f)

    // Saved position & dock state
    val defaultY = with(density) { 180.dp.toPx() }
    var offsetY by remember { mutableStateOf(prefs.getFloat("floating_btn_y", defaultY).coerceIn(minYPx, maxYPx)) }
    var isDocked by remember { mutableStateOf(prefs.getBoolean("floating_btn_docked", false)) }
    var isRightEdge by remember { mutableStateOf(prefs.getBoolean("floating_btn_is_right", true)) }

    // X position
    var rawOffsetX by remember {
        val initialX = if (isRightEdge) maxWidthPx - buttonSizePx - with(density) { 12.dp.toPx() } else with(density) { 12.dp.toPx() }
        mutableStateOf(initialX)
    }

    // Target animated X:
    val targetOffsetX = if (isDocked) {
        if (isRightEdge) maxWidthPx - with(density) { 18.dp.toPx() }
        else with(density) { -34.dp.toPx() }
    } else {
        if (isRightEdge) maxWidthPx - buttonSizePx - with(density) { 12.dp.toPx() }
        else with(density) { 12.dp.toPx() }
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(280),
        label = "FloatingX"
    )

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY.coerceIn(minYPx, maxYPx),
        animationSpec = tween(180),
        label = "FloatingY"
    )

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    animatedOffsetX.roundToInt(),
                    animatedOffsetY.roundToInt()
                )
            }
            .pointerInput(maxWidthPx) {
                detectDragGestures(
                    onDragStart = {
                        if (isDocked) isDocked = false
                    },
                    onDragEnd = {
                        val midX = maxWidthPx / 2f
                        isRightEdge = rawOffsetX >= midX
                        
                        // If moved very close to screen edge, dock into edge tab
                        val edgeThreshold = with(density) { 55.dp.toPx() }
                        if (rawOffsetX > maxWidthPx - buttonSizePx - edgeThreshold || rawOffsetX < edgeThreshold) {
                            isDocked = true
                        } else {
                            isDocked = false
                        }

                        prefs.edit()
                            .putFloat("floating_btn_y", offsetY)
                            .putBoolean("floating_btn_docked", isDocked)
                            .putBoolean("floating_btn_is_right", isRightEdge)
                            .apply()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        rawOffsetX = (rawOffsetX + dragAmount.x).coerceIn(0f, maxWidthPx - buttonSizePx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(minYPx, maxYPx)
                    }
                )
            }
    ) {
        if (isDocked) {
            // Sleek docked edge handle with status indicator
            Surface(
                onClick = {
                    isDocked = false
                    prefs.edit().putBoolean("floating_btn_docked", false).apply()
                },
                modifier = Modifier
                    .size(width = 30.dp, height = 50.dp)
                    .shadow(
                        10.dp,
                        RoundedCornerShape(
                            topStart = if (isRightEdge) 16.dp else 0.dp,
                            bottomStart = if (isRightEdge) 16.dp else 0.dp,
                            topEnd = if (!isRightEdge) 16.dp else 0.dp,
                            bottomEnd = if (!isRightEdge) 16.dp else 0.dp
                        )
                    ),
                color = Color(0xFF0F1426),
                shape = RoundedCornerShape(
                    topStart = if (isRightEdge) 16.dp else 0.dp,
                    bottomStart = if (isRightEdge) 16.dp else 0.dp,
                    topEnd = if (!isRightEdge) 16.dp else 0.dp,
                    bottomEnd = if (!isRightEdge) 16.dp else 0.dp
                ),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isOnline -> Color(0xFFEF5350)
                                    !isRunning -> Color(0xFF00E676)
                                    zoyaState == ZoyaState.LISTENING -> Color(0xFFB388FF)
                                    zoyaState == ZoyaState.THINKING -> Color(0xFFFFD180)
                                    zoyaState == ZoyaState.SPEAKING -> Color(0xFF69F0AE)
                                    else -> Color(0xFF00E5FF)
                                }
                            )
                    )
                }
            }
        } else {
            // Full expanded Floating Button
            Surface(
                onClick = onTap,
                modifier = Modifier
                    .size(buttonSizeDp)
                    .shadow(14.dp, CircleShape, ambientColor = Color(0xFF00E5FF), spotColor = Color(0xFF00E5FF)),
                color = Color(0xFF0D1224),
                shape = CircleShape,
                border = BorderStroke(
                    1.5f.dp,
                    when {
                        !isOnline -> Color(0xFFEF5350).copy(alpha = 0.8f)
                        !isRunning -> Color(0xFF00E676).copy(alpha = 0.8f)
                        else -> Color(0xFF00E5FF)
                    }
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Zoya Assistant Floating Trigger",
                        tint = when {
                            !isOnline -> Color(0xFFEF5350)
                            !isRunning -> Color(0xFF00E676)
                            zoyaState == ZoyaState.LISTENING -> Color(0xFFB388FF)
                            zoyaState == ZoyaState.THINKING -> Color(0xFFFFD180)
                            zoyaState == ZoyaState.SPEAKING -> Color(0xFF69F0AE)
                            else -> Color(0xFF00E5FF)
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoyaOrb(state: ZoyaState) {
    val radiusScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0.5f) }
    
    // Orbital Ring Rotations using hardware-synchronized InfiniteTransition
    val infiniteTransition = rememberInfiniteTransition(label = "OrbitalRings")
    val ring1Angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val ring2Angle by infiniteTransition.animateFloat(
        initialValue = 120f,
        targetValue = 360f + 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )
    val ring3Angle by infiniteTransition.animateFloat(
        initialValue = 240f,
        targetValue = 360f + 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3"
    )
    val ring4Angle by infiniteTransition.animateFloat(
        initialValue = 45f,
        targetValue = -360f + 45f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring4"
    )

    LaunchedEffect(state) {
        when (state) {
            ZoyaState.IDLE -> {
                radiusScale.animateTo(1f, animationSpec = tween(800))
                glowAlpha.animateTo(
                    targetValue = 0.45f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.LISTENING -> {
                radiusScale.animateTo(1.12f, animationSpec = tween(400))
                glowAlpha.animateTo(
                    targetValue = 0.85f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.THINKING -> {
                radiusScale.animateTo(1.06f, animationSpec = tween(300))
                glowAlpha.animateTo(
                    targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.SPEAKING -> {
                radiusScale.animateTo(1.22f, animationSpec = tween(200))
                glowAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(280, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 4.2f
            val currentRadius = baseRadius * radiusScale.value
            
            val coreInnerColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF80D8FF)
                ZoyaState.LISTENING -> Color(0xFFB388FF)
                ZoyaState.THINKING -> Color(0xFFFFD180)
                ZoyaState.SPEAKING -> Color(0xFF69F0AE)
            }
            
            val coreOuterColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF00B0FF)
                ZoyaState.LISTENING -> Color(0xFF651FFF)
                ZoyaState.THINKING -> Color(0xFFFF9100)
                ZoyaState.SPEAKING -> Color(0xFF00E676)
            }

            // 1. Ambient Background Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreOuterColor.copy(alpha = glowAlpha.value * 0.55f), Color.Transparent),
                    center = center,
                    radius = currentRadius * 2.6f
                ),
                radius = currentRadius * 2.6f
            )

            // 2. The Glass Sphere Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        coreInnerColor.copy(alpha = 0.85f),
                        coreOuterColor.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.5f)
                    ),
                    center = Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.3f),
                    radius = currentRadius * 1.25f
                ),
                radius = currentRadius
            )
            
            // 3. Specular Highlight for 3D depth
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                center = Offset(center.x - currentRadius * 0.4f, center.y - currentRadius * 0.4f),
                radius = currentRadius * 0.28f
            )

            // 4. Neon Orbital Rings
            val ringRadiusX = currentRadius * 1.85f
            val ringRadiusY = currentRadius * 0.65f
            
            fun drawNeonRing(angle: Float, startColor: Color, endColor: Color, strokeWidth: Float) {
                rotate(angle, center) {
                    drawOval(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                            colors = listOf(startColor, endColor, startColor, Color.Transparent, startColor),
                            center = center
                        ),
                        topLeft = Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth)
                    )
                    drawOval(
                        color = startColor.copy(alpha = 0.25f),
                        topLeft = Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth * 2.5f)
                    )
                }
            }

            val speedMultiplier = if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 2f else 1f
            drawNeonRing(ring1Angle * speedMultiplier, Color(0xFFFF1744), Color(0xFFD50000), 3.5f)
            drawNeonRing(ring2Angle * speedMultiplier, Color(0xFF00E676), Color(0xFF76FF03), 3.5f)
            drawNeonRing(ring3Angle * speedMultiplier, Color(0xFF00E5FF), Color(0xFF2979FF), 3.5f)
            drawNeonRing(ring4Angle * speedMultiplier, Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f), 2f)
            
            // 5. Outer Dome Rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.25f)),
                    center = center,
                    radius = currentRadius * 2.2f
                ),
                radius = currentRadius * 2.2f,
                style = Stroke(width = 1.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val isRunning by ZoyaForegroundService.isRunning.collectAsState()
    val zoyaState by ZoyaForegroundService.currentState.collectAsState()
    val liveSessionManager = ZoyaForegroundService.activeService?.liveSessionManager
    val messages by (liveSessionManager?.messages ?: ZoyaForegroundService.messages).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Zoya Terminal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = when {
                                !isOnline -> "Status: OFFLINE (No Internet)"
                                isRunning -> "Status: ${zoyaState.name}"
                                else -> "Status: ONLINE (Standby)"
                            },
                            color = when {
                                !isOnline -> Color(0xFFFF8A80)
                                isRunning -> Color(0xFF00E5FF)
                                else -> Color(0xFF69F0AE)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ZoyaForegroundService.activeService?.reconnectSession()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconnect",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D111F))
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0D111F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Command or message Zoya...", color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00E5FF)
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val msg = inputText.trim()
                            if (msg.isNotEmpty()) {
                                val service = ZoyaForegroundService.activeService
                                if (service != null) {
                                    service.sendTextMessage(msg)
                                    inputText = ""
                                } else {
                                    android.widget.Toast.makeText(context, "Start Zoya on the Home screen first.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF050B14)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF070A12)
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF).copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No interactions logged yet.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Text(
                                "Speak out loud or type a command below.",
                                color = Color.DarkGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: String) {
    val isUser = message.startsWith("You: ")
    val isAction = message.startsWith("Action:") || message.startsWith("Result:")
    val isSystem = message.startsWith("Notice:") || message.startsWith("Error:") || message.startsWith("Connected") || message.startsWith("Session")

    val displayText = when {
        isUser -> message.removePrefix("You: ")
        message.startsWith("Zoya: ") -> message.removePrefix("Zoya: ")
        else -> message
    }

    if (isAction || isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = if (isAction) Color(0xFFFFD54F).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isAction) Color(0xFFFFD54F).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = displayText,
                    color = if (isAction) Color(0xFFFFE082) else Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                color = if (isUser) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF7C4DFF).copy(alpha = 0.18f),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isUser) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color(0xFF7C4DFF).copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth(0.82f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isUser) "You" else "Zoya",
                        color = if (isUser) Color(0xFF00E5FF) else Color(0xFFB388FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
