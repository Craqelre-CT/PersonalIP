package com.personalip.app.ui.schedule

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalip.app.data.ai.CaptionStyle

/**
 * 两阶段交互式生成对话框（对齐 moments-writer 网页版流程）。
 *
 * 阶段一：用户输入识别方向 → 调视觉模型识别图片 → 展示识别结果 → 确认/重新识别
 * 阶段二：用户输入意图 + 选风格 → 调文本模型写文案 → 展示文案 → 完成/重写
 *
 * 状态由 [ScheduleViewModel.genStep] 驱动，本组件只管理输入框和当前阶段切换。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateDialog(
    planId: Long,
    materialId: Long?,
    materialUri: Uri?,
    categoryDisplay: String,
    viewModel: ScheduleViewModel,
    onDismiss: () -> Unit
) {
    val genStep by viewModel.genStep.collectAsStateWithLifecycle()

    // 本地状态：当前在哪个阶段 + 输入框内容
    var step by remember { mutableStateOf(1) }
    var direction by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(CaptionStyle.SEED) }

    // 识别成功后自动切到确认视图（但仍在阶段一，等用户点"下一步"）
    // 写文案完成后自动切到阶段二结果视图
    LaunchedEffect(genStep) {
        if (genStep is GenStep.Done) {
            // 留在 step 2，展示结果
        }
    }

    val scrollState = rememberScrollState()
    val isRecognizing = genStep is GenStep.Recognizing
    val isWriting = genStep is GenStep.Writing
    val recognized = genStep as? GenStep.Recognized
    val done = genStep as? GenStep.Done
    val error = genStep as? GenStep.Error

    androidx.compose.ui.window.Dialog(onDismissRequest = {
        viewModel.resetGen()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 600.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // —— 顶部：缩略图 + 分类 + 步骤指示器 ——
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (materialUri != null) {
                        AsyncImage(
                            model = materialUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "分类：$categoryDisplay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (step == 1) "步骤 1 / 2：图片识别" else "步骤 2 / 2：写文案",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    // 步骤切换指示
                    StepIndicator(step = step)
                }

                when (step) {
                    1 -> StepRecognize(
                        direction = direction,
                        onDirectionChange = { direction = it },
                        isRecognizing = isRecognizing,
                        recognized = recognized,
                        error = error,
                        onStartRecognize = {
                            if (materialId != null) {
                                viewModel.recognize(materialId, direction)
                            }
                        },
                        onRetry = {
                            if (materialId != null) {
                                viewModel.recognize(materialId, direction)
                            }
                        },
                        onNext = { step = 2 }
                    )
                    2 -> StepWriteCaption(
                        recognizedContent = recognized?.content,
                        intent = intent,
                        onIntentChange = { intent = it },
                        selectedStyle = selectedStyle,
                        onStyleChange = { selectedStyle = it },
                        isWriting = isWriting,
                        done = done,
                        error = error,
                        onBack = { step = 1 },
                        onWrite = { viewModel.writeCaption(planId, intent, selectedStyle) },
                        onRetry = { viewModel.writeCaption(planId, intent, selectedStyle) },
                        onDone = {
                            viewModel.resetGen()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

// ============================ 阶段一：图片识别 ============================

@Composable
private fun StepRecognize(
    direction: String,
    onDirectionChange: (String) -> Unit,
    isRecognizing: Boolean,
    recognized: GenStep.Recognized?,
    error: GenStep.Error?,
    onStartRecognize: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit
) {
    // 识别方向输入框
    Text("告诉 AI 你想识别图片里的什么", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = direction,
        onValueChange = onDirectionChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "例：重点识别健身器材、动作、身材变化\n留空则让模型自由描述全部内容",
                style = MaterialTheme.typography.bodySmall
            )
        },
        minLines = 2,
        enabled = !isRecognizing
    )

    // 开始识别 / 加载中
    if (isRecognizing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("正在识别图片…", style = MaterialTheme.typography.bodyMedium)
        }
    } else if (recognized == null && error == null) {
        // Idle 状态
        androidx.compose.material3.Button(
            onClick = onStartRecognize,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始识别")
        }
    }

    // 识别结果展示
    if (recognized != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("识别结果", style = MaterialTheme.typography.labelMedium)
                Text(
                    recognized.content,
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Visible
                )
                if (recognized.direction.isNotBlank()) {
                    Text(
                        "识别方向：${recognized.direction}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 确认 / 重新识别 / 下一步
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("重新识别")
            }
            androidx.compose.material3.Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("下一步")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }

    // 错误展示
    if (error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("识别失败", style = MaterialTheme.typography.labelMedium)
                Text(error.message, style = MaterialTheme.typography.bodySmall)
            }
        }
        androidx.compose.material3.Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("重试")
        }
    }
}

// ============================ 阶段二：写文案 ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepWriteCaption(
    recognizedContent: String?,
    intent: String,
    onIntentChange: (String) -> Unit,
    selectedStyle: CaptionStyle,
    onStyleChange: (CaptionStyle) -> Unit,
    isWriting: Boolean,
    done: GenStep.Done?,
    error: GenStep.Error?,
    onBack: () -> Unit,
    onWrite: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    // 只读展示识别结果（摘要）
    if (recognizedContent != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已确认识别结果", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBack) {
                        Text("修改", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    recognizedContent,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (done == null) {
        // 意图输入框
        Text("你要表达什么意思？", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = intent,
            onValueChange = onIntentChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "例：今天练腿日，分享训练心得\n留空则让 AI 围绕图片内容自由发挥",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            minLines = 2,
            enabled = !isWriting
        )

        // 风格快捷按钮
        Text("文案风格", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionStyle.entries.forEach { style ->
                FilterChip(
                    selected = selectedStyle == style,
                    onClick = { onStyleChange(style) },
                    label = { Text(style.label) }
                )
            }
        }
        Text(
            selectedStyle.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 生成 / 加载中
        if (isWriting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在写文案…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("上一步")
                }
                androidx.compose.material3.Button(
                    onClick = onWrite,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成文案")
                }
            }
        }

        // 错误展示
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("生成失败", style = MaterialTheme.typography.labelMedium)
                    Text(error.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            androidx.compose.material3.Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("重试")
            }
        }
    } else {
        // —— 文案结果展示 ——
        CaptionResultCard(result = done.result, onDone = onDone)
    }
}

@Composable
private fun CaptionResultCard(
    result: com.personalip.app.data.ai.GenerateResult,
    onDone: () -> Unit
) {
    // 正文
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("正文", style = MaterialTheme.typography.labelMedium)
            Text(
                result.content ?: "（无内容）",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // 备选
    if (result.alternatives.isNotEmpty()) {
        Text("备选", style = MaterialTheme.typography.labelMedium)
        result.alternatives.forEachIndexed { index, alt ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "备选 ${index + 1}：$alt",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }

    // 标签
    if (result.tags.isNotEmpty()) {
        Text(
            result.tags.joinToString("  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    // 合规提醒
    result.complianceNote?.let {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    // 完成按钮
    androidx.compose.material3.Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.Check, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("完成")
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 圆点 1
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (step >= 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        )
        Spacer(Modifier.width(4.dp))
        // 连线
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .background(
                    if (step >= 2) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        )
        Spacer(Modifier.width(4.dp))
        // 圆点 2
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (step >= 2) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        )
    }
}
