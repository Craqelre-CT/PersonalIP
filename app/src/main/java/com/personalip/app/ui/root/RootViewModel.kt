package com.personalip.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.local.DatabaseSeeder
import com.personalip.app.data.storage.AuthorizationState
import com.personalip.app.data.storage.RootFolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 提供根目录授权状态给 [com.personalip.app.ui.RootStateHost]，
 * 决定显示「授权引导页」还是「主界面」。
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    rootFolderRepository: RootFolderRepository,
    private val databaseSeeder: DatabaseSeeder
) : ViewModel() {

    init {
        // 首次启动即写入默认分类与空人设，幂等。
        viewModelScope.launch { runCatching { databaseSeeder.seedIfNeeded() } }
    }

    val uiState: StateFlow<RootUiState> = rootFolderRepository.isAuthorizedFlow
        .map { state ->
            when (state) {
                is AuthorizationState.Authorized -> RootUiState.Authorized
                AuthorizationState.Unauthorized -> RootUiState.Unauthorized
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootUiState.Loading
        )
}

sealed interface RootUiState {
    data object Loading : RootUiState
    data object Unauthorized : RootUiState
    data object Authorized : RootUiState
}
