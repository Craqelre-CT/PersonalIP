package com.personalip.app.data.storage

/**
 * 存储结构与文件夹常量。
 *
 * - 根文件夹英文名固定为 "PersonalIP"，App 界面显示中文名 "个人IP打造"。
 * - 所有子文件夹由 App 自动创建，使用中文名（SAF/DocumentFile 支持 Unicode 名称）。
 * - 数据库中区分 displayName（中文显示名）与 folderName（实际文件夹名），
 *   默认分类二者相同；用户新建分类时也保持同名，未来若需要可解耦。
 */
object FolderConstants {
    /** 根文件夹英文名（实际目录名）。 */
    const val ROOT_FOLDER_NAME = "PersonalIP"
    /** 根文件夹在 App 内的中文名（仅界面显示）。 */
    const val ROOT_DISPLAY_NAME = "个人IP打造"

    /** 根下的素材库目录。 */
    const val DIR_MATERIALS = "素材库"
    /** 根下的输出目录（AI 生成文案按 周几/ 落盘）。 */
    const val DIR_OUTPUT = "输出"
    /** 根下的人设目录（persona.json）。 */
    const val DIR_PERSONA = "人设"
    /** 根下的配置目录。 */
    const val DIR_CONFIG = "配置"
    /** 根下的备份目录。 */
    const val DIR_BACKUP = "备份"

    /** 根目录下需要自动创建的顶级子目录。 */
    val TOP_LEVEL_DIRS = listOf(
        DIR_MATERIALS,
        DIR_OUTPUT,
        DIR_PERSONA,
        DIR_CONFIG,
        DIR_BACKUP
    )

    /**
     * 默认素材分类（displayName, folderName）。
     * 这些分类会在「素材库/」下创建对应子文件夹。
     */
    val DEFAULT_CATEGORIES: List<Pair<String, String>> = listOf(
        "男生大体重" to "男生大体重",
        "男生小体重" to "男生小体重",
        "女生大体重" to "女生大体重",
        "女生小体重" to "女生小体重",
        "10斤内塑形" to "10斤内塑形",
        "改善亚健康" to "改善亚健康",
        "抗衰" to "抗衰"
    )

    /** 文件名中禁止出现的字符，需在生成文件名时过滤。 */
    val FORBIDDEN_FILE_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
}
