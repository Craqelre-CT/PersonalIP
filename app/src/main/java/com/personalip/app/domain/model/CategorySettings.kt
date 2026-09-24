package com.personalip.app.domain.model

/**
 * 文件夹（分类）级设置：影响该文件夹下素材的导入与 AI 生成默认行为。
 *
 * 与 web 端 [web-debug/index.html] 中 `category.settings` 字段一一对应。
 *
 * @param autoOcr 导入图片时是否自动调用视觉模型识别（OCR + 场景描述）。
 * @param autoTags 导入图片时是否自动生成标签。
 * @param postTone 该文件夹默认文案语气（亲和/真实/幽默/专业/激励）。
 * @param postGoal 该文件夹默认转化目标（点赞/评论/咨询/转发）。
 * @param cooldownDays 该文件夹同素材冷却期（天）。若为 null，则使用全局冷却期。
 */
data class CategorySettings(
    val autoOcr: Boolean = true,
    val autoTags: Boolean = true,
    val postTone: String = "亲和",
    val postGoal: String = "点赞",
    val cooldownDays: Int? = 7
) {
    companion object {
        val DEFAULT = CategorySettings()
    }
}
