package com.personalip.app.ui.gallery

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import coil.compose.AsyncImage
import com.personalip.app.data.gallery.AlbumBucket
import com.personalip.app.data.gallery.MediaGallery
import com.personalip.app.data.gallery.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自定义相册选择器：
 *  1) 先展示系统全部相册（通过 MediaStore 查询，无数量限制）
 *  2) 点进相册 → 网格多选图片/视频
 *  3) 确认后把选中的 Uri 列表返回给调用方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<android.net.Uri>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 页面状态
    var albums by remember { mutableStateOf<List<AlbumBucket>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<AlbumBucket?>(null) }
    var albumMedia by remember { mutableStateOf<List<MediaItem>?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 加载所有相册
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) { MediaGallery.queryAllAlbums(context) }
        }.onSuccess { albums = it }
            .onFailure { error = it.message ?: "加载相册失败" }
    }

    // 加载相册内的媒体
    LaunchedEffect(selectedAlbum) {
        if (selectedAlbum != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    MediaGallery.queryMediaInAlbum(context, selectedAlbum!!.id)
                }
            }.onSuccess { albumMedia = it }
                .onFailure { error = it.message ?: "加载相册内容失败" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty() || selectedAlbum != null,
                onClick = {
                    // 如果在网格页且有选中
                    if (selectedAlbum != null && selectedIds.isNotEmpty()) {
                        val uris = (albumMedia ?: emptyList())
                            .filter { selectedIds.contains(it.id) }
                            .map { it.uri }
                        onConfirm(uris)
                    } else if (selectedAlbum != null) {
                        // 网格页没选 → 默认全选
                        val uris = (albumMedia ?: emptyList()).map { it.uri }
                        onConfirm(uris)
                    }
                }
            ) {
                Text(
                    if (selectedAlbum != null && selectedIds.isNotEmpty())
                        "确定 (${selectedIds.size})"
                    else if (selectedAlbum != null)
                        "确定 (${albumMedia?.size ?: 0})"
                    else
                        "取消"
                )
            }
        },
        dismissButton = {
            if (selectedAlbum != null) {
                TextButton(onClick = { selectedAlbum = null; selectedIds = emptySet() }) {
                    Text("返回相册列表")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = {
            Text(
                text = if (selectedAlbum != null) selectedAlbum!!.name else "选择相册",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                when {
                    error != null -> {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    albums == null || (selectedAlbum != null && albumMedia == null) -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    selectedAlbum == null -> {
                        AlbumGridView(
                            albums = albums ?: emptyList(),
                            onPick = { selectedAlbum = it }
                        )
                    }

                    else -> {
                        MediaGridView(
                            media = albumMedia ?: emptyList(),
                            selectedIds = selectedIds,
                            onToggle = { id ->
                                selectedIds = if (selectedIds.contains(id))
                                    selectedIds - id else selectedIds + id
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AlbumGridView(
    albums: List<AlbumBucket>,
    onPick: (AlbumBucket) -> Unit,
) {
    if (albums.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("未找到相册")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(album) }
            ) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "${album.count} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaGridView(
    media: List<MediaItem>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (media.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("此相册为空")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(media, key = { it.id }) { item ->
            val selected = selectedIds.contains(item.id)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(item.id) }
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )

                // 视频标记
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = "视频",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }

                // 选中标记
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "已选中" else "未选中",
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                )

                // 选中遮罩
                if (selected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    )
                }
            }
        }
    }
}
