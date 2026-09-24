package com.personalip.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalip.app.ui.onboarding.OnboardingScreen
import com.personalip.app.ui.root.RootUiState
import com.personalip.app.ui.root.RootViewModel

/**
 * 全局「根目录授权状态」抽象（第 2 阶段接入真实实现）。
 * 仅供需要感知授权状态的非 UI 层使用；UI 层主要通过 [RootStateHost] 的页面切换感知。
 */
interface RootState {
    val isAuthorized: Boolean
    val displayName: String?
}

val LocalRootState = staticCompositionLocalOf<RootState> {
    error("RootState not provided")
}

/**
 * 根目录授权网关：
 * - Loading：展示 loading；
 * - Unauthorized：展示 [OnboardingScreen] 引导用户选择并授权根文件夹；
 * - Authorized：展示主界面 [content]。
 *
 * 授权状态由 [RootViewModel] 从 [com.personalip.app.data.storage.RootFolderRepository]
 * 的 isAuthorizedFlow 驱动；用户在引导页完成授权后状态会自动翻转。
 */
@Composable
fun RootStateHost(content: @Composable () -> Unit) {
    val rootViewModel: RootViewModel = hiltViewModel()
    val state by rootViewModel.uiState.collectAsStateWithLifecycle()

    when (state) {
        RootUiState.Loading -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        RootUiState.Unauthorized -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                OnboardingScreen()
            }
        }
        RootUiState.Authorized -> {
            val authorized = object : RootState {
                override val isAuthorized = true
                override val displayName: String? = "个人IP打造"
            }
            CompositionLocalProvider(LocalRootState provides authorized) {
                content()
            }
        }
    }
}
