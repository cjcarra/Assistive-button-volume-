package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.ui.theme.AppThemePreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

class VolumeService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // --- Custom Lifecycle/SavedState Architecture for Compose Service ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // --- Variables & Services ---
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var repository: SettingsRepository
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- State Flow bindings for Reactive UI ---
    private val appSettingsFlow = MutableStateFlow(AppSettings())
    private var params = WindowManager.LayoutParams()

    private val handler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null

    // Stateful volume flows to communicate system audio changes to Composable
    private val mediaVolume = MutableStateFlow(0)
    private val maxMediaVolume = MutableStateFlow(15)

    private val ringVolume = MutableStateFlow(0)
    private val maxRingVolume = MutableStateFlow(15)

    private val notificationVolume = MutableStateFlow(0)
    private val maxNotificationVolume = MutableStateFlow(15)

    private val alarmVolume = MutableStateFlow(0)
    private val maxAlarmVolume = MutableStateFlow(15)

    // UI state
    private val isExpandedState = MutableStateFlow(false)
    private val isIdleState = MutableStateFlow(false)
    private val sideIsLeftState = MutableStateFlow(false) // Whether snap docked on left side

    // Receiver to listen to hardware volume clicks
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                syncVolumes()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize simple custom state and lifecycle
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Room local config binding
        val db = AppDatabase.getDatabase(this)
        repository = SettingsRepository(db.settingsDao())

        // Start listening to our Room settings and system volumes
        syncVolumes()
        registerVolumeReceiver()
        observeDatabaseSettings()

        // Setup Floating Window UI
        setupOverlayWindow()

        // Set running state in DB
        serviceScope.launch {
            repository.setServiceRunning(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        resetIdleTimer()
        return START_NOT_STICKY
    }

    private fun registerVolumeReceiver() {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
    }

    private fun syncVolumes() {
        mediaVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        maxMediaVolume.value = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        ringVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        maxRingVolume.value = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)

        notificationVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        maxNotificationVolume.value = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)

        alarmVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        maxAlarmVolume.value = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
    }

    private fun updateStreamVolume(stream: Int, value: Int) {
        try {
            audioManager.setStreamVolume(stream, value, 0)
            syncVolumes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeDatabaseSettings() {
        serviceScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                appSettingsFlow.value = settings
                // On first load, check if we need to load prior coordinates
                if (settings.lastPositionX != -1 && settings.lastPositionY != -1) {
                    params.x = settings.lastPositionX
                    params.y = settings.lastPositionY
                    val displayMetrics = resources.displayMetrics
                    sideIsLeftState.value = params.x + (settings.buttonSize * 3) / 2 < displayMetrics.widthPixels / 2
                    tryUpdateWindowLayout()
                }
            }
        }
    }

    private fun setupOverlayWindow() {
        val sizeVal = appSettingsFlow.value.buttonSize
        val density = resources.displayMetrics.density
        val sizePx = (sizeVal * density).toInt()

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx
            y = resources.displayMetrics.heightPixels / 2 - sizePx
        }

        sideIsLeftState.value = false

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@VolumeService)
            setViewTreeViewModelStoreOwner(this@VolumeService)
            setViewTreeSavedStateRegistryOwner(this@VolumeService)
            setContent {
                OverlayContent()
            }
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryUpdateWindowLayout() {
        composeView?.let { view ->
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWindowDimensions(expanded: Boolean) {
        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            // Enable touch watch to dismiss on touch outside card bounds
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.gravity = Gravity.TOP or Gravity.START
        }
        tryUpdateWindowLayout()
    }

    private fun resetIdleTimer() {
        idleRunnable?.let { handler.removeCallbacks(it) }
        isIdleState.value = false
        val timeoutMs = appSettingsFlow.value.idleTimeoutSeconds * 1000L
        idleRunnable = Runnable {
            if (!isExpandedState.value && appSettingsFlow.value.hideToCornerWhenIdle) {
                isIdleState.value = true
            }
        }
        handler.postDelayed(idleRunnable!!, timeoutMs)
    }

    private fun snapToNearestEdge(currentX: Int, currentY: Int) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val sizeVal = appSettingsFlow.value.buttonSize
        val density = displayMetrics.density
        val sizePx = (sizeVal * density).toInt()

        // Constraint within screen safe boundaries
        val safeY = currentY.coerceIn(100, screenHeight - sizePx - 100)

        val targetX = if (currentX + sizePx / 2 < screenWidth / 2) {
            sideIsLeftState.value = true
            0
        } else {
            sideIsLeftState.value = false
            screenWidth - sizePx
        }

        // Animate coordinate snap smoothly
        serviceScope.launch {
            val steps = 12
            val distance = targetX - currentX
            val startY = params.y
            val distanceY = safeY - startY
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                // Cubic ease out ease curves
                val t = 1f - progress
                val easeOut = 1f - t * t * t
                params.x = (currentX + distance * easeOut).toInt()
                params.y = (startY + distanceY * easeOut).toInt()
                tryUpdateWindowLayout()
                delay(14)
            }
            params.x = targetX
            params.y = safeY
            tryUpdateWindowLayout()

            // Save the exact final coordinate
            repository.updateSettings {
                it.copy(lastPositionX = params.x, lastPositionY = params.y)
            }
            resetIdleTimer()
        }
    }

    @Composable
    fun OverlayContent() {
        val settings by appSettingsFlow.collectAsState()
        val isExpanded by isExpandedState.collectAsState()
        val isIdle by isIdleState.collectAsState()
        val sideIsLeft by sideIsLeftState.collectAsState()

        val activeTheme = remember(settings.selectedTheme) {
            AppThemePreset.fromName(settings.selectedTheme)
        }

        MaterialTheme(colorScheme = activeTheme.toColorScheme()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isExpanded) Alignment.Center else Alignment.TopStart
            ) {
                if (isExpanded) {
                    // Dim/blur backing sheet that responds to dismiss clicks
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable {
                                isExpandedState.value = false
                                updateWindowDimensions(false)
                                resetIdleTimer()
                            }
                    )

                    // Volume panels card
                    VolumeControlPanel(activeTheme)
                } else {
                    // Floating circular button
                    FloatingButton(
                        settings = settings,
                        activeTheme = activeTheme,
                        isIdle = isIdle,
                        sideIsLeft = sideIsLeft
                    )
                }
            }
        }
    }

    @Composable
    fun FloatingButton(
        settings: AppSettings,
        activeTheme: AppThemePreset,
        isIdle: Boolean,
        sideIsLeft: Boolean
    ) {
        val haptic = LocalHapticFeedback.current
        var isDragging by remember { mutableStateOf(false) }

        // Slide partially off-screen if idle, using animated offsets
        val buttonSize = settings.buttonSize
        val targetOffset = if (isIdle) {
            if (sideIsLeft) -buttonSize * 0.55f else buttonSize * 0.55f
        } else {
            0f
        }

        val animatedOffset by animateFloatAsState(
            targetValue = targetOffset,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "idle_slide"
        )

        val animatedAlpha by animateFloatAsState(
            targetValue = if (isIdle) settings.opacityIdle else settings.opacityActive,
            animationSpec = tween(400),
            label = "idle_alpha"
        )

        val finalX = params.x
        val finalY = params.y

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = finalX + OffsetConversion.dpToPx(applicationContext, animatedOffset),
                        y = finalY
                    )
                }
                .size(buttonSize.dp)
                .alpha(animatedAlpha)
                .pointerInput(settings.id) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            isIdleState.value = false
                        },
                        onDragEnd = {
                            isDragging = false
                            snapToNearestEdge(params.x, params.y)
                        },
                        onDragCancel = {
                            isDragging = false
                            snapToNearestEdge(params.x, params.y)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            params.x = (params.x + dragAmount.x).toInt()
                            params.y = (params.y + dragAmount.y).toInt()
                            tryUpdateWindowLayout()
                        }
                    )
                }
                .pointerInput(settings.selectedTheme) {
                    detectTapGestures {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        isExpandedState.value = true
                        updateWindowDimensions(true)
                    }
                }
                .shadow(
                    elevation = if (isDragging) 12.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = activeTheme.shadowColor,
                    spotColor = activeTheme.primary
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(activeTheme.surface, activeTheme.background),
                        radius = 160f
                    )
                )
                .padding(2.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            activeTheme.primary.copy(alpha = 0.9f),
                            activeTheme.primary.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Adjust Volume",
                tint = activeTheme.onSurface,
                modifier = Modifier.size((buttonSize * 0.45).dp)
            )
        }
    }

    @Composable
    fun VolumeControlPanel(activeTheme: AppThemePreset) {
        val settings by appSettingsFlow.collectAsState()
        val haptic = LocalHapticFeedback.current

        val currentMediaVal by mediaVolume.collectAsState()
        val maxMediaVal by maxMediaVolume.collectAsState()

        val currentRingVal by ringVolume.collectAsState()
        val maxRingVal by maxRingVolume.collectAsState()

        val currentNotifVal by notificationVolume.collectAsState()
        val maxNotifVal by maxNotificationVolume.collectAsState()

        val currentAlarmVal by alarmVolume.collectAsState()
        val maxAlarmVal by maxAlarmVolume.collectAsState()

        Card(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.92f)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = activeTheme.shadowColor.copy(alpha = 0.45f),
                    spotColor = activeTheme.primary.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
            border = BorderStroke(1.4.dp, activeTheme.glassBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Volume Mixer",
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = activeTheme.onSurface
                        )
                        Text(
                            text = "Theme: ${activeTheme.name}",
                            fontSize = 12.sp,
                            color = activeTheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                if (settings.hapticFeedback) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                cycleTheme()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = activeTheme.primary.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Cycle Theme",
                                tint = activeTheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                if (settings.hapticFeedback) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                isExpandedState.value = false
                                updateWindowDimensions(false)
                                resetIdleTimer()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = activeTheme.primary.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize Control",
                                tint = activeTheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Custom Sliders for all Audio Streams
                VolumeSliderItem(
                    title = "Media",
                    icon = Icons.Default.MusicNote,
                    value = currentMediaVal,
                    maxValue = maxMediaVal,
                    onValueChange = {
                        updateStreamVolume(AudioManager.STREAM_MUSIC, it)
                    },
                    theme = activeTheme
                )

                Spacer(modifier = Modifier.height(10.dp))

                VolumeSliderItem(
                    title = "Ringtone",
                    icon = Icons.Default.RingVolume,
                    value = currentRingVal,
                    maxValue = maxRingVal,
                    onValueChange = {
                        updateStreamVolume(AudioManager.STREAM_RING, it)
                    },
                    theme = activeTheme
                )

                Spacer(modifier = Modifier.height(10.dp))

                VolumeSliderItem(
                    title = "Notifications",
                    icon = Icons.Default.Notifications,
                    value = currentNotifVal,
                    maxValue = maxNotifVal,
                    onValueChange = {
                        updateStreamVolume(AudioManager.STREAM_NOTIFICATION, it)
                    },
                    theme = activeTheme
                )

                Spacer(modifier = Modifier.height(10.dp))

                VolumeSliderItem(
                    title = "Alarms",
                    icon = Icons.Default.Alarm,
                    value = currentAlarmVal,
                    maxValue = maxAlarmVal,
                    onValueChange = {
                        updateStreamVolume(AudioManager.STREAM_ALARM, it)
                    },
                    theme = activeTheme
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Mode presets banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        text = "Mute All",
                        icon = Icons.Default.VolumeMute,
                        onClick = {
                            toggleAudioMode(AudioMode.MUTE)
                        },
                        theme = activeTheme,
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        text = "Vibrate",
                        icon = Icons.Default.Vibration,
                        onClick = {
                            toggleAudioMode(AudioMode.VIBRATE)
                        },
                        theme = activeTheme,
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        text = "Booster Max",
                        icon = Icons.Default.VolumeUp,
                        onClick = {
                            toggleAudioMode(AudioMode.BOOST)
                        },
                        theme = activeTheme,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        }
    }

    @Composable
    fun ColumnScope.VolumeSliderItem(
        title: String,
        icon: ImageVector,
        value: Int,
        maxValue: Int,
        onValueChange: (Int) -> Unit,
        theme: AppThemePreset
    ) {
        val haptic = LocalHapticFeedback.current
        val isMuted = value == 0

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isMuted) {
                        onValueChange(maxValue / 2)
                    } else {
                        onValueChange(0)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else icon,
                    contentDescription = "Toggle Mute",
                    tint = if (isMuted) theme.onSurface.copy(alpha = 0.4f) else theme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = theme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$value / $maxValue",
                        fontSize = 12.sp,
                        color = theme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }

                Slider(
                    value = value.toFloat(),
                    valueRange = 0f..maxValue.toFloat(),
                    onValueChange = {
                        val intVal = it.roundToInt()
                        if (intVal != value) {
                            onValueChange(intVal)
                        }
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = theme.primary,
                        inactiveTrackColor = theme.primary.copy(alpha = 0.2f),
                        thumbColor = theme.accent
                    )
                )
            }
        }
    }

    @Composable
    fun PresetButton(
        text: String,
        icon: ImageVector,
        onClick: () -> Unit,
        theme: AppThemePreset,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.primary.copy(alpha = 0.12f),
                contentColor = theme.primary
            ),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = text, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }

    private fun cycleTheme() {
        val list = AppThemePreset.ALL
        val currentSetting = appSettingsFlow.value.selectedTheme
        val currentIndex = list.indexOfFirst { it.name.equals(currentSetting, ignoreCase = true) }
        val nextIndex = (currentIndex + 1) % list.size
        val nextTheme = list[nextIndex].name

        serviceScope.launch {
            repository.updateSettings {
                it.copy(selectedTheme = nextTheme)
            }
        }
    }

    private fun toggleAudioMode(mode: AudioMode) {
        when (mode) {
            AudioMode.MUTE -> {
                updateStreamVolume(AudioManager.STREAM_MUSIC, 0)
                updateStreamVolume(AudioManager.STREAM_RING, 0)
                updateStreamVolume(AudioManager.STREAM_NOTIFICATION, 0)
                updateStreamVolume(AudioManager.STREAM_ALARM, 0)
            }
            AudioMode.VIBRATE -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                updateStreamVolume(AudioManager.STREAM_MUSIC, 0)
                updateStreamVolume(AudioManager.STREAM_RING, 0)
            }
            AudioMode.BOOST -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                updateStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume.value)
                updateStreamVolume(AudioManager.STREAM_RING, maxRingVolume.value)
                updateStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotificationVolume.value)
                updateStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume.value)
            }
        }
    }

    enum class AudioMode {
        MUTE, VIBRATE, BOOST
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        // Remove receiver safely
        try {
            unregisterReceiver(volumeReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Remove overlay safely
        composeView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        serviceScope.launch {
            repository.setServiceRunning(false)
        }

        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object OffsetConversion {
    fun dpToPx(context: Context, dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
