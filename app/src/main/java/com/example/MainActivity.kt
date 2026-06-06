package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppSettings
import com.example.ui.theme.AppThemePreset
import com.example.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var settingsViewModel: SettingsViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SettingsViewModel = viewModel()
            settingsViewModel = vm
            MainScreen(vm)
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically activate service on return if permission was just granted
        val vm = settingsViewModel
        if (vm != null) {
            val context = this
            if (vm.isOverlayPermissionGranted(context)) {
                val settings = vm.settingsState.value
                if (settings.isServiceRunning) {
                    vm.startServiceIfPermitted(context)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    var isPermissionGranted by remember { mutableStateOf(false) }

    // Recheck permission state on initialization and on every resume
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = viewModel.isOverlayPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeTheme = remember(settings.selectedTheme) {
        AppThemePreset.fromName(settings.selectedTheme)
    }

    // Audio stream monitoring in main activity (so adjustments are reflected instantly)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var mediaVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxMediaVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var ringVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_RING)) }
    val maxRingVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) }

    var notifVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)) }
    val maxNotifVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) }

    var alarmVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM)) }
    val maxAlarmVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }

    MaterialTheme(colorScheme = activeTheme.toColorScheme()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = activeTheme.bgGradients
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row matching HTML spec
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Assistive Volume",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "v2.5 Pro Active",
                                fontSize = 12.sp,
                                color = activeTheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Glass cog button visual decoration
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(activeTheme.glassBg)
                                .border(1.2.dp, activeTheme.glassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (settings.hapticFeedback) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚙️", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                // 1. Permission status panel
                PermissionBannerCard(
                    isGranted = isPermissionGranted,
                    activeTheme = activeTheme,
                    onRequestPermission = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        val intent = viewModel.getOverlayPermissionIntent(context)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Service Control Panel
                ServiceControlCard(
                    isServiceRunning = settings.isServiceRunning,
                    isPermissionGranted = isPermissionGranted,
                    activeTheme = activeTheme,
                    onToggleService = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.toggleService(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Mini visual preview card
                LivePreviewCard(settings = settings, activeTheme = activeTheme)

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Custom Themes List Selector
                ThemeSelectorCard(
                    selectedTheme = settings.selectedTheme,
                    activeTheme = activeTheme,
                    onThemeSelected = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        viewModel.updateTheme(it)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Volume Mixer Card (Test out AudioManager adjustments directly)
                VolumeMixerCard(
                    mediaVol = mediaVol,
                    maxMediaVol = maxMediaVol,
                    ringVol = ringVol,
                    maxRingVol = maxRingVol,
                    notifVol = notifVol,
                    maxNotifVol = maxNotifVol,
                    alarmVol = alarmVol,
                    maxAlarmVol = maxAlarmVol,
                    activeTheme = activeTheme,
                    onMediaChange = {
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                            mediaVol = it
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    onRingChange = {
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, it, 0)
                            ringVol = it
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    onNotifChange = {
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, it, 0)
                            notifVol = it
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    onAlarmChange = {
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
                            alarmVol = it
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Detailed Settings Controllers (Sliders, Toggles)
                InteractiveSettingsCard(
                    settings = settings,
                    activeTheme = activeTheme,
                    viewModel = viewModel
                )

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
}

@Composable
fun PermissionBannerCard(
    isGranted: Boolean,
    activeTheme: AppThemePreset,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                activeTheme.glassBg
            } else {
                activeTheme.primary.copy(alpha = 0.15f)
            }
        ),
        border = BorderStroke(
            width = 1.3.dp,
            color = if (isGranted) activeTheme.glassBorder else activeTheme.accent.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) activeTheme.primary.copy(alpha = 0.2f) else activeTheme.accent.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) activeTheme.primary else activeTheme.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGranted) "Permission Enabled" else "Overlay Permission Required",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeTheme.onSurface
                )
                Text(
                    text = if (isGranted) {
                        "The volume widget is ready to draw over other apps."
                    } else {
                        "This app needs draw over apps permission to display floating controls above other layout interfaces."
                    },
                    fontSize = 12.sp,
                    color = activeTheme.onSurface.copy(alpha = 0.70f)
                )

                if (!isGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("grant_permission_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeTheme.primary,
                            contentColor = activeTheme.background
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceControlCard(
    isServiceRunning: Boolean,
    isPermissionGranted: Boolean,
    activeTheme: AppThemePreset,
    onToggleService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.35f),
                spotColor = activeTheme.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
        border = BorderStroke(1.3.dp, activeTheme.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Assistive Controller",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.onSurface
                    )
                    Text(
                        text = if (isServiceRunning) "Floating widget is currently active" else "Widget background service stopped",
                        fontSize = 12.sp,
                        color = if (isServiceRunning) activeTheme.primary else activeTheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isServiceRunning) activeTheme.primary else activeTheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onToggleService,
                enabled = isPermissionGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("service_toggle_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) activeTheme.accent else activeTheme.primary,
                    disabledContainerColor = activeTheme.onSurface.copy(alpha = 0.12f),
                    contentColor = activeTheme.background
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceRunning) "Stop Assistive Button" else "Start Assistive Button",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isPermissionGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Please grant overlay permissions first to enable widget launch.",
                    fontSize = 11.sp,
                    color = activeTheme.accent,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LivePreviewCard(settings: AppSettings, activeTheme: AppThemePreset) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
        border = BorderStroke(1.3.dp, activeTheme.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preset Button Live Previews",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = activeTheme.onSurface.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Active / Floating Preview
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Active (Floating)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(activeTheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, activeTheme.glassBorder), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(settings.buttonSize.dp)
                                .alpha(settings.opacityActive)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = activeTheme.shadowColor.copy(alpha = 0.3f)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            activeTheme.primary,
                                            activeTheme.accent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((settings.buttonSize * 0.45).dp)
                            )
                        }
                    }
                }

                // Docked / Idle Preview
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Docked (Idle)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(activeTheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, activeTheme.glassBorder), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Vertical screen edge marker on the left
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .background(activeTheme.primary.copy(alpha = 0.5f))
                        )

                        // Docked Button: rectangular & aligned to start (simulated edge)
                        Box(
                            modifier = Modifier
                                .width(settings.dockedButtonSize.dp)
                                .height(settings.buttonSize.dp)
                                .alpha(settings.opacityIdle)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                                    ambientColor = activeTheme.shadowColor.copy(alpha = 0.2f)
                                )
                                .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            activeTheme.primary,
                                            activeTheme.accent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((settings.dockedButtonSize * 0.6).coerceAtMost(settings.buttonSize * 0.35).dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Size: Active = ${settings.buttonSize}dp, Docked = ${settings.dockedButtonSize}dp",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ThemeSelectorCard(
    selectedTheme: String,
    activeTheme: AppThemePreset,
    onThemeSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
        border = BorderStroke(1.3.dp, activeTheme.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Choose Theme Presets",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = activeTheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppThemePreset.ALL.forEach { preset ->
                    val isSelected = preset.name.equals(selectedTheme, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) activeTheme.primary else activeTheme.onSurface.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(preset.glassBg)
                            .border(BorderStroke(1.dp, preset.glassBorder), RoundedCornerShape(16.dp))
                            .clickable { onThemeSelected(preset.name) }
                            .padding(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = preset.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = preset.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preset.primary))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preset.accent))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preset.background))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeMixerCard(
    mediaVol: Int,
    maxMediaVol: Int,
    ringVol: Int,
    maxRingVol: Int,
    notifVol: Int,
    maxNotifVol: Int,
    alarmVol: Int,
    maxAlarmVol: Int,
    activeTheme: AppThemePreset,
    onMediaChange: (Int) -> Unit,
    onRingChange: (Int) -> Unit,
    onNotifChange: (Int) -> Unit,
    onAlarmChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
        border = BorderStroke(1.3.dp, activeTheme.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Manual System Audio Mixer",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = activeTheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            VolumeMixerSliderItem(
                label = "Media Content",
                icon = Icons.Default.MusicNote,
                value = mediaVol,
                maxValue = maxMediaVol,
                activeTheme = activeTheme,
                onValueChange = onMediaChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            VolumeMixerSliderItem(
                label = "Ringtone Sound",
                icon = Icons.Default.RingVolume,
                value = ringVol,
                maxValue = maxRingVol,
                activeTheme = activeTheme,
                onValueChange = onRingChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            VolumeMixerSliderItem(
                label = "Notifications",
                icon = Icons.Default.Notifications,
                value = notifVol,
                maxValue = maxNotifVol,
                activeTheme = activeTheme,
                onValueChange = onNotifChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            VolumeMixerSliderItem(
                label = "System Alarms",
                icon = Icons.Default.Alarm,
                value = alarmVol,
                maxValue = maxAlarmVol,
                activeTheme = activeTheme,
                onValueChange = onAlarmChange
            )
        }
    }
}

@Composable
fun VolumeMixerSliderItem(
    label: String,
    icon: ImageVector,
    value: Int,
    maxValue: Int,
    activeTheme: AppThemePreset,
    onValueChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val actualMax = maxValue.coerceAtLeast(1)
    val isMuted = value == 0
    var prevVolume by androidx.compose.runtime.saveable.rememberSaveable(label) { mutableStateOf(actualMax / 2) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isMuted) {
                    onValueChange(prevVolume.coerceIn(1, actualMax))
                } else {
                    prevVolume = value
                    onValueChange(0)
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else icon,
                contentDescription = "Toggle Mute for $label",
                tint = if (isMuted) activeTheme.onSurface.copy(alpha = 0.40f) else activeTheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, fontSize = 12.sp, color = activeTheme.onSurface.copy(alpha = 0.8f))
                Text(text = "$value / $actualMax", fontSize = 11.sp, color = activeTheme.primary, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = value.toFloat(),
                valueRange = 0f..actualMax.toFloat(),
                onValueChange = {
                    val target = it.roundToInt()
                    if (target != value) {
                        if (target > 0) {
                            prevVolume = target
                        }
                        onValueChange(target)
                    }
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    inactiveTrackColor = activeTheme.primary.copy(alpha = 0.15f),
                    thumbColor = activeTheme.accent
                )
            )
        }
    }
}

