package com.personalip.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalip.app.data.ai.AiProvider

/**
 * AI 接口配置对话框。
 *
 * 优化：内置 5 家常见提供商（DeepSeek/通义/Kimi/智谱/OpenAI）+ 自定义，
 * 选提供商即自动填充 baseUrl、文案模型和图片识别模型，用户只需填 API Key。
 *
 * 流程对应 moments-writer：图片识别模型先看图说话，文案模型再写朋友圈。
 */
@Composable
fun AiConfigDialog(
    onDismiss: () -> Unit,
    viewModel: AiConfigViewModel = hiltViewModel()
) {
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val visionModel by viewModel.visionModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) { viewModel.consumeSaved(); onDismiss() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 接口配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ① 提供商选择
                Text("选择提供商", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiProvider.entries) { provider ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = { viewModel.selectProvider(provider) },
                            label = { Text(provider.displayName) }
                        )
                    }
                }

                // ② Base URL（选提供商后自动填充，自定义可手输）
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text(selectedProvider.baseUrl.ifBlank { "https://api.example.com/v1" }) }
                )

                // ③ 图片识别模型（用于多模态：图片 → 文字描述）
                OutlinedTextField(
                    value = visionModel,
                    onValueChange = viewModel::setVisionModel,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("图片识别模型") },
                    placeholder = {
                        Text(
                            if (selectedProvider.visionModels.isNotEmpty())
                                selectedProvider.visionModels.joinToString(" / ")
                            else "未配置（仅文本素材时跳过）"
                        )
                    },
                    supportingText = {
                        if (selectedProvider.visionModels.isNotEmpty()) {
                            Text("推荐：${selectedProvider.visionModels.joinToString("、")}（用于图片识别）")
                        } else if (selectedProvider != AiProvider.CUSTOM) {
                            Text("该提供商暂无视觉模型，仅支持文字素材")
                        }
                    }
                )

                // ④ 文案生成模型
                OutlinedTextField(
                    value = model,
                    onValueChange = viewModel::setModel,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文案生成模型") },
                    placeholder = {
                        Text(
                            if (selectedProvider.textModels.isNotEmpty())
                                selectedProvider.textModels.joinToString(" / ")
                            else "deepseek-chat"
                        )
                    },
                    supportingText = {
                        if (selectedProvider.textModels.isNotEmpty()) {
                            Text("推荐：${selectedProvider.textModels.joinToString("、")}")
                        }
                    }
                )

                // ⑤ API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = viewModel::setApiKey,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key（加密存储）") },
                    placeholder = { Text(selectedProvider.keyPlaceholder) },
                    supportingText = {
                        if (selectedProvider.signupUrl.isNotEmpty()) {
                            Text("获取 Key：${selectedProvider.signupUrl}")
                        }
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = viewModel::save) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
