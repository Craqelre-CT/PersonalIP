package com.personalip.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalip.app.domain.model.CategorySettings
import com.personalip.app.ui.gallery.AlbumPickerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val folderQuery by viewModel.folderQuery.collectAsStateWithLifecycle()
    val filteredCategories by viewModel.filteredCategories.collectAsStateWithLifecycle()
    val materialQuery by viewModel.materialQuery.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // 通用状态
    var showFolderSettingsFor by remember { mutableStateOf<Long?>(null) }
    var showFolderDeleteFor by remember { mutableStateOf<Long?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<MaterialDisplayItem?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
    var pendingImportCatId by remember { mutableStateOf<Long?>(null) }
    var showAlbumPicker by remember { mutableStateOf(false) }

    // 请求存储权限的 launcher：授权后弹出自定义相册选择器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            showAlbumPicker = true
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("需要存储权限才能访问相册")
            }
            pendingImportCatId = null
        }
    }

    fun checkAndRequestPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // 预检查：已授权则直接打开相册选择器，不重复请求
        val allGranted = perms.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            showAlbumPicker = true
        } else {
            permissionLauncher.launch(perms)
        }
    }

    fun startImport(catId: Long?) {
        pendingImportCatId = catId
        checkAndRequestPermission()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentFolder == null) {
                TopAppBar(title = { Text("素材文件夹") })
            } else {
                TopAppBar(
                    title = { Text(currentFolder?.displayName ?: "素材") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.backToFolders() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFolderSettingsFor = currentFolder?.id }) {
                            Icon(Icons.Filled.Settings, contentDescription = "文件夹设置")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentFolder == null) {
                ExtendedFloatingActionButton(
                    onClick = { showNewFolderDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "新建文件夹") },
                    text = { Text("新建文件夹") }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { startImport(currentFolder?.id) },
                    icon = {
                        if (importing) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                        ) else Icon(Icons.Filled.Add, contentDescription = "导入")
                    },
                    text = { Text(if (importing) "导入中…" else "从相册导入") }
                )
            }
        }
    ) { padding ->
        if (currentFolder == null) {
            FolderListPage(
                padding = padding,
                categories = categories,
                filteredCategories = filteredCategories,
                folderQuery = folderQuery,
                showSearch = categories.size > 4,
                onQueryChange = viewModel::setFolderQuery,
                onEnter = { viewModel.enterFolder(it) },
                onImport = { id -> startImport(id) },
                onSettings = { showFolderSettingsFor = it },
                onDelete = { showFolderDeleteFor = it }
            )
        } else {
            MaterialListPage(
                padding = padding,
                folderName = currentFolder?.displayName ?: "",
                items = items,
                query = materialQuery,
                onQueryChange = viewModel::setMaterialQuery,
                allTags = allTags,
                tagFilter = tagFilter,
                onToggleTag = viewModel::toggleTagFilter,
                onItemClick = { actionItem = it }
            )
        }
    }

    // 新建文件夹对话框
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val id = viewModel.createFolder(name)
                    if (id != null) {
                        showNewFolderDialog = false
                        viewModel.enterFolder(id)
                    }
                }
            }
        )
    }

    // 文件夹设置弹窗
    showFolderSettingsFor?.let { folderId ->
        FolderSettingsDialog(
            folderId = folderId,
            onDismiss = { showFolderSettingsFor = null },
            onLoad = { viewModel.getFolderSettings(folderId) },
            onSave = { newName, settings ->
                scope.launch {
                    if (newName.isNotBlank()) viewModel.renameFolder(folderId, newName)
                    viewModel.updateFolderSettings(folderId, settings)
                    showFolderSettingsFor = null
                }
            },
            onDelete = {
                showFolderSettingsFor = null
                showFolderDeleteFor = folderId
            }
        )
    }

    // 删除文件夹确认
    showFolderDeleteFor?.let { folderId ->
        val cat = categories.firstOrNull { it.id == folderId }
        AlertDialog(
            onDismissRequest = { showFolderDeleteFor = null },
            title = { Text("删除文件夹") },
            text = {
                Text("将永久删除「${cat?.displayName}」及其下全部素材、排期和使用记录，不可恢复。确定继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.deleteFolder(folderId)
                        showFolderDeleteFor = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteFor = null }) { Text("取消") }
            }
        )
    }

    // 素材操作弹窗
    actionItem?.let { item ->
        MaterialActionsSheet(
            item = item,
            onDismiss = { actionItem = null },
            onCopyOcr = {
                item.entity.ocrText?.takeIf { it.isNotBlank() }?.let {
                    clipboard.setText(AnnotatedString(it))
                }
                actionItem = null
            },
            onReRunOcr = { viewModel.reRunOcr(item.entity.id); actionItem = null },
            onEditTags = {
                tagDraft = item.entity.tags.joinToString(", ")
                showTagDialog = true
            },
            onDelete = { viewModel.deleteMaterial(item.entity.id); actionItem = null }
        )
    }

    // 标签编辑对话框
    if (showTagDialog) {
        actionItem?.let { item ->
            AlertDialog(
                onDismissRequest = { showTagDialog = false },
                title = { Text("编辑标签") },
                text = {
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        singleLine = false,
                        placeholder = { Text("用英文逗号分隔，如：减重,对比,前后") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val tags = tagDraft.split(",")
                            .map { it.trim() }.filter { it.isNotBlank() }
                        viewModel.updateTags(item.entity.id, tags)
                        showTagDialog = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showTagDialog = false }) { Text("取消") }
                }
            )
        }
    }

    // 自定义相册选择器（展示系统全部相册）
    if (showAlbumPicker) {
        AlbumPickerDialog(
            onDismiss = {
                showAlbumPicker = false
                pendingImportCatId = null
            },
            onConfirm = { uris ->
                showAlbumPicker = false
                if (uris.isNotEmpty()) {
                    viewModel.importUris(uris, pendingImportCatId)
                }
                pendingImportCatId = null
            }
        )
    }
}

