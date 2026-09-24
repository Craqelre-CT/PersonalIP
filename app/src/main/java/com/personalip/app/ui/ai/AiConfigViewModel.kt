package com.personalip.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.ai.AiProvider
import com.personalip.app.data.settings.AiConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val aiConfig: AiConfigRepository
) : ViewModel() {

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()
    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()
    private val _visionModel = MutableStateFlow("")
    val visionModel: StateFlow<String> = _visionModel.asStateFlow()
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** 当前选中的提供商（用于 UI 高亮 + 自动填充 baseUrl/模型）。 */
    private val _selectedProvider = MutableStateFlow(AiProvider.DEEPSEEK)
    val selectedProvider: StateFlow<AiProvider> = _selectedProvider.asStateFlow()

    init {
        viewModelScope.launch {
            val url = aiConfig.baseUrlFlow.first().orEmpty()
            val model = aiConfig.modelFlow.first().orEmpty()
            val visionModel = aiConfig.visionModelFlow.first().orEmpty()
            _baseUrl.value = url
            _model.value = model
            _visionModel.value = visionModel
            _apiKey.value = aiConfig.apiKey().orEmpty()
            // 根据 baseUrl 回显当前提供商。
            _selectedProvider.value = AiProvider.matchByUrl(url) ?: AiProvider.CUSTOM
        }
    }

    fun setBaseUrl(v: String) { _baseUrl.value = v }
    fun setModel(v: String) { _model.value = v }
    fun setVisionModel(v: String) { _visionModel.value = v }
    fun setApiKey(v: String) { _apiKey.value = v }

    /**
     * 选择提供商：自动填充 baseUrl、文案模型、图片识别模型，保留已输入的 API Key。
     * - 若已选文案模型不在该提供商列表，则取第一个作为默认。
     * - 图片识别模型同理；若提供商无视觉模型，则清空字段（提示用户该提供商不支持图片识别）。
     */
    fun selectProvider(provider: AiProvider) {
        _selectedProvider.value = provider
        if (provider != AiProvider.CUSTOM) {
            _baseUrl.value = provider.baseUrl
            if (provider.textModels.isNotEmpty() &&
                provider.textModels.none { it == _model.value }
            ) {
                _model.value = provider.textModels.first()
            }
            if (provider.visionModels.isNotEmpty() &&
                provider.visionModels.none { it == _visionModel.value }
            ) {
                _visionModel.value = provider.visionModels.first()
            }
            if (provider.visionModels.isEmpty()) {
                _visionModel.value = ""
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            aiConfig.setBaseUrl(_baseUrl.value.trim())
            aiConfig.setModel(_model.value.trim())
            aiConfig.setVisionModel(_visionModel.value.trim())
            aiConfig.setApiKey(_apiKey.value.trim())
            _saved.value = true
        }
    }

    fun consumeSaved() { _saved.value = false }
}
