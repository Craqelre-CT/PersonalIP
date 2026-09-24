package com.personalip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalip.app.data.compliance.ComplianceHit
import com.personalip.app.data.compliance.Level
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.schedule.WeekIdUtil
import kotlinx.coroutines.launch

/**
 * 今日待发：首页展示当天要发的朋友圈。
 *
 * - 点击「复制文案」→ 复制到系统剪贴板。
 * - 点击「打开微信分享」→ 系统分享（默认拉起微信；未安装则选择器）。
 * - 标记：草稿、待发、已发、跳过。
 *
 * 不做自动发布朋友圈，只做复制到剪贴板和系统分享。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val weekId = WeekIdUtil.weekIdOf()
    val dayLabel = WeekIdUtil.dayLabelOf()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val copyMessage by viewModel.copyMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 合规检测弹窗：复制 / 分享前若命中风险词则提示用户。
    var complianceHits by remember { mutableStateOf<List<ComplianceHit>?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(copyMessage) {
        copyMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("今日待发 · $dayLabel（$weekId）") }) }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("今天没有排期", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "请先到「排期」页生成本周计划并 AI 生成文案",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.plan.id }) { item ->
                    TodayPostCard(
                        item = item,
                        onCopy = {
                            item.post?.let { post ->
                                // 复制前做合规检测：命中风险词时提示用户。
                                scope.launch {
                                    val hits = viewModel.checkCompliance(post)
                                    if (hits.isEmpty()) {
                                        viewModel.copyText(post)
                                    } else {
                                        complianceHits = hits
                                        pendingAction = { viewModel.copyText(post) }
                                    }
                                }
                            }
                        },
                        onShareWeChat = {
                            item.post?.let { post ->
                                // 分享前做合规检测。
                                scope.launch {
                                    val hits = viewModel.checkCompliance(post)
                                    if (hits.isEmpty()) {
                                        shareToWeChat(context, viewModel, post, snackbarHostState, scope)
                                    } else {
                                        complianceHits = hits
                                        pendingAction = {
                                            shareToWeChat(context, viewModel, post, snackbarHostState, scope)
                                        }
                                    }
                                }
                            }
                        },
                        onMarkSent = { viewModel.setStatus(item.plan, PostStatus.SENT) },
                        onSkip = { viewModel.setStatus(item.plan, PostStatus.SKIPPED) },
                        onPending = { viewModel.setStatus(item.plan, PostStatus.PENDING) }
                    )
                }
            }
        }
    }

    // 合规风险提示弹窗：命中风险词时让用户决定是否继续。
    complianceHits?.let { hits ->
        val hasHardBlock = hits.any { it.level == Level.HARD_BLOCK }
        AlertDialog(
            onDismissRequest = {
                complianceHits = null
                pendingAction = null
            },
            title = { Text("⚠ 合规风险提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (hasHardBlock) "文案命中硬阻断风险词，强烈建议修改后再发布。"
                        else "文案存在合规风险词，请确认后继续。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    hits.forEach { hit ->
                        Text(
                            "• ${hit.word}（${hit.label}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hit.level == Level.HARD_BLOCK)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingAction
                    complianceHits = null
                    pendingAction = null
                    action?.invoke()
                }) { Text(if (hasHardBlock) "仍然继续" else "继续") }
            },
            dismissButton = {
                TextButton(onClick = {
                    complianceHits = null
                    pendingAction = null
                }) { Text("取消") }
            }
        )
    }
}

/**
 * 拉起微信分享；未安装微信时回退到系统分享选择器。
 */
private fun shareToWeChat(
    context: android.content.Context,
    viewModel: HomeViewModel,
    post: com.personalip.app.data.local.entity.PostEntity,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val intent = viewModel.buildShareIntent(post)
    runCatching { context.startActivity(intent) }
        .onFailure {
            // 未安装微信，回退到系统分享选择器。
            val chooser = viewModel.buildShareChooserIntent(post)
            runCatching { context.startActivity(chooser) }
                .onFailure {
                    scope.launch { snackbarHostState.showSnackbar("没有可用的分享应用") }
                }
        }
}

@Composable
private fun TodayPostCard(
    item: HomeItem,
    onCopy: () -> Unit,
    onShareWeChat: () -> Unit,
    onMarkSent: () -> Unit,
    onSkip: () -> Unit,
    onPending: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 缩略图
                if (item.materialUri != null) {
                    AsyncImage(
                        model = item.materialUri,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        "${item.plan.time} · ${item.categoryDisplay}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        item.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = {}, label = { Text(item.statusLabel) })
            }

            if (item.post == null) {
                Text(
                    "尚未生成文案，请到「排期」页点击 ✨ 生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    item.post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.post.tags.isNotEmpty()) {
                    Text(
                        item.post.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item.post.imageSuggestion?.let {
                    Text(
                        "配图建议：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.post.alternative1?.let {
                    Text(
                        "备选1：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 合规提醒：健康类内容必须附带。
                Text(
                    "⚠ 合规提醒：本内容仅供参考，效果因人而异，严重情况请咨询专业人士。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCopy,
                    enabled = item.hasPost,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(" 复制文案")
                }
                Button(
                    onClick = onShareWeChat,
                    enabled = item.hasPost,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" 微信分享")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPending, modifier = Modifier.weight(1f)) {
                    Text("待发")
                }
                OutlinedButton(onClick = onMarkSent, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Text(" 已发")
                }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                    Text(" 跳过")
                }
            }
        }
    }
}