@Composable
fun InteractiveSettingsCard(
    settings: AppSettings,
    activeTheme: AppThemePreset,
    viewModel: SettingsViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = activeTheme.shadowColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.glassBg),
        border = BorderStroke(1.3.dp, activeTheme.glassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Widget Customization",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = activeTheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Button size customizer
            Text(
                text = "Button Floating Size: ${settings.buttonSize}dp",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.buttonSize.toFloat(),
                valueRange = 40f..80f,
                onValueChange = { viewModel.updateButtonSize(it.roundToInt()) },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    thumbColor = activeTheme.accent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Button docked size customizer
            Text(
                text = "Button Docked Size: ${settings.dockedButtonSize}dp",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.dockedButtonSize.toFloat(),
                valueRange = 10f..40f,
                onValueChange = { viewModel.updateDockedButtonSize(it.roundToInt()) },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    thumbColor = activeTheme.accent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opacity Active customizer
            Text(
                text = "Active Opacity: ${(settings.opacityActive * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.opacityActive,
                valueRange = 0.3f..1.0f,
                onValueChange = { viewModel.updateOpacityActive(it) },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    thumbColor = activeTheme.accent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opacity Idle customizer
            Text(
                text = "Idle/Hidden Opacity: ${(settings.opacityIdle * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.opacityIdle,
                valueRange = 0.1f..0.8f,
                onValueChange = { viewModel.updateOpacityIdle(it) },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    thumbColor = activeTheme.accent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Idle dock timeout customizer
            Text(
                text = "Docking Idle Timer: ${settings.idleTimeoutSeconds} seconds",
                fontSize = 12.sp,
                color = activeTheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.idleTimeoutSeconds.toFloat(),
                valueRange = 2f..10f,
                onValueChange = { viewModel.updateIdleTimeout(it.roundToInt()) },
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTheme.primary,
                    thumbColor = activeTheme.accent
                )
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = activeTheme.onSurface.copy(alpha = 0.1f)
            )

            // Switch option: Haptic Feedback
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateHaptic(!settings.hapticFeedback) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Haptic Vibrations", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.onSurface)
                    Text(text = "Buzz device on widget clicks and snaps", fontSize = 11.sp, color = activeTheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = settings.hapticFeedback,
                    onCheckedChange = { viewModel.updateHaptic(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activeTheme.accent,
                        checkedTrackColor = activeTheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Switch option: Hide when idle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateHideToCornerWhenIdle(!settings.hideToCornerWhenIdle) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Partially Hide on Corner", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.onSurface)
                    Text(text = "Snap partially off-screen & dim when idle", fontSize = 11.sp, color = activeTheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = settings.hideToCornerWhenIdle,
                    onCheckedChange = { viewModel.updateHideToCornerWhenIdle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activeTheme.accent,
                        checkedTrackColor = activeTheme.primary
                    )
                )
            }
        }
    }
}
