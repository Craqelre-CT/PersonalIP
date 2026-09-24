package com.personalip.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalip.app.ui.ai.AiConfigDialog

/**
 * 设置页：人设、根目录、AI 接口、冷却期、备份导出、重新选择根文件夹、新建分类。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val cooldownDays by viewModel.cooldownDays.collectAsStateWithLifecycle()
    val rootTreeUri by viewModel.rootTreeUri.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    var showAiConfig by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showReselect by remember { mutableStateOf(false) }
    // 重新选择根文件夹时的迁移意图，在 SAF 选择器回调中使用。
    var migrateOnReselect by remember { mutableStateOf(false) }

    // SAF 文件夹选择器（用于重新选择根文件夹）。
    val reselectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.reselectRoot(uri, migrateOnReselect)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("根目录") {
                Text(
                    "当前根文件夹 Uri：",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    rootTreeUri ?: "未授权",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showReselect = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新选择根文件夹") }
            }

            SettingsSection("AI 接口") {
                Button(
                    onClick = { showAiConfig = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("配置 baseUrl / 模型 / API Key") }
                Text(
                    "API Key 本地加密保存，支持 DeepSeek、通义、Kimi、智谱、OpenAI 等 OpenAI 兼容接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection("冷却期") {
                Text(
                    "避免短期内重复使用同一素材",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = cooldownDays == days,
                            onClick = { viewModel.setCooldown(days) },
                            label = { Text("${days}天") }
                        )
                    }
                }
            }

            SettingsSection("人设") {
                PersonaEditor(
                    persona = persona,
                    onUpdate = viewModel::updatePersona,
                    onUpdateForbidden = viewModel::updateForbiddenWords,
                    onSave = viewModel::savePersona
                )
            }

            SettingsSection("素材分类") {
                Button(
                    onClick = { showNewCategory = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("新建分类") }
            }

            SettingsSection("备份") {
                Button(
                    onClick = viewModel::exportBackup,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(if (busy) " 导出中…" else " 导出 Zip 备份")
                }
                Text(
                    "备份将保存到根目录 备份/ 下，排除 备份 目录自身避免递归。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAiConfig) {
        AiConfigDialog(onDismiss = { showAiConfig = false })
    }

    if (showNewCategory) {
        NewCategoryDialog(
            onConfirm = { name -> viewModel.createCategory(name); showNewCategory = false },
            onDismiss = { showNewCategory = false }
        )
    }

    if (showReselect) {
        ReselectRootDialog(
            onConfirm = { migrate ->
                showReselect = false
                migrateOnReselect = migrate
                // 弹出 SAF 文件夹选择器。
                reselectLauncher.launch(null)
            },
            onDismiss = { showReselect = false }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PersonaEditor(
    persona: com.personalip.app.data.local.entity.PersonaEntity,
    onUpdate: (PersonaField, String) -> Unit,
    onUpdateForbidden: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = persona.nickname,
            onValueChange = { onUpdate(PersonaField.NICKNAME, it) },
            label = { Text("昵称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.identity,
            onValueChange = { onUpdate(PersonaField.IDENTITY, it) },
            label = { Text("身份") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.background,
            onValueChange = { onUpdate(PersonaField.BACKGROUND, it) },
            label = { Text("专业背景") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.personality,
            onValueChange = { onUpdate(PersonaField.PERSONALITY, it) },
            label = { Text("性格") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.catchphrase,
            onValueChange = { onUpdate(PersonaField.CATCHPHRASE, it) },
            label = { Text("口头禅") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.targetAudience,
            onValueChange = { onUpdate(PersonaField.TARGET_AUDIENCE, it) },
            label = { Text("目标客户") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = persona.forbiddenWords.joinToString("\n"),
            onValueChange = onUpdateForbidden,
            label = { Text("禁用词（每行一个或用逗号分隔）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存人设")
        }
    }
}

@Composable
private fun NewCategoryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名（将作为素材库子文件夹名）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ReselectRootDialog(
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新选择根文件夹") },
        text = { Text("是否将旧 PersonalIP 数据迁移到新位置？建议选择「迁移」。") },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) { Text("迁移后切换") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) { Text("不迁移") }
        }
    )
}
