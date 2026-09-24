package com.personalip.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.storage.RootFolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首次授权引导页的 ViewModel。
 * 处理用户选择文件夹后的「持久化权限 + 创建目录结构」流程。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val rootFolderRepository: RootFolderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /**
     * 接收 SAF 选择器返回的 tree Uri 并完成授权与建目录。
     * 成功后 [RootViewModel] 的授权状态会自动翻转为 Authorized，从而切回主界面。
     */
    fun handlePickerResult(treeUri: Uri?) {
        // 用户取消选择：静默处理，不弹错误。
        if (treeUri == null) {
            _state.value = OnboardingUiState()
            return
        }
        _state.value = OnboardingUiState(isCreating = true, error = null)
        viewModelScope.launch {
            val ok = runCatching {
                rootFolderRepository.authorizeAndCreateStructure(treeUri)
            }.getOrDefault(false)
            // 成功则 RootViewModel 会自动切换；失败时显示提示。
            _state.value = OnboardingUiState(
                isCreating = false,
                error = if (!ok) "授权或创建目录失败，请重新选择一个可读写的文件夹" else null
            )
        }
    }

    /** 清除错误提示。 */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

data class OnboardingUiState(
    val isCreating: Boolean = false,
    val error: String? = null
)