// ===================== 文件夹列表页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderListPage(
    padding: PaddingValues,
    categories: List<com.personalip.app.data.local.entity.CategoryEntity>,
    filteredCategories: List<com.personalip.app.data.local.entity.CategoryEntity>,
    folderQuery: String,
    showSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onEnter: (Long) -> Unit,
    onImport: (Long) -> Unit,
    onSettings: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        if (showSearch) {
            OutlinedTextField(
                value = folderQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("🔍 搜索文件夹") }
            )
        }

        if (filteredCategories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (folderQuery.isBlank()) "暂无文件夹，点右下角新建"
                        else "未匹配到文件夹",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredCategories, key = { it.id }) { cat ->
                FolderCard(
                    name = cat.displayName,
                    count = -1, // 占位，由异步查询补足
                    onEnter = { onEnter(cat.id) },
                    onImport = { onImport(cat.id) },
                    onSettings = { onSettings(cat.id) },
                    onDelete = { onDelete(cat.id) },
                    canDelete = categories.size > 1
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    name: String,
    count: Int,
    onEnter: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("📥 相册", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("设置", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (canDelete) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("删除", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ===================== 素材列表页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialListPage(
    padding: PaddingValues,
    folderName: String,
    items: List<MaterialDisplayItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    allTags: List<String>,
    tagFilter: List<String>,
    onToggleTag: (String) -> Unit,
    onItemClick: (MaterialDisplayItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            singleLine = true,
            placeholder = { Text("搜索「$folderName」内的标签 / OCR / 文件名") }
        )

        if (allTags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTags, key = { it }) { tag ->
                    FilterChip(
                        selected = tagFilter.contains(tag),
                        onClick = { onToggleTag(tag) },
                        label = { Text("#$tag") }
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (query.isBlank() && tagFilter.isEmpty())
                            "该文件夹暂无素材，点右下角从相册导入"
                        else "无匹配素材",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.entity.id }) { item ->
                MaterialGridItem(item) { onItemClick(item) }
            }
        }
    }
}

@Composable
private fun MaterialGridItem(
    item: MaterialDisplayItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (item.isImage && item.uri != null) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.entity.mimeType.startsWith("video/") -> Icons.Filled.PlayCircle
                        item.entity.mimeType.startsWith("text/") -> Icons.Filled.Article
                        else -> Icons.Filled.Image
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = item.categoryDisplay,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color(0x88000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "用 ${item.entity.useCount}",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(Color(0x88000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MaterialActionsSheet(
    item: MaterialDisplayItem,
    onDismiss: () -> Unit,
    onCopyOcr: () -> Unit,
    onReRunOcr: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("素材操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("分类：${item.categoryDisplay}")
                Text("使用次数：${item.entity.useCount}")
                if (item.entity.tags.isNotEmpty()) {
                    Text("标签：${item.entity.tags.joinToString("、")}")
                }
                if (item.entity.ocrText.isNullOrBlank()) {
                    Text("OCR：无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "OCR：${item.entity.ocrText!!.take(60)}…",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onEditTags) { Text("编辑标签") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.isImage) TextButton(onClick = onReRunOcr) { Text("重新OCR") }
                if (!item.entity.ocrText.isNullOrBlank()) TextButton(onClick = onCopyOcr) { Text("复制文字") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

// ===================== 新建文件夹对话框 =====================

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("如：男生大体重") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) })
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("新建并进入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===================== 文件夹设置弹窗 =====================

@Composable
private fun FolderSettingsDialog(
    folderId: Long,
    onDismiss: () -> Unit,
    onLoad: suspend () -> CategorySettings,
    onSave: (newName: String, settings: CategorySettings) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<CategorySettings?>(null) }
    var newName by remember { mutableStateOf("") }
    var autoOcr by remember { mutableStateOf(true) }
    var autoTags by remember { mutableStateOf(true) }
    var postTone by remember { mutableStateOf("亲和") }
    var postGoal by remember { mutableStateOf("点赞") }
    var cooldownDays by remember { mutableStateOf("7") }

    LaunchedEffect(folderId) {
        val s = onLoad()
        loaded = s
        autoOcr = s.autoOcr
        autoTags = s.autoTags
        postTone = s.postTone
        postGoal = s.postGoal
        cooldownDays = (s.cooldownDays ?: 7).toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件夹设置") },
        text = {
            if (loaded == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("加载中…")
                }
                return@AlertDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("导入图片自动 OCR")
                    Switch(checked = autoOcr, onCheckedChange = { autoOcr = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("导入自动生成标签")
                    Switch(checked = autoTags, onCheckedChange = { autoTags = it })
                }
                OutlinedTextField(
                    value = postTone,
                    onValueChange = { postTone = it },
                    label = { Text("默认语气（亲和/真实/幽默/专业/激励）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = postGoal,
                    onValueChange = { postGoal = it },
                    label = { Text("默认目标（点赞/评论/咨询/转发）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cooldownDays,
                    onValueChange = { cooldownDays = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("同素材冷却期（天，1-90）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cd = cooldownDays.toIntOrNull()?.coerceIn(1, 90) ?: 7
                onSave(
                    newName,
                    CategorySettings(
                        autoOcr = autoOcr,
                        autoTags = autoTags,
                        postTone = postTone,
                        postGoal = postGoal,
                        cooldownDays = cd
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text("删除文件夹", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
