package com.example

import android.os.Bundle
import android.text.format.Formatter
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.CyberDarkSurfaceVariant
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateGrey
import com.example.ui.theme.TerminalGreen

class MainActivity : ComponentActivity() {
    private val viewModel: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CyberDarkBg
                ) { innerPadding ->
                    EmulatorConsoleScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun EmulatorConsoleScreen(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val ramSize by viewModel.ramSizeGb.collectAsState()
    val cpuCores by viewModel.cpuCores.collectAsState()
    val sdCardSize by viewModel.sdCardSizeGb.collectAsState()
    val gpuRendering by viewModel.gpuRendering.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()

    val currentFileIndex by viewModel.currentFileIndex.collectAsState()
    val currentFileProgress by viewModel.currentFileProgress.collectAsState()
    val totalBytesWritten by viewModel.totalBytesWritten.collectAsState()
    val writeSpeedMbs by viewModel.writeSpeedMbs.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val freeStorage by viewModel.freeStorageBytes.collectAsState()
    val totalStorage by viewModel.totalStorageBytes.collectAsState()

    // Pulse animation for status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Scroll logs automatically when a new log arrives
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Refresh storage space on mount
    LaunchedEffect(Unit) {
        viewModel.updateStorageInfo(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
    ) {
        // --- HIGH TECH HEADER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberDarkSurface)
                .border(width = 1.dp, color = CyberDarkSurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .alpha(if (status == EmulatorStatus.CREATING) pulseAlpha else 1f)
                                .background(
                                    when (status) {
                                        EmulatorStatus.CREATING -> TerminalGreen
                                        EmulatorStatus.PAUSED -> CyberYellow
                                        EmulatorStatus.IDLE -> SlateGrey
                                        EmulatorStatus.ERROR -> CyberRed
                                        EmulatorStatus.COMPLETED -> CyberBlue
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ANDROID VMX BUILDER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Text(
                        text = "Среда разработки виртуальных симуляторов",
                        fontSize = 11.sp,
                        color = SlateGrey,
                        fontWeight = FontWeight.Normal
                    )
                }

                // Emulator Build Tag / Status
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = when (status) {
                                EmulatorStatus.CREATING -> TerminalGreen.copy(alpha = 0.5f)
                                EmulatorStatus.PAUSED -> CyberYellow.copy(alpha = 0.5f)
                                else -> CyberBlue.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(CyberDarkBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (status) {
                            EmulatorStatus.IDLE -> "GUEST IDLE"
                            EmulatorStatus.CREATING -> "PROVISIONING"
                            EmulatorStatus.PAUSED -> "PAUSED"
                            EmulatorStatus.ERROR -> "SYSTEM ERROR"
                            EmulatorStatus.COMPLETED -> "LAUNCHED"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (status) {
                            EmulatorStatus.CREATING -> TerminalGreen
                            EmulatorStatus.PAUSED -> CyberYellow
                            EmulatorStatus.ERROR -> CyberRed
                            else -> CyberBlue
                        }
                    )
                }
            }
        }

        // --- SCROLLABLE CONTAINER FOR TOP CONTROLS & GAUGES ---
        Column(
            modifier = Modifier
                .weight(1.2f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (status == EmulatorStatus.IDLE) {
                // Device Configuration Card (Shown only in Setup State)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = CyberBlue.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "СОЗДАНИЕ ВИРТУАЛЬНОГО ОКРУЖЕНИЯ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberBlue,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected Device Label
                        Text(
                            text = "Архитектура девайса: $deviceName",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // RAM Allocation Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Выделяемая RAM: $ramSize ГБ", fontSize = 12.sp, color = SlateGrey)
                            Text(
                                text = if (ramSize >= 12) "Максимальная" else "Оптимальная",
                                fontSize = 10.sp,
                                color = if (ramSize >= 12) CyberYellow else TerminalGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = ramSize.toFloat(),
                            onValueChange = { viewModel.setRamSize(it.toInt()) },
                            valueRange = 2f..16f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                activeTrackColor = CyberBlue,
                                thumbColor = CyberBlue,
                                inactiveTrackColor = CyberDarkBg
                            )
                        )

                        // CPU cores Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Ядера процессора (VCPU): $cpuCores Cores", fontSize = 12.sp, color = SlateGrey)
                            Text(
                                text = if (cpuCores >= 8) "Turbo" else "Normal",
                                fontSize = 10.sp,
                                color = if (cpuCores >= 8) CyberRed else CyberBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = cpuCores.toFloat(),
                            onValueChange = { viewModel.setCpuCores(it.toInt()) },
                            valueRange = 2f..12f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                activeTrackColor = CyberBlue,
                                thumbColor = CyberBlue,
                                inactiveTrackColor = CyberDarkBg
                            )
                        )

                        // SD Card Allocation Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Размер накопителя SD: $sdCardSize ГБ", fontSize = 12.sp, color = SlateGrey)
                        }
                        Slider(
                            value = sdCardSize.toFloat(),
                            onValueChange = { viewModel.setSdCardSize(it.toInt()) },
                            valueRange = 4f..128f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                activeTrackColor = CyberBlue,
                                thumbColor = CyberBlue,
                                inactiveTrackColor = CyberDarkBg
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = CyberDarkSurfaceVariant
                        )

                        // GPU Selection Choice Box
                        Text(
                            text = "Движок рендеринга:",
                            fontSize = 11.sp,
                            color = SlateGrey,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Vulkan 1.3 - Hardware", "OpenGL ES - Software").forEach { item ->
                                val isSelected = gpuRendering == item
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) CyberBlue.copy(alpha = 0.15f) else CyberDarkBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CyberBlue else CyberDarkSurfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.setGpuRendering(item) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else SlateGrey,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- TELEMETRY GAUGES CARD (Hidden in Setup, Active during creation/paused) ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (status == EmulatorStatus.CREATING) TerminalGreen.copy(alpha = 0.4f) else CyberYellow.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "АКТИВНЫЙ ПРОЦЕСС КЛИППИНГА И КОМПИЛЯЦИИ ROM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (status == EmulatorStatus.CREATING) TerminalGreen else CyberYellow,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Speed and written statistics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "СКОРОСТЬ ПРОШИВКИ",
                                    fontSize = 10.sp,
                                    color = SlateGrey,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f МБ/с", writeSpeedMbs),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (status == EmulatorStatus.CREATING) TerminalGreen else SlateGrey,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "ВЫДЕЛЕНО НА ДИСКЕ",
                                    fontSize = 10.sp,
                                    color = SlateGrey,
                                    fontFamily = FontFamily.Monospace
                                )
                                val formattedTotal = Formatter.formatShortFileSize(context, totalBytesWritten)
                                Text(
                                    text = formattedTotal,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress details of the current 200MB file
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Запись сегмента ROM (#$currentFileIndex):",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "%.0f%%", currentFileProgress * 100f),
                                fontSize = 11.sp,
                                color = TerminalGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Linear progress with cyberpunk glow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(CyberDarkBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(currentFileProgress)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(CyberBlue, TerminalGreen)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tiny text explaining partitions size in full details
                        Text(
                            text = "Структурное разбиение: блоки по 200.0 МБ на раздел. Всего создано разделов: $currentFileIndex",
                            fontSize = 10.sp,
                            color = SlateGrey,
                            onTextLayout = {}
                        )
                    }
                }
            }

            // --- STORAGE RESOURCES STATS CARD ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = CyberDarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Storage",
                        tint = CyberBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val freeSizeFormatted = Formatter.formatShortFileSize(context, freeStorage)
                        val totalSizeFormatted = Formatter.formatShortFileSize(context, totalStorage)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Хранилище хост-устройства", fontSize = 11.sp, color = SlateGrey)
                            Text(
                                text = "Свободно: $freeSizeFormatted / $totalSizeFormatted",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val storageRatio = if (totalStorage > 0) {
                            (totalStorage - freeStorage).toFloat() / totalStorage
                        } else 0f
                        LinearProgressIndicator(
                            progress = { storageRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (storageRatio > 0.9f) CyberRed else CyberBlue,
                            trackColor = CyberDarkBg
                        )
                    }
                }
            }

            // --- PRIMARY BUILD TRIGGERS & ACTIONS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status == EmulatorStatus.IDLE) {
                    Button(
                        onClick = { viewModel.startCreation(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("create_emulator_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Создать эмулятор Android 17",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    // Pause / Resume Toggle
                    Button(
                        onClick = {
                            if (status == EmulatorStatus.CREATING) {
                                viewModel.pauseCreation()
                            } else {
                                viewModel.resumeCreation(context)
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp)
                            .testTag("pause_resume_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status == EmulatorStatus.CREATING) CyberYellow else TerminalGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val iconImg = if (status == EmulatorStatus.CREATING) Icons.Default.Close else Icons.Default.PlayArrow
                        Icon(imageVector = iconImg, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status == EmulatorStatus.CREATING) "Приостановить" else "Возобновить сборку",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cancel & Sweep/Reset button
                    Button(
                        onClick = { viewModel.stopAndCleanup(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сброс / Стереть",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Hidden Utility button inside Setup mode to secure clean workspace manually
            if (status == EmulatorStatus.IDLE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberDarkSurfaceVariant.copy(alpha = 0.5f))
                        .clickable { viewModel.cleanAllCache(context) }
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Cache",
                            tint = SlateGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Очистить накопленную кэш-память образов системы",
                            fontSize = 11.sp,
                            color = SlateGrey,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- DEV LOGS CONSOLE SECTION (Primary half size) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .border(width = 1.dp, color = CyberDarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Console bar actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkSurface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = TerminalGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "КОНСОЛЬ СИСТЕМНОГО СБОРЩИКА (STDOUT / KERNEL)",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "СТРОК: ${logs.size}",
                        fontSize = 10.sp,
                        color = TerminalGreen.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[Системный журнал пуст. Запустите сборщик VM для вывода логов ядра]",
                            color = SlateGrey.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            LogItemRow(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: LogEntry) {
    val contentColor = when (log.type) {
        LogType.INFO -> CyberBlue
        LogType.DEBUG -> SlateGrey
        LogType.WARNING -> CyberYellow
        LogType.ERROR -> CyberRed
        LogType.SUCCESS -> TerminalGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = "[${log.timestamp}]",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = SlateGrey.copy(alpha = 0.7f),
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(82.dp)
        )

        // Tag
        Text(
            text = "[${log.tag}]",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = contentColor.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Message
        Text(
            text = log.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (log.type == LogType.DEBUG) SlateGrey else Color.White,
            fontWeight = if (log.type == LogType.SUCCESS) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 14.sp
        )
    }
}
