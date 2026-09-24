package com.personalip.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalip.app.data.ai.Goal
import com.personalip.app.data.ai.Tone
import com.personalip.app.data.local.PostStatus
import com.personalip.app.ui.ai.AiConfigDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val weekId = viewModel.weekId
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val slotTimes by viewModel.slotTimes.collectAsStateWithLifecycle()
    val cooldownDays by viewModel.cooldownDays.collectAsStateWithLifecycle()
    val selectedCategoryIds by viewModel.selectedCategoryIds.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val generatingPlanId by viewModel.generatingPlanId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // AI 配置 / 生成对话框状态。
    var showAiConfig by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf<Long?>(null) }
    var selectedTone by remember { mutableStateOf(Tone.WARM) }
    var selectedGoal by remember { mutableStateOf(Goal.LIKE) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("一周排期（$weekId）") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 配置区
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("生成配置", style = MaterialTheme.typography.titleMedium)

                        Text("每日发布时间（闹钟式）", style = MaterialTheme.typography.labelLarge)
                        slotTimes.forEachIndexed { index, time ->
                            TimeSlotRow(
                                time = time,
                                onChange = { viewModel.setSlotTime(index, it) },
                                onRemove = { viewModel.removeSlotTime(index) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.addSlotTime() }) {
                                Icon(Icons.Filled.Add, contentDescription = "增加时段")
                            }
                            Text(
                                "共 ${slotTimes.size} 条/天 × 7 天 = ${slotTimes.size * 7} 条",
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "修改时间后自动按时间排序",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("冷却期（避免短期内重复使用）", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { days ->
                                FilterChip(
                                    selected = cooldownDays == days,
                                    onClick = { viewModel.setCooldown(days) },
                                    label = { Text("${days}天") }
                                )
                            }
                        }

                        Text("参与分类（不选则全部）", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategoryIds.contains(cat.id),
                                    onClick = { viewModel.toggleCategory(cat.id) },
                                    label = { Text(cat.displayName) }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.generate() },
                            enabled = !generating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (generating) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp), strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            }
                            Text(if (generating) " 生成中…" else " 生成本周计划")
                        }

                        // AI 接口配置入口（baseUrl / 模型 / API Key）。
                        Button(
                            onClick = { showAiConfig = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("AI 接口配置")
                        }
                    }
                }
            }

            // 排期列表
            items(items, key = { it.plan.id }) { item ->
                ScheduleCard(
                    item = item,
                    onMoveUp = { viewModel.movePlan(item.plan.id, up = true) },
                    onMoveDown = { viewModel.movePlan(item.plan.id, up = false) },
                    onReplace = { viewModel.replaceMaterial(item.plan.id) },
                    onGeneratePost = { showGenerateDialog = item.plan.id },
                    onMarkSent = { viewModel.setStatus(item.plan.id, PostStatus.SENT) },
                    onSkip = { viewModel.setStatus(item.plan.id, PostStatus.SKIPPED) }
                )
            }
        }
    }

    // AI 接口配置对话框。
    if (showAiConfig) {
        AiConfigDialog(onDismiss = { showAiConfig = false })
    }

    // 生成朋友圈对话框（选语气 / 转化目标）。
    showGenerateDialog?.let { planId ->
        AlertDialog(
            onDismissRequest = { showGenerateDialog = null },
            title = { Text("生成朋友圈文案") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择语气和转化目标", style = MaterialTheme.typography.bodyMedium)
                    Text("语气", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Tone.entries) { tone ->
                            FilterChip(
                                selected = selectedTone == tone,
                                onClick = { selectedTone = tone },
                                label = { Text(tone.label) }
                            )
                        }
                    }
                    Text("转化目标", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Goal.entries) { goal ->
                            FilterChip(
                                selected = selectedGoal == goal,
                                onClick = { selectedGoal = goal },
                                label = { Text(goal.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.generatePost(planId, selectedTone, selectedGoal)
                        showGenerateDialog = null
                    },
                    enabled = !generating
                ) {
                    if (generating && generatingPlanId == planId) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("生成")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotRow(
    time: String,
    onChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val (h, m) = remember(time) {
        val parts = time.split(":")
        (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    var pickerHour by remember(time) { mutableStateOf(h) }
    var pickerMinute by remember(time) { mutableStateOf(m) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = { showDialog = true },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "%02d:%02d".format(h, m),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除时段",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = pickerHour,
            initialMinute = pickerMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择时间") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    item: ScheduleItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReplace: () -> Unit,
    onGeneratePost: () -> Unit,
    onMarkSent: () -> Unit,
    onSkip: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 缩略图
            if (item.materialUri != null) {
                AsyncImage(
                    model = item.materialUri,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${item.dayLabel} ${item.plan.time}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    item.material?.let { "分类：${item.categoryDisplay} · 用${it.useCount}次" }
                        ?: "素材缺失",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.material?.tags?.isNotEmpty() == true) {
                    Text(
                        "#${item.material.tags.take(3).joinToString(" #")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(item.statusLabel) }
                )
            }
            Column {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下移")
                }
                IconButton(onClick = onReplace) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "换素材")
                }
                IconButton(onClick = onGeneratePost) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "生成文案")
                }
                IconButton(onClick = onMarkSent) { Text("✓") }
                IconButton(onClick = onSkip) { Text("跳") }
            }
        }
    }
}
