package com.personalip.app.data.ai

/**
 * AI 提供商预设：baseUrl + 文本模型 + 视觉模型。
 *
 * 流程对应 moments-writer：
 *   1) 调 [visionModels] 识别图片内容 → 得到文字描述
 *   2) 调 [textModels] 结合人设生成朋友圈文案
 *
 * 用户只需选择提供商 + 填 API Key，无需手输 baseUrl / 模型名。
 */
enum class AiProvider(
    val displayName: String,
    val baseUrl: String,
    val textModels: List<String>,
    val visionModels: List<String>,
    val keyPlaceholder: String,
    val signupUrl: String
) {
    DEEPSEEK(
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        // DeepSeek 当前未开放公开视觉模型，文案生成使用 chat / reasoner。
        textModels = listOf("deepseek-chat", "deepseek-reasoner"),
        visionModels = emptyList(),
        keyPlaceholder = "sk-...",
        signupUrl = "https://platform.deepseek.com/api_keys"
    ),
    QWEN(
        displayName = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        textModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        visionModels = listOf("qwen-vl-plus", "qwen-vl-max"),
        keyPlaceholder = "sk-...",
        signupUrl = "https://dashscope.console.aliyun.com/apiKey"
    ),
    KIMI(
        displayName = "Kimi (月之暗面)",
        baseUrl = "https://api.moonshot.cn/v1",
        textModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        // Kimi 视觉模型：moonshot-v1-8k-vision-preview / 32k-vision-preview。
        visionModels = listOf("moonshot-v1-8k-vision-preview", "moonshot-v1-32k-vision-preview"),
        keyPlaceholder = "sk-...",
        signupUrl = "https://platform.moonshot.cn/console/api-keys"
    ),
    ZHIPU(
        displayName = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        textModels = listOf("glm-4-flash", "glm-4", "glm-4-plus"),
        visionModels = listOf("glm-4v-flash", "glm-4v", "glm-4v-plus"),
        keyPlaceholder = "...",
        signupUrl = "https://open.bigmodel.cn/usercenter/apikeys"
    ),
    OPENAI(
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        // gpt-4o 系列同时支持文本与视觉输入。
        textModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo"),
        visionModels = listOf("gpt-4o-mini", "gpt-4o"),
        keyPlaceholder = "sk-...",
        signupUrl = "https://platform.openai.com/api-keys"
    ),
    CUSTOM(
        displayName = "自定义（兼容 OpenAI 接口）",
        baseUrl = "",
        textModels = emptyList(),
        visionModels = emptyList(),
        keyPlaceholder = "sk-...",
        signupUrl = ""
    );

    /** 文本模型旧字段名（兼容旧调用方）。 */
    val models: List<String> get() = textModels

    companion object {
        /** 根据 baseUrl 匹配预设（用于回显当前已选提供商）。 */
        fun matchByUrl(url: String?): AiProvider? =
            entries.firstOrNull { it != CUSTOM && it.baseUrl == url?.trimEnd('/') }
    }
}
